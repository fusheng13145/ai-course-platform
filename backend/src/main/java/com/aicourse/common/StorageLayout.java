package com.aicourse.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 上传存储布局的唯一口径。
 *
 * <p>默认根目录在仓库之外：{@code ~/.aicourse-platform/storage} 与
 * {@code ~/.aicourse-platform/chunks}。可用 {@code AICOURSE_DATA_DIR} 整体改根，
 * 或用 {@code FILE_UPLOAD_DIR} / {@code FILE_CHUNK_DIR} 分别覆盖。仓库内已不再保留
 * {@code uploads/} 目录：所有读写只走外部存储根，不存在「外部缺失再回退仓库」的路径。</p>
 *
 * <p>对外 URL 仍保持 {@code /uploads/<key>}：本地模式由 {@code UploadResourceConfig}
 * 映射到外部存储根；MinIO 模式由 {@code UploadResourceController} 代理读取对象存储。
 * 因此前端与历史数据库 URL 都不需要改前缀。</p>
 */
public final class StorageLayout {

    private static final Logger log = LoggerFactory.getLogger(StorageLayout.class);

    /** 对外统一逻辑前缀，保留旧口径以兼容历史数据。 */
    public static final String PUBLIC_PREFIX = "/uploads/";

    private StorageLayout() {
    }

