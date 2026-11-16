package com.aicourse.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * 上传存储布局的唯一口径。
 *
 * <p>默认根目录在仓库之外：{@code ~/.aicourse-platform/storage} 与
 * {@code ~/.aicourse-platform/chunks}。可用 {@code AICOURSE_DATA_DIR} 整体改根，
 * 或用 {@code FILE_UPLOAD_DIR} / {@code FILE_CHUNK_DIR} 分别覆盖。仓库内旧的
 * {@code uploads/} 只作为历史文件只读兼容位置，新文件不再写入该目录。</p>
 *
 * <p>对外 URL 仍保持 {@code /uploads/<key>}，由 {@code UploadResourceConfig}
 * 映射到外部存储根；因此前端、历史数据库 URL 与既有测试断言都不需要改前缀。</p>
 */
public final class StorageLayout {

    /** 对外统一逻辑前缀，保留旧口径以兼容历史数据。 */
    public static final String PUBLIC_PREFIX = "/uploads/";

    /** 旧版仓库内上传目录，仅用于历史文件回读/删除。 */
    public static final String LEGACY_UPLOAD_DIR = "uploads";

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

    /** 仓库内旧上传目录的绝对路径。 */
    public static Path legacyUploadRoot() {
        return Paths.get(LEGACY_UPLOAD_DIR).toAbsolutePath().normalize();
    }

    /**
     * 把落库 URL 或裸 key 解析成稳定的对象 key：去掉 {@code /uploads/} 或 {@code uploads/} 前缀。
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
     * 解析落库 URL 对应的本地文件路径：先查外部存储根，再回退旧仓库 {@code uploads/}。
     * 返回值可能是尚不存在的路径（下载方自行判断存在性）；非法 URL 返回 null。
     */
    public static Path resolveUrl(String storedUrl) {
        return resolveUrl(null, storedUrl);
    }

    /**
     * 解析落库 URL 对应的本地文件路径：显式根优先，再回退旧仓库 {@code uploads/}。
     */
    public static Path resolveUrl(String configuredDir, String storedUrl) {
        String key = objectKey(storedUrl);
        if (key == null) {
            return null;
        }
        Path primary = resolveKey(uploadRoot(configuredDir), key);
        if (primary != null && Files.isRegularFile(primary)) {
            return primary;
        }
        Path legacy = resolveKey(legacyUploadRoot(), key);
        if (legacy != null && Files.isRegularFile(legacy)) {
            return legacy;
        }
        return primary;
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
     * 从外部存储根读取输入流；不存在时回退旧仓库 {@code uploads/}。
     *
     * @return 文件输入流；不存在返回 null
     */
    public static java.io.InputStream openRead(String storedUrl) throws IOException {
        return openRead(null, storedUrl);
    }

    /**
     * 从指定外部存储根读取输入流；不存在时回退旧仓库 {@code uploads/}。
     */
    public static java.io.InputStream openRead(String configuredDir, String storedUrl) throws IOException {
        Path path = resolveUrl(configuredDir, storedUrl);
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        return Files.newInputStream(path);
    }

    /**
     * 删除落库 URL 对应的物理文件：先外部根，再旧仓库根；任一删除成功即返回 true。
     */
    public static boolean deleteByUrl(String storedUrl) {
        return deleteByUrl(null, storedUrl);
    }

    /**
     * 删除落库 URL 对应的物理文件：先指定外部根，再旧仓库根；任一删除成功即返回 true。
     */
    public static boolean deleteByUrl(String configuredDir, String storedUrl) {
        String key = objectKey(storedUrl);
        if (key == null) {
            return false;
        }
        for (Path root : new Path[]{uploadRoot(configuredDir), legacyUploadRoot()}) {
            Path target = resolveKey(root, key);
            if (target == null || !Files.isRegularFile(target)) {
                continue;
            }
            try {
                return Files.deleteIfExists(target);
            } catch (IOException e) {
                // 继续尝试下一个兼容位置，调用方只关心最终是否删除。
            }
        }
        return false;
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
