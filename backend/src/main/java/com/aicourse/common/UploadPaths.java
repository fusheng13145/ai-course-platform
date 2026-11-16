package com.aicourse.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 上传目录的唯一定义来源（V-1 / V-2，2026-11-15）。
 *
 * <p>历史问题：{@code uploads/} 只写在 {@code ResourceController} 的私有常量里，
 * 其它地方（审核删除、分成）各自拼字符串，导致「删除只删库不删盘」长期无人发现。
 * 这里把路径解析、URL 反解、穿越防护集中一处，禁止再各写一份。</p>
 *
 * <p>口径：{@code /uploads/<storedName>} 落库，磁盘位置相对<b>进程工作目录</b>
 * （{@code start.bat} 从仓库根启动，即仓库根下的 {@code uploads/}）。</p>
 */
public final class UploadPaths {

    /** 相对工作目录的上传根，末尾带分隔符以便直接拼接。 */
    public static final String BASE_DIR = "uploads/";

    /** 落库 URL 前缀。 */
    public static final String URL_PREFIX = "/uploads/";

    private UploadPaths() {
    }

    /** 上传根目录的绝对规范路径。 */
    public static Path base() {
        return Paths.get(BASE_DIR).toAbsolutePath().normalize();
    }

    /**
     * 把落库 URL 反解为磁盘路径；非法或越界返回 {@code null}。
     *
     * @param url 形如 {@code /uploads/xxx.pdf}，也接受不带前导斜杠的 {@code uploads/xxx.pdf}
     * @return 规范化后的绝对路径，或 {@code null}
     */
    public static Path resolveUrl(String url) {
        if (url == null) {
            return null;
        }
        // 库里存在两种落库形态：'/uploads/x' 与相对形态 'uploads/x'
        // （UploadResourceAccessFilter.findResourceByStoredUrl 的回查条件已证明这一点），
        // 只认前导斜杠会漏删相对形态的历史行。
        String name;
        if (url.startsWith(URL_PREFIX)) {
            name = url.substring(URL_PREFIX.length());
        } else if (url.startsWith(BASE_DIR)) {
            name = url.substring(BASE_DIR.length());
        } else {
            name = url;
        }
        if (name.isEmpty() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return null;
        }
        Path base = base();
        Path target = base.resolve(name).normalize();
        return target.startsWith(base) ? target : null;
    }

    /**
     * 删除落库 URL 对应的磁盘文件（不存在或非法都返回 false，不抛异常）。
     *
     * @param url 落库 URL
     * @return 真的删掉了才返回 true
     */
    public static boolean deleteQuietly(String url) {
        Path target = resolveUrl(url);
        if (target == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            return false;
        }
    }
}