    /** 仓库外数据根：AICOURSE_DATA_DIR > aicourse.data-dir > ~/.aicourse-platform。 */
    public static Path dataRoot() {
        String fromEnv = System.getenv("AICOURSE_DATA_DIR");
        if (!isBlank(fromEnv)) {
            return Paths.get(fromEnv).toAbsolutePath().normalize();
        }
        String fromProperty = System.getProperty("aicourse.data-dir");
        if (!isBlank(fromProperty)) {
            return Paths.get(fromProperty).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.home"), ".aicourse-platform")
                .toAbsolutePath().normalize();
    }

    /** 最终文件根：显式配置优先，否则 AICOURSE_DATA_DIR/storage。 */
    public static Path uploadRoot(String configuredDir) {
        if (!isBlank(configuredDir)) {
            return Paths.get(configuredDir).toAbsolutePath().normalize();
        }
        String fromEnv = System.getenv("FILE_UPLOAD_DIR");
        if (!isBlank(fromEnv)) {
            return Paths.get(fromEnv).toAbsolutePath().normalize();
        }
        return dataRoot().resolve("storage").toAbsolutePath().normalize();
    }

    /** 最终文件根（默认口径）。 */
    public static Path uploadRoot() {
        return uploadRoot(null);
    }

    /** 分片暂存根：显式配置优先，否则 AICOURSE_DATA_DIR/chunks。 */
    public static Path chunkRoot(String configuredDir) {
        if (!isBlank(configuredDir)) {
            return Paths.get(configuredDir).toAbsolutePath().normalize();
        }
        String fromEnv = System.getenv("FILE_CHUNK_DIR");
        if (!isBlank(fromEnv)) {
            return Paths.get(fromEnv).toAbsolutePath().normalize();
        }
        return dataRoot().resolve("chunks").toAbsolutePath().normalize();
    }

    /** 分片暂存根（默认口径）。 */
    public static Path chunkRoot() {
        return chunkRoot(null);
    }

    /**
     * 把落库 URL 或裸 key 解析成稳定的对象 key：去掉 {@code /uploads/} 或 {@code uploads/} 前缀。
     *
     * <p>{@code uploads/} 这种无前导斜杠的形态来自历史数据，仍需接受；它同样映射到
     * 外部存储根，与仓库目录无关。</p>
     *
     * @return 合法 key；含穿越片段、绝对路径、Windows 盘符或空值时返回 null
     */
    public static String objectKey(String storedUrl) {
        if (isBlank(storedUrl)) {
            return null;
        }
        String raw = storedUrl.trim().replace('\\', '/');
        if (raw.startsWith(PUBLIC_PREFIX)) {
            raw = raw.substring(PUBLIC_PREFIX.length());
        } else if (raw.startsWith("uploads/")) {
            raw = raw.substring("uploads/".length());
        }
        if (raw.isBlank() || raw.startsWith("/") || raw.contains(":")) {
            return null;
        }
        if (raw.equals("..") || raw.startsWith("../") || raw.endsWith("/..") || raw.contains("/../")) {
            return null;
        }
        Path normalized = Paths.get(raw).normalize();
        String key = normalized.toString().replace('\\', '/');
        if (key.isBlank() || key.startsWith("..") || key.contains("/../")) {
            return null;
        }
        return key;
    }

    /**
     * 把 key 解析到指定根内；越界返回 null。
     */
    public static Path resolveKey(Path root, String objectName) {
        String key = objectKey(objectName);
        if (key == null) {
            return null;
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(key).normalize();
        return target.startsWith(normalizedRoot) ? target : null;
    }

    /**
     * 解析落库 URL 对应的本地文件路径（默认外部根）。
     * 返回值可能是尚不存在的路径（下载方自行判断存在性）；非法 URL 返回 null。
     */
    public static Path resolveUrl(String storedUrl) {
        return resolveUrl(null, storedUrl);
    }

    /**
     * 解析落库 URL 对应的本地文件路径（显式根优先，否则默认外部根）。
     */
    public static Path resolveUrl(String configuredDir, String storedUrl) {
        String key = objectKey(storedUrl);
        if (key == null) {
            return null;
        }
        return resolveKey(uploadRoot(configuredDir), key);
    }

    /** 将 key 转成对外 URL。 */
    public static String publicUrl(String objectName) {
        String key = objectKey(objectName);
        if (key == null) {
            throw new IllegalArgumentException("非法存储对象名: " + objectName);
        }
        return PUBLIC_PREFIX + key;
    }

    /**
     * 写入字节到外部存储根，父目录自动创建。
     *
     * @return 实际落盘路径
     */
    public static Path writeBytes(String objectName, byte[] bytes) throws IOException {
        return writeBytes(null, objectName, bytes);
    }

    /**
     * 写入字节到指定外部存储根，父目录自动创建。
     */
    public static Path writeBytes(String configuredDir, String objectName, byte[] bytes) throws IOException {
        Path target = resolveKey(uploadRoot(configuredDir), objectName);
        if (target == null) {
            throw new IOException("非法存储对象名: " + objectName);
        }
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        return target;
    }

    /**
     * 从外部存储根读取输入流。
     *
     * @return 文件输入流；不存在返回 null
     */
    public static java.io.InputStream openRead(String storedUrl) throws IOException {
        return openRead(null, storedUrl);
    }

    /**
     * 从指定外部存储根读取输入流。
     */
    public static java.io.InputStream openRead(String configuredDir, String storedUrl) throws IOException {
        Path path = resolveUrl(configuredDir, storedUrl);
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        return Files.newInputStream(path);
    }

    /**
     * 删除落库 URL 对应的物理文件（默认外部根）；删除成功返回 true。
     */
    public static boolean deleteByUrl(String storedUrl) {
        return deleteByUrl(null, storedUrl);
    }

    /**
     * 删除落库 URL 对应的物理文件（显式根优先，否则默认外部根）；删除成功返回 true。
     */
    public static boolean deleteByUrl(String configuredDir, String storedUrl) {
        String key = objectKey(storedUrl);
        if (key == null) {
            return false;
        }
        Path target = resolveKey(uploadRoot(configuredDir), key);
        if (target == null || !Files.isRegularFile(target)) {
            return false;
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            // 删除失败被上层当成「文件不在了」，必须以 warn 留痕，否则存储泄漏无从排查
            log.warn("删除存储对象失败: key={}, cause={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 把源文件移动到外部存储根（同盘移动，不复制大文件）。
     */
    public static Path moveIntoUploadRoot(Path source, String objectName) throws IOException {
        Path target = resolveKey(uploadRoot(), objectName);
        if (target == null) {
            throw new IOException("非法存储对象名: " + objectName);
        }
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /** 判断文件扩展名使用的小写后缀，供日志/诊断使用。 */
    public static String suffixOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
