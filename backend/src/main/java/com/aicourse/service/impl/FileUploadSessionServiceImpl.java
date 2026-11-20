package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiCode;
import com.aicourse.common.StorageLayout;
import com.aicourse.common.UploadMagic;
import com.aicourse.entity.FileUploadSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.FileUploadSessionMapper;
import com.aicourse.service.FileStorageService;
import com.aicourse.service.FileUploadSessionService;
import com.aicourse.service.FileVirusScanService;
import com.aicourse.vo.UploadProgressVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 文件上传会话服务实现类
 * 实现分片上传会话的创建、分片记录、合并、进度查询、取消及过期清理等核心逻辑
 */
@Service
public class FileUploadSessionServiceImpl extends ServiceImpl<FileUploadSessionMapper, FileUploadSession> implements FileUploadSessionService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadSessionServiceImpl.class);

    public FileUploadSessionServiceImpl(FileStorageService fileStorageService,
                                        FileVirusScanService virusScanService) {
        this.fileStorageService = fileStorageService;
        this.virusScanService = virusScanService;
    }

    /** 上传会话默认过期时间：24小时 */
    private static final int SESSION_EXPIRE_HOURS = 24;

    /**
     * 单文件总大小上限（字节，默认 2GB）。
     * 修复：原仅依赖 servlet.multipart 单次 50MB 限制，分片可无限累积拼出超大文件占满磁盘。
     */
    private static final long MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024 * 1024;
    /** 总分片数上限（默认 4096），防 totalChunks 声明巨大值被无限推进 */
    private static final int MAX_TOTAL_CHUNKS = 4096;

    /** 单个分片上限（默认 50MB），与 servlet.multipart 单次上限及 ResourceController 口径一致。 */
    private static final long MAX_CHUNK_SIZE_BYTES = 50L * 1024 * 1024;

    /** 单用户同时处于 UPLOADING 的会话数上限，防止并发占用分片目录。 */
    @Value("${file.upload.max-active-sessions-per-user:10}")
    private int maxActiveSessionsPerUser = 10;

    /** 单用户所有 UPLOADING 会话声明字节数之和上限，默认 4GB。 */
    @Value("${file.upload.max-active-bytes-per-user:4294967296}")
    private long maxActiveBytesPerUser = 4L * 1024 * 1024 * 1024;

    /** 统一存储入口：分片合并最终产物必须经此落盘，禁止绕过去直接写 upload-dir。 */
    private final FileStorageService fileStorageService;

    /** 合并产物落盘前的病毒扫描入口；扫描不可用按 fail-closed 拒绝上传。 */
    private final FileVirusScanService virusScanService;

    /** 分片暂存文件后缀，文件名为 {0基索引}.part */
    private static final String CHUNK_SUFFIX = ".part";

    /** 最终文件落盘根目录：默认仓库外 ~/.aicourse-platform/storage，与静态映射同根 */
    @Value("${file.upload-dir:}")
    private String uploadDir;

    /** 分片暂存目录：默认仓库外 ~/.aicourse-platform/chunks，刻意不放在静态映射根下 */
    @Value("${file.chunk-dir:}")
    private String chunkDir;

    /** 当前生效的分片根：Spring 配置优先，空值由 StorageLayout 解析到仓库外默认目录。 */
    private Path chunkRoot() {
        return StorageLayout.chunkRoot(chunkDir);
    }

    /** 当前生效的最终文件根。 */
    private Path uploadRoot() {
        return StorageLayout.uploadRoot(uploadDir);
    }

    /** 允许上传的文件扩展名白名单（小写）。<b>刻意不含 svg</b>：本地静态模式会按扩展名回
     * {@code image/svg+xml} 并同源内联，任何登录用户可经分片上传构造存储型 XSS。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv", "md", "json", "xml",
            "png", "jpg", "jpeg", "gif", "bmp",
            "mp4", "webm", "mov",
            "zip", "rar", "7z"
    );

    /**
     * 单用户并发会话与未完成字节配额。
     *
     * <p>只统计 {@code UPLOADING}：已完成/失败会话的最终文件进入存储根，磁盘占用由部署侧配额治理；
     * 这里拦的是“同时挂起多个会话、长期占用分片目录”的风险。</p>
     */
    private void enforceUserQuota(Long uploaderId, Long fileSize) {
        if (uploaderId == null) {
            return;
        }
        long activeSessions = 0L;
        long activeBytes = 0L;
        List<FileUploadSession> active = this.list(new LambdaQueryWrapper<FileUploadSession>()
                .eq(FileUploadSession::getUploaderId, uploaderId)
                .eq(FileUploadSession::getStatus, "UPLOADING"));
        if (active != null) {
            activeSessions = active.size();
            for (FileUploadSession session : active) {
                if (session.getFileSize() != null && session.getFileSize() > 0) {
                    activeBytes += session.getFileSize();
                }
            }
        }
        if (activeSessions >= maxActiveSessionsPerUser) {
            throw new BusinessException(ApiCode.TOO_MANY_REQUESTS,
                    "上传会话过多：最多同时保留 " + maxActiveSessionsPerUser + " 个未完成会话");
        }
        if (fileSize != null && fileSize > 0 && activeBytes + fileSize > maxActiveBytesPerUser) {
            throw new BusinessException(ApiCode.TOO_MANY_REQUESTS,
                    "未完成上传总大小超出配额：最多 " + maxActiveBytesPerUser + " 字节");
        }
    }

    /**
     * 上传会话安全清洗文件名，同时校验扩展名白名单。
     * - 移除一切路径分量（防目录穿越）：仅保留最后一个路径分隔符后的纯文件名
     * - 校验扩展名是否在允许白名单内，非法则抛出异常
     */
    private String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            throw new BusinessException("文件名为空");
        }
        // 1) 仅取纯文件名部分（阻止 ../../etc/passwd、C:\windows\... 等路径穿越）
        String name = Paths.get(rawFilename).getFileName().toString();
        if (name == null || name.isBlank()) {
            throw new BusinessException("文件名无效");
        }
        // 2) 扩展名白名单校验
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx == -1 || dotIdx == name.length() - 1) {
            throw new BusinessException("文件缺少有效扩展名，允许的类型：" + String.join(", ", ALLOWED_EXTENSIONS));
        }
        String ext = name.substring(dotIdx + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不允许的文件类型（." + ext + "），允许的类型：" + String.join(", ", ALLOWED_EXTENSIONS));
        }
        return name;
    }

    /**
     * 创建上传会话
     * 生成上传路径（uploads/{year}/{month}/{uuid}_{filename}），设置24小时过期时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadSession createUploadSession(String filename, Long fileSize, Long totalChunks, Long uploaderId) {
        log.info("创建文件上传会话");

        // 安全清洗：防路径穿越 + 扩展名白名单
        String safeFilename = sanitizeFilename(filename);
        // 总大小/总分片数上限（修复：防分片绕过单次 50MB 限制无限累积拼超大文件）
        if (fileSize != null && fileSize > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException("文件过大，单文件上限 2GB");
        }
        if (totalChunks != null && totalChunks > MAX_TOTAL_CHUNKS) {
            throw new BusinessException("总分片数超出上限（最多 4096 片）");
        }
        enforceUserQuota(uploaderId, fileSize);
        // 对象名相对 file.upload-dir（与 LocalFileStorageServiceImpl / ResourceController 同口径），
        // 对外可访问 URL 为 /uploads/{uploadPath}，故此处刻意不带 "uploads/" 前缀
        LocalDate now = LocalDate.now();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String uploadPath = now.getYear() + "/" +
                String.format("%02d", now.getMonthValue()) + "/" + uuid + "_" + safeFilename;

        FileUploadSession session = new FileUploadSession();
        session.setFilename(safeFilename);
        session.setFileSize(fileSize);
        session.setTotalChunks(totalChunks.intValue());
        session.setUploadedChunks(0);
        session.setUploadPath(uploadPath);
        session.setStatus("UPLOADING");
        session.setUploaderId(uploaderId);
        session.setExpiresAt(LocalDateTime.now().plusHours(SESSION_EXPIRE_HOURS));
        this.save(session);
        return session;
    }

    /**
     * 归属校验（修复 P1-1 IDOR）：会话必须属于当前登录用户，否则 403 拒绝。
     */
    private void assertOwnership(FileUploadSession session, Long uploaderId) {
        if (session.getUploaderId() == null || uploaderId == null
                || !session.getUploaderId().equals(uploaderId)) {
            throw new BusinessException(403, "无权操作该上传会话");
        }
    }

    /**
     * 记录单个分片上传完成
     * 已上传分片数+1
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadSession uploadChunk(Long sessionId, Integer chunkIndex,
                                         InputStream chunkData, long size, String md5, Long uploaderId) {
        log.info("上传文件上传会话");

        FileUploadSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("上传会话不存在");
        }
        assertOwnership(session, uploaderId);
        if (!"UPLOADING".equals(session.getStatus())) {
            throw new BusinessException("当前会话状态不允许上传分片");
        }
        Integer totalChunks = session.getTotalChunks();
        if (chunkIndex == null || totalChunks == null || chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new BusinessException("分片索引越界：应为 0~"
                    + (totalChunks == null ? 0 : totalChunks - 1) + "，实际=" + chunkIndex);
        }
        if (size <= 0) {
            throw new BusinessException("分片内容为空");
        }
        if (size > MAX_CHUNK_SIZE_BYTES) {
            throw new BusinessException(ApiCode.PAYLOAD_TOO_LARGE,
                    "单个分片不能超过 " + MAX_CHUNK_SIZE_BYTES + " 字节");
        }
        // 配额：累计落盘字节不得超会话声明的文件大小（未声明则退回单文件上限），防客户端边传边改口
        long declaredSize = session.getFileSize() != null && session.getFileSize() > 0
                ? session.getFileSize() : MAX_FILE_SIZE_BYTES;
        long storedBytes = storedChunkBytes(sessionId);
        if (storedBytes + size > declaredSize) {
            throw new BusinessException("分片累计字节超过会话声明的文件大小：声明 " + declaredSize
                    + " 字节，已落盘 " + storedBytes + " 字节，本片 " + size + " 字节");
        }
        Path chunkFile = chunkRoot().resolve(String.valueOf(sessionId)).resolve(chunkIndex + CHUNK_SUFFIX);
        try {
            Files.createDirectories(chunkFile.getParent());
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream digested = new DigestInputStream(chunkData, digest)) {
                Files.copy(digested, chunkFile, StandardCopyOption.REPLACE_EXISTING);
            }
            if (md5 != null && !md5.isBlank()
                    && !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(md5.trim())) {
                Files.deleteIfExists(chunkFile);
                throw new BusinessException("分片摘要校验不通过（MD5 与声明不符），本片已丢弃");
            }
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(500, "分片摘要校验不可用", e);
        } catch (IOException e) {
            log.error("分片落盘失败: session={}, chunk={}, {}", sessionId, chunkIndex, e.getMessage());
            throw new BusinessException("分片写入失败");
        }
        // 进度以磁盘实际分片文件数为准：同一分片重传覆盖同名文件，不会被计成两片
        session.setUploadedChunks(countStoredChunks(sessionId));
        this.updateById(session);
        return session;
    }

    /** 会话已落盘的分片文件（磁盘是进度、配额与合并的唯一真值） */
    private List<Path> storedChunkFiles(Long sessionId) {
        Path dir = chunkRoot().resolve(String.valueOf(sessionId));
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(CHUNK_SUFFIX)).toList();
        } catch (IOException e) {
            log.error("分片目录读取失败: session={}, {}", sessionId, e.getMessage());
            throw new BusinessException("分片目录读取失败");
        }
    }

    /** 统计会话目录下已落盘的分片文件数（磁盘为进度的唯一真值） */
    private int countStoredChunks(Long sessionId) {
        return storedChunkFiles(sessionId).size();
    }

    /**
     * 已落盘分片的「0 基索引 → 文件」映射。
     * 文件名不可解析为整数时跳过并告警：进度与合并都走这条读路径，
     * 残留脏文件只能不被计入，不能把轮询端点打成 500。
     */
    private Map<Integer, Path> storedChunkIndexMap(Long sessionId) {
        Map<Integer, Path> byIndex = new HashMap<>();
        for (Path chunk : storedChunkFiles(sessionId)) {
            String name = chunk.getFileName().toString();
            try {
                byIndex.put(Integer.parseInt(name.substring(0, name.length() - CHUNK_SUFFIX.length())), chunk);
            } catch (NumberFormatException e) {
                log.warn("忽略非索引命名的分片残留: {}", chunk);
            }
        }
        return byIndex;
    }

    /** 声明范围内磁盘尚未落盘的索引（升序），进度出参与合并前置校验共用同一真值 */
    private List<Integer> missingChunkIndexes(Map<Integer, Path> chunkByIndex, int totalChunks) {
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            if (!chunkByIndex.containsKey(i)) {
                missing.add(i);
            }
        }
        return missing;
    }

    /** 统计会话已落盘分片的累计字节数，用于配额拦截 */
    private long storedChunkBytes(Long sessionId) {
        long total = 0L;
        for (Path chunk : storedChunkFiles(sessionId)) {
            try {
                total += Files.size(chunk);
            } catch (IOException e) {
                log.error("分片大小读取失败: {}, {}", chunk, e.getMessage());
                throw new BusinessException("分片大小读取失败");
            }
        }
        return total;
    }

    /**
     * 合并所有分片
     * 以磁盘分片为真值：按 0 基索引序拼接全部 .part 落到最终对象路径，校验声明尺寸后回收分片目录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadSession mergeChunks(Long sessionId, Long uploaderId) {
        log.info("合并文件上传会话");

        FileUploadSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("上传会话不存在");
        }
        assertOwnership(session, uploaderId);
        if (!"UPLOADING".equals(session.getStatus())) {
            throw new BusinessException("当前会话状态不允许合并");
        }
        Integer totalChunks = session.getTotalChunks();
        if (totalChunks == null || totalChunks <= 0) {
            throw new BusinessException("会话未声明有效的总分片数");
        }

        Map<Integer, Path> chunkByIndex = storedChunkIndexMap(sessionId);
        List<Integer> missing = missingChunkIndexes(chunkByIndex, totalChunks);
        if (!missing.isEmpty()) {
            throw new BusinessException("分片未全部上传完成，缺失索引：" + missing
                    + "（已落盘 " + chunkByIndex.size() + "/" + totalChunks + "）");
        }

        // 先做对象名越界校验；实际落盘交给 FileStorageService（Local/MinIO 共用同一入口）。
        resolveWithinUploadRoot(session.getUploadPath());
        Path merging = chunkRoot().resolve(sessionId + "-" + UUID.randomUUID() + ".merging");
        try {
            Files.createDirectories(chunkRoot());
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(merging))) {
                for (int i = 0; i < totalChunks; i++) {
                    Files.copy(chunkByIndex.get(i), out);
                }
            }
            long declared = session.getFileSize() == null ? 0L : session.getFileSize();
            long merged = Files.size(merging);
            if (declared > 0 && merged != declared) {
                Files.deleteIfExists(merging);
                session.setStatus("FAILED");
                this.updateById(session);
                throw new BusinessException("合并字节数与会话声明的文件大小不符：合并 "
                        + merged + " 字节，声明 " + declared + " 字节");
            }
            // 安全检查 1：按扩展名核对真实文件头，阻止把 .exe 改名成 .pdf 等“扩展名伪装”。
            String magic;
            try (InputStream headerIn = Files.newInputStream(merging)) {
                magic = UploadMagic.detect(UploadMagic.readHeader(headerIn));
            }
            if (!UploadMagic.matchesExtension(extensionOf(session.getFilename()), magic)) {
                session.setStatus("FAILED");
                this.updateById(session);
                throw new BusinessException(ApiCode.BAD_REQUEST,
                        "文件内容与扩展名不符，已拒绝合并上传");
            }
            // 安全检查 2：落盘前做病毒扫描；扫描器不可用时 fail-closed，不把“未扫描”当干净。
            virusScanService.scanOrThrow(merging, session.getFilename());
            // 统一经存储抽象落盘：Local 写外部根、MinIO 走上传 SDK，合并路径不再直写目录。
            fileStorageService.uploadFromPath(session.getUploadPath(), merging,
                    "application/octet-stream", merged);
            try {
                Files.deleteIfExists(merging);
            } catch (IOException cleanupFailure) {
                log.warn("合并临时文件清理失败（最终文件已落盘）: {}", merging);
            }
        } catch (BusinessException e) {
            try {
                Files.deleteIfExists(merging);
            } catch (IOException cleanupFailure) {
                log.warn("合并被拒后的临时产物清理失败: {}", merging);
            }
            if (!"FAILED".equals(session.getStatus())) {
                session.setStatus("FAILED");
                this.updateById(session);
            }
            throw e;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(merging);
            } catch (IOException cleanupFailure) {
                log.warn("合并中间产物清理失败: {}", merging);
            }
            log.error("分片合并失败: session={}, {}", sessionId, e.getMessage());
            throw new BusinessException(500, "分片合并失败", e);
        } catch (RuntimeException e) {
            try {
                Files.deleteIfExists(merging);
            } catch (IOException cleanupFailure) {
                log.warn("合并中间产物清理失败: {}", merging);
            }
            log.error("分片合并存储失败: session={}, {}", sessionId, e.getMessage());
            throw new BusinessException(500, "分片合并失败", e);
        }
        deleteChunkDir(sessionId);
        session.setStatus("COMPLETED");
        this.updateById(session);
        return session;
    }

    /** 取文件扩展名（不含点）；无扩展名返回空串。 */
    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot + 1) : "";
    }

    /**
     * 把会话存储的对象名解析为上传根内的绝对路径。
     * upload_path 列可空、且历史行存在 "/tmp/upload/xxx" 这类非本目录口径的值，故空值与越界都要拒绝。
     */
    private Path resolveWithinUploadRoot(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new BusinessException("会话未记录有效的存储路径");
        }
        Path root = uploadRoot();
        Path target = StorageLayout.resolveKey(root, objectName);
        if (target == null) {
            throw new BusinessException("会话存储路径越出上传根目录，已拒绝合并");
        }
        return target;
    }

    /** 回收会话的分片暂存目录；最终文件已提交，故回收失败只告警不回滚 */
    private void deleteChunkDir(Long sessionId) {
        Path dir = chunkRoot().resolve(String.valueOf(sessionId));
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> reversed = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : reversed) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            log.warn("分片目录回收失败: {}, {}", dir, e.getMessage());
        }
    }

    /**
     * 获取上传进度信息
     * <p>在途会话（UPLOADING／FAILED 等）的 uploaded/percentage/missingIndexes 一律取自磁盘分片，
     * 不信会话行的历史计数；**终态会话（COMPLETED／CANCELLED）反过来**——它们的分片目录已被
     * {@code deleteChunkDir} 清掉，磁盘真值只能得出「0 已传 + 还缺全部」，语义是错的，
     * 故终态回会话行的持久计数、missingIndexes 为空（B-13 ⑲）。{@code percentage} 仍是
     * 字符串（如 {@code "100.0%"}），类型不变。</p>
     */
    @Override
    public UploadProgressVO getUploadProgress(Long sessionId, Long uploaderId) {
        FileUploadSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("上传会话不存在");
        }
        assertOwnership(session, uploaderId);

        int total = session.getTotalChunks() != null && session.getTotalChunks() > 0
                ? session.getTotalChunks() : 0;

        if (isTerminalStatus(session.getStatus())) {
            int persisted = session.getUploadedChunks() != null ? session.getUploadedChunks() : 0;
            int bounded = total > 0 ? Math.min(persisted, total) : persisted;
            return new UploadProgressVO(bounded, total, percentageText(bounded, total),
                    session.getStatus(), List.of());
        }

        Map<Integer, Path> chunkByIndex = storedChunkIndexMap(sessionId);
        List<Integer> missing = missingChunkIndexes(chunkByIndex, total);
        int uploaded = total - missing.size();

        return new UploadProgressVO(uploaded, total, percentageText(uploaded, total), session.getStatus(), missing);
    }

    /** 终态：已完成或已取消——两种状态的分片目录都不再存在。 */
    private boolean isTerminalStatus(String status) {
        return "COMPLETED".equals(status) || "CANCELLED".equals(status);
    }

    /** 百分比文案；终态与在途共用一个算法，避免两条路径给出不同小数位。 */
    private String percentageText(int uploaded, int total) {
        double percentage = total > 0 ? Math.round((double) uploaded / total * 10000) / 100.0 : 0.0;
        return percentage + "%";
    }

    /**
     * 取消上传会话
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelUpload(Long sessionId, Long uploaderId) {
        log.info("取消文件上传会话");

        FileUploadSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("上传会话不存在");
        }
        assertOwnership(session, uploaderId);
        if ("COMPLETED".equals(session.getStatus()) || "CANCELLED".equals(session.getStatus())) {
            throw new BusinessException("该会话已完成或已取消，无法再次操作");
        }
        session.setStatus("CANCELLED");
        this.updateById(session);
        deleteChunkDir(sessionId);
    }

    /**
     * 清理过期会话
     * 删除 expiresAt < now 的记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanExpiredSessions() {
        List<FileUploadSession> expiredSessions = this.list(
                new LambdaQueryWrapper<FileUploadSession>()
                        .lt(FileUploadSession::getExpiresAt, LocalDateTime.now())
        );
        if (expiredSessions.isEmpty()) {
            return 0;
        }
        for (FileUploadSession session : expiredSessions) {
            this.removeById(session.getId());
            deleteChunkDir(session.getId());
        }
        return expiredSessions.size();
    }

    /**
     * 回收无主分片目录：目录名是会话 id，但会话行已不存在，或会话已进入终态。
     *
     * <p>存在理由：取消/过期/合并三处的事件式回收都以「会话行还在」为前提，行被直接删掉
     * （运维清库、导入覆盖）或事务半途失败时，分片就永久留在磁盘上，没人再会去清它。
     * UPLOADING 与 FAILED 的目录刻意保留——前者在续传，后者要靠这些片补传缺片。
     * 非会话 id 命名的子目录一律跳过：命名口径之外就推断不出归属，不该由本任务删。
     */
    @Override
    public int sweepOrphanChunkDirs() {
        Path root = chunkRoot();
        if (!Files.isDirectory(root)) {
            return 0;
        }
        List<Path> dirs;
        try (Stream<Path> children = Files.list(root)) {
            dirs = children.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            log.error("分片根目录读取失败: {}", root, e);
            throw new BusinessException("分片根目录读取失败");
        }
        Map<Path, Long> dirSessionIds = new HashMap<>();
        for (Path dir : dirs) {
            try {
                dirSessionIds.put(dir, Long.parseLong(dir.getFileName().toString()));
            } catch (NumberFormatException e) {
                log.warn("分片根目录下有非会话 id 命名的目录，跳过不回收: {}", dir);
            }
        }
        if (dirSessionIds.isEmpty()) {
            return 0;
        }
        // B-03：一次批量取会话行，避免「每个孤儿目录一次 selectById」的 N+1。
        Map<Long, FileUploadSession> sessions = this.listByIds(dirSessionIds.values()).stream()
                .collect(java.util.stream.Collectors.toMap(FileUploadSession::getId, s -> s));
        int swept = 0;
        for (Map.Entry<Path, Long> entry : dirSessionIds.entrySet()) {
            long sessionId = entry.getValue();
            FileUploadSession session = sessions.get(sessionId);
            boolean reclaimable = session == null
                    || "COMPLETED".equals(session.getStatus())
                    || "CANCELLED".equals(session.getStatus());
            if (reclaimable) {
                deleteChunkDir(sessionId);
                swept++;
            }
        }
        return swept;
    }
}
