package com.aicourse.config;

import com.aicourse.common.StorageLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 上传文件静态资源映射（仅在本地存储模式注册）。
 * <p>
 * MinIO 模式由 {@code UploadResourceController} 流式读取对象，不能再注册这个本地
 * 资源映射，否则 {@code /uploads/**} 会指向一个已经不再写入的目录。</p>
 */
@Configuration
@ConditionalOnProperty(name = "minio.enabled", havingValue = "false", matchIfMissing = true)
public class UploadResourceConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(UploadResourceConfig.class);

    @Value("${file.upload-dir:}")
    private String uploadDir;

    /**
     * 将 {@code /uploads/**} 映射到外部存储根（唯一物理位置）。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = StorageLayout.uploadRoot(uploadDir).toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
        log.info("[UploadResourceConfig] 上传资源映射已注册：/uploads/** -> {}", location);
    }
}
