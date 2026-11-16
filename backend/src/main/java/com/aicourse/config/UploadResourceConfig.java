package com.aicourse.config;

import com.aicourse.common.StorageLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 上传文件静态资源映射
 * <p>
 * 对外仍保持 {@code /uploads/**} 逻辑前缀，避免改前端与历史数据库 URL；物理目录由
 * {@link StorageLayout} 解析到仓库外（默认 {@code ~/.aicourse-platform/storage}）。
 * 仓库内旧 {@code uploads/} 追加为只读兼容位置，历史文件仍可通过原 URL 读取。</p>
 * <p>
 * 安全说明：SecurityConfig 已将 {@code /uploads/**} 取消免认证（修复 F-01，2026-09-15），
 * 现需登录后方可访问；涉及权限控制的教学资源必须走 {@code /api/resources/download/{id}}
 * （已实现对象级鉴权 canAccessResource），不应依赖静态直链。</p>
 */
@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(UploadResourceConfig.class);

    @Value("${file.upload-dir:}")
    private String uploadDir;

    /**
     * 将 {@code /uploads/**} 映射到外部存储根，并把旧仓库 {@code uploads/} 作为只读兼容位置。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = StorageLayout.uploadRoot(uploadDir).toUri().toString();
        String legacy = StorageLayout.legacyUploadRoot().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location, legacy)
                .setCachePeriod(3600);
        log.info("[UploadResourceConfig] 上传资源映射已注册：/uploads/** -> {}（兼容旧目录 {}）",
                location, legacy);
    }
}
