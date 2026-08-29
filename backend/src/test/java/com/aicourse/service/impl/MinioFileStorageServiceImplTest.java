package com.aicourse.service.impl;

import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MinioFileStorageServiceImpl 单元测试：mock MinioClient，覆盖上传/下载/删除的
 * 成功与异常分支（异常分支对应源码中 catch 后抛 RuntimeException 或返回 false）。
 */
@ExtendWith(MockitoExtension.class)
class MinioFileStorageServiceImplTest {

    @Mock
    private MinioClient minioClient;

    private MinioFileStorageServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new MinioFileStorageServiceImpl(minioClient);
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
        lenient().when(minioClient.getObject(any())).thenReturn(mock(GetObjectResponse.class));
    }

    @Test
    void upload_success() {
        String name = service.upload("o1", new ByteArrayInputStream("x".getBytes()), "text/plain", 1);
        assertEquals("o1", name);
    }

    @Test
    void upload_failureWrapsRuntimeException() throws Exception {
        doThrow(new RuntimeException("minio down")).when(minioClient).putObject(any());
        assertThrows(RuntimeException.class,
                () -> service.upload("o1", new ByteArrayInputStream("x".getBytes()), "text/plain", 1));
    }

    @Test
    void download_success() throws Exception {
        try (InputStream is = service.download("o1")) {
            assertNotNull(is);
        }
    }

    @Test
    void download_failureWrapsRuntimeException() throws Exception {
        doThrow(new RuntimeException("get fail")).when(minioClient).getObject(any());
        assertThrows(RuntimeException.class, () -> service.download("o1"));
    }

    @Test
    void delete_success() {
        assertTrue(service.delete("o1"));
    }

    @Test
    void delete_failureReturnsFalse() throws Exception {
        doThrow(new RuntimeException("remove fail")).when(minioClient).removeObject(any());
        assertFalse(service.delete("o1"));
    }
}
