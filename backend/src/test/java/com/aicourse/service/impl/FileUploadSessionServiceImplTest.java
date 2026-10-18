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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileUploadSessionServiceImplTest {

    @Mock
    private FileUploadSessionMapper fileUploadSessionMapper;

    @TempDir
    Path tempRoot;

    private FileUploadSessionServiceImpl service;

    private Path chunkRoot;
    private Path uploadRoot;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), FileUploadSession.class);
        service = new FileUploadSessionServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", fileUploadSessionMapper);
        uploadRoot = tempRoot.resolve("uploads");
        chunkRoot = tempRoot.resolve("upload-chunks");
        ReflectionTestUtils.setField(service, "uploadDir", uploadRoot.toString());
        ReflectionTestUtils.setField(service, "chunkDir", chunkRoot.toString());
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

    @Test
    @DisplayName("cancelUpload 回收已落盘的分片目录")
    void cancel_deletesChunkDir() throws Exception {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 1, 3);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        when(fileUploadSessionMapper.updateById(any(FileUploadSession.class))).thenReturn(1);
        seedChunk(1L, 0, "AA");

        service.cancelUpload(1L, 99L);

        assertFalse(Files.exists(chunkRoot.resolve("1")), "取消后分片目录必须回收");
    }

    @Test
    @DisplayName("cleanExpiredSessions 删行的同时回收各自分片目录")
    void clean_deletesChunkDirs() throws Exception {
        FileUploadSession a = session(1L, 99L, "UPLOADING", 0, 5);
        FileUploadSession b = session(2L, 99L, "UPLOADING", 0, 5);
        when(fileUploadSessionMapper.selectList(any())).thenReturn(List.of(a, b));
        when(fileUploadSessionMapper.deleteById(any(Serializable.class))).thenReturn(1);
        seedChunk(1L, 0, "AA");
        seedChunk(2L, 0, "BB");

        assertEquals(2, service.cleanExpiredSessions());

        assertFalse(Files.exists(chunkRoot.resolve("1")), "过期会话 1 的分片必须回收");
        assertFalse(Files.exists(chunkRoot.resolve("2")), "过期会话 2 的分片必须回收");
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
    @DisplayName("uploadChunk 把分片字节落盘到会话专属目录，按 0 基索引命名")
    void chunk_writesBytesToDisk() throws Exception {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 0, 3);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        when(fileUploadSessionMapper.updateById(any(FileUploadSession.class))).thenReturn(1);

        byte[] bytes = "chunk-zero-payload".getBytes(StandardCharsets.UTF_8);
        service.uploadChunk(1L, 0, new ByteArrayInputStream(bytes), bytes.length, null, 99L);

        Path chunkFile = chunkRoot.resolve("1").resolve("0.part");
        assertTrue(Files.exists(chunkFile), "分片应落盘到 " + chunkFile);
        assertArrayEquals(bytes, Files.readAllBytes(chunkFile));
    }

    @Test
    @DisplayName("同一分片重复上传只计一片，进度不被重传虚增")
    void chunk_duplicateIndexCountedOnce() throws Exception {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 0, 3);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        when(fileUploadSessionMapper.updateById(any(FileUploadSession.class))).thenReturn(1);

        byte[] bytes = "same-chunk".getBytes(StandardCharsets.UTF_8);
        service.uploadChunk(1L, 0, new ByteArrayInputStream(bytes), bytes.length, null, 99L);
        FileUploadSession afterRetransmit =
                service.uploadChunk(1L, 0, new ByteArrayInputStream(bytes), bytes.length, null, 99L);

        assertEquals(1, afterRetransmit.getUploadedChunks());
    }

    @Test
    @DisplayName("uploadChunk 索引越界（≥totalChunks 或负数）拒绝且不落盘")
    void chunk_indexOutOfRange_rejected() {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 0, 3);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);

        for (int badIndex : new int[]{3, -1}) {
            byte[] bytes = "x".getBytes(StandardCharsets.UTF_8);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.uploadChunk(
                    1L, badIndex, new ByteArrayInputStream(bytes), bytes.length, null, 99L));
            assertTrue(ex.getMessage().contains("分片索引"), "越界应给出索引类提示，实际=" + ex.getMessage());
        }
        assertFalse(Files.exists(chunkRoot.resolve("1").resolve("3.part")), "越界分片不得落盘");
        assertFalse(Files.exists(chunkRoot.resolve("1").resolve("-1.part")), "负索引分片不得落盘");
    }

    @Test
    @DisplayName("uploadChunk 空分片拒绝")
    void chunk_emptyRejected() {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 0, 3);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);

        assertThrows(BusinessException.class, () -> service.uploadChunk(
                1L, 0, new ByteArrayInputStream(new byte[0]), 0L, null, 99L));
        assertFalse(Files.exists(chunkRoot.resolve("1").resolve("0.part")), "空分片不得落盘");
    }

    @Test
    @DisplayName("uploadChunk 客户端声明摘要与实算 MD5 不符时丢弃该分片")
    void chunk_md5Mismatch_discardsChunk() {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 0, 3);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);

        byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.uploadChunk(
                1L, 0, new ByteArrayInputStream(bytes), bytes.length, "deadbeef", 99L));
        assertTrue(ex.getMessage().contains("摘要"), "应提示摘要不符，实际=" + ex.getMessage());
        assertFalse(Files.exists(chunkRoot.resolve("1").resolve("0.part")), "摘要不符的分片不得留盘");
        assertEquals(0, s.getUploadedChunks(), "摘要不符不得推进进度");
    }

    @Test
    @DisplayName("uploadChunk 声明摘要与实算 MD5 一致时接受分片")
    void chunk_md5Match_accepted() {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 0, 3);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        when(fileUploadSessionMapper.updateById(any(FileUploadSession.class))).thenReturn(1);

        byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
        FileUploadSession r = service.uploadChunk(1L, 0, new ByteArrayInputStream(bytes),
                bytes.length, "321c3cf486ed509164edec1e1981fec8", 99L);

        assertEquals(1, r.getUploadedChunks());
        assertTrue(Files.exists(chunkRoot.resolve("1").resolve("0.part")));
    }

    @Test
    @DisplayName("分片累计字节超过会话声明的 fileSize 时拒绝，且不留残片")
    void chunk_quotaExceeded_rejected() throws Exception {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 0, 2);
        s.setFileSize(10L);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        when(fileUploadSessionMapper.updateById(any(FileUploadSession.class))).thenReturn(1);

        service.uploadChunk(1L, 0, new ByteArrayInputStream(new byte[6]), 6L, null, 99L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.uploadChunk(
                1L, 1, new ByteArrayInputStream(new byte[6]), 6L, null, 99L));
        assertTrue(ex.getMessage().contains("超过"), "应提示超出声明大小，实际=" + ex.getMessage());
        assertFalse(Files.exists(chunkRoot.resolve("1").resolve("1.part")), "超配额分片不得留盘");
        assertEquals(1, s.getUploadedChunks(), "被拒分片不得推进进度");
    }

    @Test
    @DisplayName("createUploadSession 的 uploadPath 存相对上传根的对象名，不含 uploads/ 前缀与穿越片段")
    void create_uploadPathIsObjectRelative() {
        when(fileUploadSessionMapper.insert(any(FileUploadSession.class))).thenReturn(1);

        FileUploadSession s = service.createUploadSession("a.pdf", 1024L, 2L, 99L);

        String uploadPath = s.getUploadPath();
        assertFalse(uploadPath.startsWith("uploads/"), "对象名不应带静态目录前缀，实际=" + uploadPath);
        assertFalse(uploadPath.contains(".."), "对象名不得含穿越片段，实际=" + uploadPath);
        assertTrue(uploadRoot.resolve(uploadPath).normalize().startsWith(uploadRoot),
                "解析后必须落在上传根目录内，实际=" + uploadPath);
    }

    @Test
    @DisplayName("createUploadSession 收到路径穿越文件名时只保留纯文件名，解析后仍在上传根内")
    void create_pathTraversalFilename_neutralized() {
        when(fileUploadSessionMapper.insert(any(FileUploadSession.class))).thenReturn(1);

        FileUploadSession s = service.createUploadSession("../../etc/passwd.pdf", 1024L, 2L, 99L);

        assertEquals("passwd.pdf", s.getFilename());
        assertTrue(uploadRoot.resolve(s.getUploadPath()).normalize().startsWith(uploadRoot),
                "越界文件名不得让落点逃出上传根，实际=" + s.getUploadPath());
    }

    @Test
    @DisplayName("mergeChunks 拒绝把会话 uploadPath 指向上传根之外的产物")
    void merge_rejectsUploadPathOutsideRoot() throws Exception {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 1, 1);
        s.setUploadPath("../escaped.txt");
        s.setFileSize(2L);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        seedChunk(1L, 0, "AA");

        assertThrows(BusinessException.class, () -> service.mergeChunks(1L, 99L));

        assertFalse(Files.exists(tempRoot.resolve("escaped.txt")), "越界产物绝不允许落盘");
    }

    @Test
    @DisplayName("uploadChunk 会话不存在")
    void chunk_notFound() {
        when(fileUploadSessionMapper.selectById(2L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.uploadChunk(2L, 0, new ByteArrayInputStream(new byte[1]), 1L, null, 99L));
    }

    @Test
    @DisplayName("uploadChunk 非属主")
    void chunk_notOwner() {
        FileUploadSession s = session(1L, 88L, "UPLOADING", 0, 5);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class,
                () -> service.uploadChunk(1L, 0, new ByteArrayInputStream(new byte[1]), 1L, null, 99L));
    }

    @Test
    @DisplayName("uploadChunk 状态不允许")
    void chunk_badStatus() {
        FileUploadSession s = session(1L, 99L, "COMPLETED", 5, 5);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class,
                () -> service.uploadChunk(1L, 0, new ByteArrayInputStream(new byte[1]), 1L, null, 99L));
    }

    /** 测试夹具：直接按 0 基索引摆放已落盘的分片文件 */
    private void seedChunk(Long sessionId, int index, String content) throws Exception {
        Path dir = chunkRoot.resolve(String.valueOf(sessionId));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(index + ".part"), content, StandardCharsets.UTF_8);
    }

    // ===== mergeChunks =====
    @Test
    @DisplayName("mergeChunks 按索引序拼接分片落盘到最终对象路径，并回收分片目录")
    void merge_concatenatesChunksInIndexOrder() throws Exception {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 3, 3);
        s.setUploadPath("2026/10/abc_a.txt");
        s.setFileSize(6L);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        when(fileUploadSessionMapper.updateById(any(FileUploadSession.class))).thenReturn(1);
        // 乱序摆片，验证合并确实按索引序而非目录序
        seedChunk(1L, 2, "CC");
        seedChunk(1L, 0, "AA");
        seedChunk(1L, 1, "BB");

        FileUploadSession r = service.mergeChunks(1L, 99L);

        assertEquals("COMPLETED", r.getStatus());
        Path finalFile = uploadRoot.resolve("2026/10/abc_a.txt");
        assertTrue(Files.exists(finalFile), "合并产物应落盘到 " + finalFile);
        assertEquals("AABBCC", Files.readString(finalFile, StandardCharsets.UTF_8));
        assertFalse(Files.exists(chunkRoot.resolve("1")), "合并成功后应回收分片目录");
    }

    @Test
    @DisplayName("mergeChunks 缺片时回传缺失索引，不产生半成品文件")
    void merge_missingChunks_reportsIndicesAndLeavesNoArtifact() throws Exception {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 2, 3);
        s.setUploadPath("2026/10/abc_a.txt");
        s.setFileSize(6L);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        seedChunk(1L, 0, "AA");
        seedChunk(1L, 2, "CC");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.mergeChunks(1L, 99L));

        assertTrue(ex.getMessage().contains("缺失索引"), "应回传缺失索引，实际=" + ex.getMessage());
        assertTrue(ex.getMessage().contains("1"), "缺失清单应含索引 1，实际=" + ex.getMessage());
        assertFalse(Files.exists(uploadRoot.resolve("2026/10/abc_a.txt")), "缺片不得产出文件");
        assertEquals("UPLOADING", s.getStatus());
    }

    @Test
    @DisplayName("mergeChunks 合并字节数与声明 fileSize 不符时删除产物并置 FAILED")
    void merge_sizeMismatch_deletesArtifactAndMarksFailed() throws Exception {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 2, 2);
        s.setUploadPath("2026/10/abc_a.txt");
        s.setFileSize(99L);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        when(fileUploadSessionMapper.updateById(any(FileUploadSession.class))).thenReturn(1);
        seedChunk(1L, 0, "AA");
        seedChunk(1L, 1, "BB");

        assertThrows(BusinessException.class, () -> service.mergeChunks(1L, 99L));

        assertFalse(Files.exists(uploadRoot.resolve("2026/10/abc_a.txt")), "尺寸不符的产物必须删除");
        assertEquals("FAILED", s.getStatus());
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

    @Test
    @DisplayName("mergeChunks 会话不存在")
    void merge_notFound() {
        when(fileUploadSessionMapper.selectById(2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.mergeChunks(2L, 99L));
    }

    @Test
    @DisplayName("mergeChunks 状态不允许合并（须因状态被拒，而非恰好缺片）")
    void merge_badStatus() {
        FileUploadSession s = session(1L, 99L, "COMPLETED", 1, 1);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        BusinessException e = assertThrows(BusinessException.class, () -> service.mergeChunks(1L, 99L));
        assertTrue(e.getMessage().contains("状态"), "应因会话状态被拒，实际=" + e.getMessage());
    }

    @Test
    @DisplayName("mergeChunks 会话未声明有效总分片数（null / 0 都拒绝，不落到 NPE 或空合并）")
    void merge_invalidTotalChunks() {
        FileUploadSession nullTotal = session(1L, 99L, "UPLOADING", 0, 1);
        nullTotal.setTotalChunks(null);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(nullTotal);
        BusinessException e = assertThrows(BusinessException.class, () -> service.mergeChunks(1L, 99L));
        assertTrue(e.getMessage().contains("总分片数"), "实际=" + e.getMessage());

        FileUploadSession zeroTotal = session(1L, 99L, "UPLOADING", 0, 1);
        zeroTotal.setTotalChunks(0);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(zeroTotal);
        assertThrows(BusinessException.class, () -> service.mergeChunks(1L, 99L));
    }

    // ===== getUploadProgress =====
    @Test
    @DisplayName("getUploadProgress 按磁盘单片算百分比（原断言沿用会话行计数，批次 2 改磁盘真值后翻向）")
    void progress_ok() throws Exception {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 1, 4);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        seedChunk(1L, 0, "AA");
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

    @Test
    @DisplayName("getUploadProgress 给出磁盘缺失分片索引集合（0 基升序），供客户端只补传缺片")
    void progress_listsMissingIndexesFromDisk() throws Exception {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 4, 4);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        seedChunk(1L, 0, "AA");
        seedChunk(1L, 2, "CC");

        com.aicourse.vo.UploadProgressVO p = service.getUploadProgress(1L, 99L);

        assertEquals(List.of(1, 3), p.getMissingIndexes(),
                "缺失索引须为 0 基升序集合，实际=" + p.getMissingIndexes());
    }

    @Test
    @DisplayName("getUploadProgress 的 uploaded 与百分比取自磁盘，不信会话行的陈旧计数")
    void progress_uploadedAndPercentageComeFromDisk() throws Exception {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 4, 4);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        seedChunk(1L, 0, "AA");
        seedChunk(1L, 2, "CC");

        com.aicourse.vo.UploadProgressVO p = service.getUploadProgress(1L, 99L);

        assertEquals(2, p.getUploaded(), "磁盘只有两片，uploaded 不得沿用会话行的 4");
        assertEquals("50.0%", p.getPercentage());
    }

    @Test
    @DisplayName("getUploadProgress 遇到非索引命名的 .part 残留不炸，按缺失处理")
    void progress_unparseableChunkNameDoesNotThrow() throws Exception {
        FileUploadSession s = session(1L, 99L, "UPLOADING", 0, 2);
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(s);
        Path dir = chunkRoot.resolve("1");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("broken.part"), "XX", StandardCharsets.UTF_8);

        com.aicourse.vo.UploadProgressVO p = service.getUploadProgress(1L, 99L);

        assertEquals(List.of(0, 1), p.getMissingIndexes(),
                "不可解析的残留片不得被当成就绪，实际=" + p.getMissingIndexes());
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

    // ===== sweepOrphanChunkDirs =====
    @Test
    @DisplayName("sweepOrphanChunkDirs 回收会话行已不存在的分片目录（删行不等于磁盘清干净）")
    void sweep_deletesDirsOfUnknownSessions() throws Exception {
        seedChunk(777L, 0, "AA");
        when(fileUploadSessionMapper.selectById(777L)).thenReturn(null);

        assertEquals(1, service.sweepOrphanChunkDirs());
        assertFalse(Files.exists(chunkRoot.resolve("777")), "无主目录必须回收");
    }

    @Test
    @DisplayName("sweepOrphanChunkDirs 保留 UPLOADING/FAILED 会话目录（续传与补传的真值所在）")
    void sweep_keepsResumableSessions() throws Exception {
        seedChunk(1L, 0, "AA");
        seedChunk(2L, 0, "BB");
        when(fileUploadSessionMapper.selectById(1L)).thenReturn(session(1L, 99L, "UPLOADING", 1, 4));
        when(fileUploadSessionMapper.selectById(2L)).thenReturn(session(2L, 99L, "FAILED", 3, 4));

        assertEquals(0, service.sweepOrphanChunkDirs());
        assertTrue(Files.exists(chunkRoot.resolve("1")), "续传中会话的分片不得被回收");
        assertTrue(Files.exists(chunkRoot.resolve("2")), "合并失败会话的分片须留给补传");
    }

    @Test
    @DisplayName("sweepOrphanChunkDirs 回收终态会话的残留目录")
    void sweep_deletesTerminalLeftovers() throws Exception {
        seedChunk(3L, 0, "CC");
        when(fileUploadSessionMapper.selectById(3L)).thenReturn(session(3L, 99L, "COMPLETED", 4, 4));

        assertEquals(1, service.sweepOrphanChunkDirs());
        assertFalse(Files.exists(chunkRoot.resolve("3")));
    }

    @Test
    @DisplayName("sweepOrphanChunkDirs 不碰非会话 id 命名的子目录")
    void sweep_skipsNonNumericDir() throws Exception {
        Path manual = chunkRoot.resolve("manual-drop");
        Files.createDirectories(manual);
        Files.writeString(manual.resolve("keep.txt"), "K", StandardCharsets.UTF_8);

        assertEquals(0, service.sweepOrphanChunkDirs());
        assertTrue(Files.exists(manual), "非数字目录一律不删：命名口径外即不可推断为分片");
    }

    @Test
    @DisplayName("sweepOrphanChunkDirs 分片根目录不存在时返回 0 且不抛")
    void sweep_absentRootIsQuiet() {
        ReflectionTestUtils.setField(service, "chunkDir", tempRoot.resolve("never-created").toString());
        assertEquals(0, service.sweepOrphanChunkDirs());
    }
}
