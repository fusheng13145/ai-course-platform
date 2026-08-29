package com.aicourse.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LocalFileStorageServiceImpl 单元测试：使用真实临时目录做文件 IO。
 * 覆盖：upload 成功 / 下载成功 / 删除成功 / 下载不存在返回 null / 删除不存在返回 false /
 * upload IO 异常（非法文件名）抛出 RuntimeException。
 */
@ExtendWith(MockitoExtension.class)
class LocalFileStorageServiceImplTest {

    private LocalFileStorageServiceImpl service;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("local-storage-test");
        service = new LocalFileStorageServiceImpl();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
        }
    }

    @Test
    void upload_and_download_roundTrip() throws IOException {
        byte[] data = "hello local storage".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String name = service.upload("docs/note.txt", new ByteArrayInputStream(data), "text/plain", data.length);

        assertEquals("docs/note.txt", name);
        try (InputStream is = service.download("docs/note.txt")) {
            assertNotNull(is);
            assertArrayEquals(data, is.readAllBytes());
        }
    }

    @Test
    void download_notFound_returnsNull() {
        assertNull(service.download("missing.txt"));
    }

    @Test
    void delete_success() throws IOException {
        byte[] data = "x".getBytes();
        service.upload("a.txt", new ByteArrayInputStream(data), "text/plain", data.length);
        assertTrue(service.delete("a.txt"));
        assertFalse(Files.exists(tempDir.resolve("a.txt")));
    }

    @Test
    void delete_notFound_returnsFalse() {
        assertFalse(service.delete("nope.txt"));
    }

    @Test
    void upload_ioError_wrapsInRuntimeException() {
        // Windows 下文件名含 "*" 属非法，FileOutputStream 创建失败 -> 捕获后抛 RuntimeException
        assertThrows(RuntimeException.class,
                () -> service.upload("bad*name.txt", new ByteArrayInputStream("x".getBytes()), "text/plain", 1));
    }
}
