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
        // 使用「读时抛 IOException」的 InputStream 强制触发 catch 块，
        // 跨平台可复现（原测试用 "bad*name.txt" 仅在 Windows 非法，
        // 在 Linux/Ubuntu 合法，导致 CI 上 FileOutputStream 创建成功、异常未抛出）。
        // 直接继承 InputStream 抽象类；同时覆盖 read()（抽象）与 read(byte[],int,int)，
        // 其中后者是实现 read(byte[]) 时的实际调用入口。
        InputStream brokenStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Simulated read failure for test");
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                throw new IOException("Simulated read failure for test");
            }
        };
        assertThrows(RuntimeException.class,
                () -> service.upload("test.txt", brokenStream, "text/plain", 1));
    }
}
