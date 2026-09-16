package com.aicourse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 上传文件静态资源映射
 * <p>
 * 修复 P1-24：上传接口返回的 {@code /uploads/xxx} 静态 URL 此前无任何后端映射，
 * 经 nginx 转发到 /api/uploads/xxx 后一律 404，图片/PDF/视频等资源无法加载。
 * 现映射 {@code /uploads/**} 到本地上传目录（与 {@code file.upload-dir} 对齐，
 * 默认 {@code uploads/}，即 LocalFileStorageServiceImpl 与 ResourceController 的落盘目录）。
 * <p>
 * 安全说明：SecurityConfig 已将 {@code /uploads/**} 取消免认证（修复 F-01，2026-09-15），
 * 现需登录后方可访问（文件名由服务端 UUID 生成、不可枚举，等价于需登录的 capability URL）；
 * 涉及权限控制的教学资源必须走 {@code /api/resources/download/{id}}（已实现对象级鉴权 canAccessResource），
 * 不应依赖静态直链，否则已登录用户仍可绕过课程级可见性 ACL。
 */
@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(UploadResourceConfig.class);

    @Value("${file.upload-dir:uploads/}")
    private String uploadDir;

    /**
     * 将 {@code /uploads/**} 映射到本地上传目录，供静态资源（图片/PDF/视频）直接访问。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
        log.info("[UploadResourceConfig] 上传资源映射已注册：/uploads/** -> {}", location);
    }
}
