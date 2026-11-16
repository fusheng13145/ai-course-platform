package com.aicourse.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StorageLayout 单元测试：钉住「默认仓库外根、URL/key 解析、穿越拒绝、旧目录兼容」四条口径。
 */
class StorageLayoutTest {

    @TempDir
    Path tempDir;

    private String previousDataDir;

    @BeforeEach
    void setUp() {
        previousDataDir = System.getProperty("aicourse.data-dir");
        System.setProperty("aicourse.data-dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (previousDataDir == null) {
            System.clearProperty("aicourse.data-dir");
        } else {
            System.setProperty("aicourse.data-dir", previousDataDir);
        }
    }

    @Test
    @DisplayName("默认上传根与分片根都落在仓库外数据目录下")
    void defaultRootsAreOutsideRepository() {
        Path upload = StorageLayout.uploadRoot();
        Path chunk = StorageLayout.chunkRoot();
        assertTrue(upload.startsWith(tempDir), "上传根应在数据目录内：" + upload);
        assertTrue(chunk.startsWith(tempDir), "分片根应在数据目录内：" + chunk);
        assertFalse(upload.startsWith(StorageLayout.legacyUploadRoot()),
                "默认上传根不得落在仓库 uploads/");
    }

    @Test
    @DisplayName("URL/key 解析支持 /uploads/、uploads/ 与裸 key")
    void objectKeyNormalizesAllStoredForms() {
        assertEquals("resources/1/a.pdf", StorageLayout.objectKey("/uploads/resources/1/a.pdf"));
        assertEquals("resources/1/a.pdf", StorageLayout.objectKey("uploads/resources/1/a.pdf"));
        assertEquals("a.pdf", StorageLayout.objectKey("a.pdf"));
        assertNull(StorageLayout.objectKey(""));
        assertNull(StorageLayout.objectKey("/etc/passwd"));
    }

    @Test
    @DisplayName("穿越、盘符与反斜杠路径一律拒绝")
    void objectKeyRejectsTraversalAndWindowsPaths() {
        assertNull(StorageLayout.objectKey("/uploads/../secret.pdf"));
        assertNull(StorageLayout.objectKey("uploads/a/../../secret.pdf"));
        assertNull(StorageLayout.objectKey("C:/Windows/system.ini"));
        assertNull(StorageLayout.objectKey("/uploads/\\..\\secret.pdf"));
    }

    @Test
    @DisplayName("写入落到外部根，读取可回读，删除后文件消失")
    void writeReadDeleteRoundTrip() throws IOException {
        Path written = StorageLayout.writeBytes("resources/1/note.txt", "hello".getBytes());
        assertTrue(Files.isRegularFile(written));
        assertEquals(tempDir.resolve("storage"), written.getParent().getParent().getParent());
        try (var in = StorageLayout.openRead("/uploads/resources/1/note.txt")) {
            assertNotNull(in, "写完应能按 URL 回读");
            assertEquals("hello", new String(in.readAllBytes()));
        }
        assertTrue(StorageLayout.deleteByUrl("/uploads/resources/1/note.txt"));
        assertFalse(Files.exists(written));
    }

    @Test
    @DisplayName("旧仓库 uploads/ 作为只读兼容位置可回读与删除")
    void legacyUploadDirIsFallback() throws IOException {
        Path legacy = StorageLayout.legacyUploadRoot().resolve("legacy-note.txt");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "old");
        try {
            Path resolved = StorageLayout.resolveUrl("/uploads/legacy-note.txt");
            assertEquals(legacy.toAbsolutePath().normalize(), resolved);
            assertTrue(StorageLayout.deleteByUrl("/uploads/legacy-note.txt"));
            assertFalse(Files.exists(legacy));
        } finally {
            Files.deleteIfExists(legacy);
        }
    }
}
