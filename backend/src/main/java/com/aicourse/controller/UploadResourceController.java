package com.aicourse.controller;

import com.aicourse.entity.Resource;
import com.aicourse.service.FileStorageService;
import com.aicourse.service.ResourceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 模式下的 /uploads/** 读取入口。
 *
 * <p>本地模式仍由 {@code UploadResourceConfig} 的静态映射直出；启用 MinIO 后静态映射
 * 不再注册，否则它会去读一个已经不再写入的本地目录。这里保留同一个外部 URL 前缀，
 * 历史数据库 URL 与前端都无需迁移。</p>
 *
 * <p>认证与对象级 ACL 仍由 Spring Security + {@code UploadResourceAccessFilter} 负责；
 * 本控制器只负责把对象流转成响应，不能绕过过滤器直接暴露桶内对象。</p>
 */
@RestController
@Tag(name = "资源文件")
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class UploadResourceController {

    private static final Logger log = LoggerFactory.getLogger(UploadResourceController.class);
    private static final String UPLOAD_PREFIX = "/uploads/";

    /** 可被浏览器同源渲染的危险类型：即使历史记录声明了，也强制降级为下载流。 */
    private static final Set<String> UNSAFE_INLINE_TYPES = Set.of(
            "text/html",
            "application/xhtml+xml",
            "image/svg+xml"
    );

    private final FileStorageService fileStorageService;
    private final ResourceService resourceService;

    public UploadResourceController(FileStorageService fileStorageService, ResourceService resourceService) {
        this.fileStorageService = fileStorageService;
        this.resourceService = resourceService;
    }

    @GetMapping("/uploads/**")
    public ResponseEntity<org.springframework.core.io.Resource> serve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith(UPLOAD_PREFIX) || path.length() <= UPLOAD_PREFIX.length()) {
            return ResponseEntity.notFound().build();
        }

        InputStream input;
        try {
            input = fileStorageService.downloadByUrl(path);
        } catch (RuntimeException e) {
            log.warn("读取上传对象失败: path={}, reason={}", path, e.getMessage());
            return ResponseEntity.notFound().build();
        }
        if (input == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = resourceService.findByUrl(path);
        String fileName = resource != null && resource.getFileName() != null
                ? resource.getFileName()
                : path.substring(path.lastIndexOf('/') + 1);
        MediaType mediaType = safeMediaType(resource == null ? null : resource.getFileType(), fileName);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(new InputStreamResource(input));
    }

    private MediaType safeMediaType(String declaredType, String fileName) {
        if (declaredType != null && !declaredType.isBlank()) {
            try {
                MediaType mediaType = MediaType.parseMediaType(declaredType);
                if (!isUnsafeInlineType(mediaType)) {
                    return mediaType;
                }
            } catch (IllegalArgumentException ignored) {
                // 历史脏值按扩展名再判一次，不因单条错误声明打崩下载。
                log.debug("资源声明的 Content-Type 无法解析，改用扩展名推断: declaredType={}", declaredType);
            }
        }
        return MediaTypeFactory.getMediaType(fileName)
                .filter(mediaType -> !isUnsafeInlineType(mediaType))
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    private boolean isUnsafeInlineType(MediaType mediaType) {
        String baseType = mediaType.getType().toLowerCase(Locale.ROOT)
                + "/" + mediaType.getSubtype().toLowerCase(Locale.ROOT);
        return UNSAFE_INLINE_TYPES.contains(baseType) || baseType.contains("svg");
    }
}
