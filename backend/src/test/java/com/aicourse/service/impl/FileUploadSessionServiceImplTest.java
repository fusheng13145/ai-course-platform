package com.aicourse.service.impl;

import com.aicourse.entity.FileUploadSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.FileUploadSessionMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileUploadSessionServiceImplTest {

    @Mock
    private FileUploadSessionMapper fileUploadSessionMapper;

    private FileUploadSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), FileUploadSession.class);
        service = new FileUploadSessionServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", fileUploadSessionMapper);
    }

    private FileUploadSession session(Long id, Long uploaderId, String status, int uploaded, int total) {
        FileUploadSession s = new FileUploadSession();
        s.setId(id);
        s.setUploaderId(uploaderId);
        s.setStatus(status);
        s.setUploadedChunks(uploaded);
        s.setTotalChunks(total);
        s.setFilename("a.txt");
        return s;
    }

    // ===== createUploadSession =====
    @Test
    @DisplayName("createUploadSession 正常")
    void create_ok() {
        when(fileUploadSessionMapper.insert(any(FileUploadSession.class))).thenReturn(1);
        FileUploadSession s = service.createUploadSession("doc.txt", 1024L, 5L, 99L);
        assertNotNull(s);
        assertEquals("doc.txt", s.getFilename());
        assertEquals("UPLOADING", s.getStatus());
        assertEquals(99L, s.getUploaderId());
        verify(fileUploadSessionMapper).insert(any(FileUploadSession.class));
    }

    @Test
    @DisplayName("createUploadSession 文件名空白")
    void create_blankName() {
        assertThrows(BusinessException.class, () -> service.createUploadSession("   ", 1024L, 5L, 99L));
    }

    @Test
    @DisplayName("createUploadSession 无扩展名")
    void create_noExt() {
        assertThrows(BusinessException.class, () -> service.createUploadSession("readme", 1024L, 5L, 99L));
    }

    @Test
    @DisplayName("createUploadSession 非法扩展名")
    void create_badExt() {
        assertThrows(BusinessException.class, () -> service.createUploadSession("evil.exe", 1024L, 5L, 99L));
    }

    @Test
    @DisplayName("createUploadSession 文件过大")
    void create_tooLarge() {
        assertThrows(BusinessException.class,
                () -> service.createUploadSession("big.txt", 3L * 1024 * 1024 * 1024, 5L, 99L));
    }

    @Test
    @DisplayName("createUploadSession 分片数超限")
    void create_tooManyChunks() {
        assertThrows(BusinessException.class, () -> service.createUploadSession("big.txt", 1024L, 5000L, 99L));
    }

    // ===== uploadChunk =====
    @Test
    @DisplayName("uploadChunk 正常递增")
    void chunk_ok() {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 0, 5);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        when(fileUploadSessionMapper.updateById(any(FileUploadSession.class))).thenReturn(1);
        FileUploadSession r = service.uploadChunk(1L, 0, 99L);
        assertEquals(1, r.getUploadedChunks());
        verify(fileUploadSessionMapper).updateById(any(FileUploadSession.class));
    }

    @Test
    @DisplayName("uploadChunk 会话不存在")
    void chunk_notFound() {
        when(fileUploadSessionMapper.selectById(2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.uploadChunk(2L, 0, 99L));
    }

    @Test
    @DisplayName("uploadChunk 非属主")
    void chunk_notOwner() {
        FileUploadSession s = session(1L, 88L, "UPLOADING", 0, 5);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.uploadChunk(1L, 0, 99L));
    }

    @Test
    @DisplayName("uploadChunk 状态不允许")
    void chunk_badStatus() {
        FileUploadSession s = session(1L, 99L, "COMPLETED", 5, 5);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.uploadChunk(1L, 0, 99L));
    }

    // ===== mergeChunks =====
    @Test
    @DisplayName("mergeChunks 正常完成")
    void merge_ok() {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 5, 5);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        when(fileUploadSessionMapper.updateById(any(FileUploadSession.class))).thenReturn(1);
        FileUploadSession r = service.mergeChunks(1L, 99L);
        assertEquals("COMPLETED", r.getStatus());
    }

    @Test
    @DisplayName("mergeChunks 分片不齐")
    void merge_incomplete() {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 3, 5);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.mergeChunks(1L, 99L));
    }

    @Test
    @DisplayName("mergeChunks 非属主")
    void merge_notOwner() {
        FileUploadSession s = session(1L, 88L, "UPLOADING", 5, 5);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.mergeChunks(1L, 99L));
    }

    // ===== getUploadProgress =====
    @Test
    @DisplayName("getUploadProgress 正常计算百分比")
    void progress_ok() {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 1, 4);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        com.aicourse.vo.UploadProgressVO p = service.getUploadProgress(1L, 99L);
        assertEquals(1, p.getUploaded());
        assertEquals(4, p.getTotal());
        assertEquals("25.0%", p.getPercentage());
        assertEquals("UPLOADING", p.getStatus());
    }

    @Test
    @DisplayName("getUploadProgress total 为 0")
    void progress_zeroTotal() {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 0, 0);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        com.aicourse.vo.UploadProgressVO p = service.getUploadProgress(1L, 99L);
        assertEquals("0.0%", p.getPercentage());
    }

    @Test
    @DisplayName("getUploadProgress 非属主")
    void progress_notOwner() {
        FileUploadSession s = session(1L, 88L, "UPLOADING", 0, 4);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.getUploadProgress(1L, 99L));
    }

    // ===== cancelUpload =====
    @Test
    @DisplayName("cancelUpload 正常")
    void cancel_ok() {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 2, 5);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        when(fileUploadSessionMapper.updateById(any(FileUploadSession.class))).thenReturn(1);
        service.cancelUpload(1L, 99L);
        assertEquals("CANCELLED", s.getStatus());
    }

    @Test
    @DisplayName("cancelUpload 已完成不可取消")
    void cancel_completed() {
        FileUploadSession s = session(1L, 99L, "COMPLETED", 5, 5);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.cancelUpload(1L, 99L));
    }

    @Test
    @DisplayName("cancelUpload 非属主")
    void cancel_notOwner() {
        FileUploadSession s = session(1L, 88L, "UPLOADING", 2, 5);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.cancelUpload(1L, 99L));
    }

    // ===== cleanExpiredSessions =====
    @Test
    @DisplayName("cleanExpiredSessions 无过期")
    void clean_empty() {
        when(fileUploadSessionMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertEquals(0, service.cleanExpiredSessions());
    }

    @Test
    @DisplayName("cleanExpiredSessions 清理两条")
    void clean_some() {
        FileUploadSession a = session(1L, 99L, "UPLOADING", 0, 5);
        FileUploadSession b = session(2L, 99L, "UPLOADING", 0, 5);
        when(fileUploadSessionMapper.selectList(any())).thenReturn(List.of(a, b));
        when(fileUploadSessionMapper.deleteById(any(Serializable.class))).thenReturn(1);
        assertEquals(2, service.cleanExpiredSessions());
        verify(fileUploadSessionMapper, times(2)).deleteById(any(Serializable.class));
    }
}
