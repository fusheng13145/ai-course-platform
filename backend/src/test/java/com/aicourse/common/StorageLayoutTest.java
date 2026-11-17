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
 * StorageLayout 单元测试：钉住「默认仓库外根、URL/key 解析、穿越拒绝」三条口径，
 * 以及「仓库内 uploads/ 目录已移除、不再参与读写」这一约束。
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
    @DisplayName("历史 uploads/ 无前导斜杠形态仍映射到外部根，仓库目录不参与")
    void relativeUploadsFormMapsToExternalRoot() throws IOException {
        Path written = StorageLayout.writeBytes("legacy-form.txt", "new".getBytes());
        Path resolved = StorageLayout.resolveUrl("uploads/legacy-form.txt");
        assertEquals(written.toAbsolutePath().normalize(), resolved);
        assertTrue(resolved.startsWith(tempDir), "解析结果必须落在外部根：" + resolved);
        try (var in = StorageLayout.openRead("uploads/legacy-form.txt")) {
            assertNotNull(in, "历史形态应按 URL 回读外部根");
            assertEquals("new", new String(in.readAllBytes()));
        }
    }

    @Test
    @DisplayName("不存在的对象解析为外部根路径，不回退仓库 uploads/")
    void missingObjectStaysInExternalRoot() {
        Path missing = StorageLayout.resolveUrl("/uploads/__never_exists__.pdf");
        assertNotNull(missing);
        assertTrue(missing.startsWith(tempDir.resolve("storage")),
                "不得回退到仓库目录：" + missing);
        assertFalse(Files.exists(missing));
    }
}
