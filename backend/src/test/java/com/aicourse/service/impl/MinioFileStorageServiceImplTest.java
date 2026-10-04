package com.aicourse.service.impl;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MinioFileStorageServiceImpl 单元测试：mock MinioClient，覆盖上传/下载/删除的
 * 成功与异常分支（异常分支对应源码中 catch 后抛 RuntimeException 或返回 false）。
 * <p>打桩原则：MinIO SDK 的 Args 对象（bucket/object/contentType/objectSize/versionId）
 * 就是被测逻辑的映射产物——桶名取错会把文件写进别的存储桶，对象键取错会直接覆盖他人文件，
 * 因此成功路径一律捕获 Args 断言其字段；异常注入桩保留 any()（那里不关心参数内容）。</p>
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
        // 兜底桩（any() 合理）：让不关心 getObject 参数的用例能跑完链路
        lenient().when(minioClient.getObject(any())).thenReturn(mock(GetObjectResponse.class));
    }

    @Test
    void upload_success() throws Exception {
        String name = service.upload("o1", new ByteArrayInputStream("x".getBytes()), "text/plain", 1);
        assertEquals("o1", name);

        ArgumentCaptor<PutObjectArgs> cap = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(cap.capture());
        PutObjectArgs args = cap.getValue();
        // 桶名来自 @Value 配置、对象键来自入参：二者任一错位都会把文件写到错误位置并覆盖同名对象
        assertEquals("test-bucket", args.bucket(), "上传必须落在配置的桶");
        assertEquals("o1", args.object(), "对象键必须按调用方给定的名字写入");
        // 声明式大小与 Content-Type 一并下发：丢了 size 会退化成流式多分片，丢了 contentType 下载端只能拿到 octet-stream
        assertEquals(1L, args.objectSize(), "应把已知文件大小交给 SDK 以单分片直传");
        assertEquals("text/plain", args.contentType(), "Content-Type 必须透传");
    }

    @Test
    void upload_failureWrapsRuntimeException() throws Exception {
        doThrow(new RuntimeException("minio down")).when(minioClient).putObject(any());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.upload("o1", new ByteArrayInputStream("x".getBytes()), "text/plain", 1));
        // 底层原因必须挂在 cause 上，否则运维只能看到「上传失败」而无从定位是桶策略还是网络
        assertEquals("MinIO 文件上传失败", ex.getMessage());
        assertNotNull(ex.getCause(), "应保留底层异常作为 cause");
        assertEquals("minio down", ex.getCause().getMessage());
    }

    @Test
    void download_success() throws Exception {
        GetObjectResponse body = mock(GetObjectResponse.class);
        when(minioClient.getObject(any())).thenReturn(body);
        try (InputStream is = service.download("o1")) {
            // 真实断言：下载必须原样透传 MinIO 返回的对象流（同一实例），而非空/包装后的新流
            assertNotNull(is);
            assertSame(body, is, "download 应直接返回 getObject 的对象流");
        }
        // 交互 + 关键参数捕获：必须以配置的 bucket 与请求的 objectName 去取对象
        ArgumentCaptor<GetObjectArgs> cap = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minioClient).getObject(cap.capture());
        assertEquals("test-bucket", cap.getValue().bucket());
        assertEquals("o1", cap.getValue().object());
    }

    @Test
    void download_failureWrapsRuntimeException() throws Exception {
        doThrow(new RuntimeException("get fail")).when(minioClient).getObject(any());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.download("o1"));
        assertEquals("MinIO 文件下载失败", ex.getMessage());
        assertNotNull(ex.getCause(), "应保留底层异常作为 cause");
    }

    @Test
    void delete_success() throws Exception {
        assertTrue(service.delete("o1"));

        ArgumentCaptor<RemoveObjectArgs> cap = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(cap.capture());
        // 删除是破坏性操作：桶/对象键必须精确，写错等于删掉别人的文件
        assertEquals("test-bucket", cap.getValue().bucket(), "删除必须限定在配置的桶");
        assertEquals("o1", cap.getValue().object(), "删除必须只命中请求的对象键");
    }

    @Test
    void delete_failureReturnsFalse() throws Exception {
        doThrow(new RuntimeException("remove fail")).when(minioClient).removeObject(any());
        assertFalse(service.delete("o1"));
    }

    @Test
    void bucketIsTakenFromConfiguration() throws Exception {
        // 桶名由 @Value 注入，未配置时应回落到默认桶（断言的是「配置值真的被读进 Args」而非硬编码）
        ReflectionTestUtils.setField(service, "bucket", "another-bucket");
        service.upload("dir/file.pdf", new ByteArrayInputStream("y".getBytes()), "application/pdf", 1);
        ArgumentCaptor<PutObjectArgs> cap = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(cap.capture());
        assertEquals("another-bucket", cap.getValue().bucket());
        assertEquals("dir/file.pdf", cap.getValue().object());
        assertEquals("application/pdf", cap.getValue().contentType());
    }
}
