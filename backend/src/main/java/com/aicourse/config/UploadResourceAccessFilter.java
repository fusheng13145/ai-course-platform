package com.aicourse.config;

import com.aicourse.common.JsonUtils;
import com.aicourse.entity.Resource;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ResourceMapper;
import com.aicourse.common.ApiCode;
import com.aicourse.security.CourseAccessControl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * 上传直链的对象级 ACL（B-05 余量，2026-11-06 第二十二批）。
 * <p>
 * 事实基线：资源以 {@code /uploads/<uuid><suffix>} 落库（{@code ResourceController} 上传分支），
 * 而 {@code /uploads/**} 由静态处理器直出，SecurityConfig 只保证「已登录」（F-01 修复）。
 * DR-16 关掉的是<b>列表枚举</b>面（listByCourse/tree/stats 无 ACL），当时留了一句判据：
 * 「涉及权限控制的教学资源不应依赖静态直链」。本过滤器就是补上那一句——
 * 只针对数据模型已经声明为「非全员可见」（{@code resources.visible_to_all != TRUE}）的资源：
 * 这类文件既然被标成受限，就不该因为「知道 UUID」而被任意登录用户读走。
 * <p>
 * 三条边界，避免把改动做成行为回归：
 * <ol>
 *   <li>路径查不到资源行 ⇒ <b>不改判</b>，沿用既有「需登录的 capability URL」语义
 *       （课程封面、头像等由别处引用的文件不属于资源模型，强判即坏功能）；</li>
 *   <li>{@code visible_to_all = TRUE} ⇒ 放行，与现网默认值一致（上传默认就是 true）；</li>
 *   <li>{@code visible_to_all = false} ⇒ 复用 {@link CourseAccessControl#assertCanReadCourse}
 *       （管理员放行／教师须是本课程负责人／学生须有 APPROVED 选课），
 *       与 {@code /resources/download/{id}} 的判定同源，不在这里另写一份成员关系规则。</li>
 * </ol>
 * 顺序必须在 Spring Security 过滤器链之后（默认 order = -100），否则拿不到 SecurityContext。
 */
public class UploadResourceAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UploadResourceAccessFilter.class);

    private static final String UPLOAD_PREFIX = "/uploads/";

    private final ResourceMapper resourceMapper;
    private final CourseAccessControl courseAccessControl;

    public UploadResourceAccessFilter(ResourceMapper resourceMapper,
                                      CourseAccessControl courseAccessControl) {
        this.resourceMapper = resourceMapper;
        this.courseAccessControl = courseAccessControl;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 路径判定只在这一处做：OncePerRequestFilter 的 shouldNotFilter 依赖容器属性写入时机，
        // 单元层不可靠，留两份判定反而分叉。
        String relative = uploadRelativePath(request);
        if (relative.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        Resource resource = findResourceByStoredUrl(relative);
        if (resource == null || Boolean.TRUE.equals(resource.getVisibleToAll())) {
            chain.doFilter(request, response);
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            courseAccessControl.assertCanReadCourse(resource.getCourseId(), auth);
        } catch (BusinessException e) {
            log.debug("[uploads-acl] 拒绝受限资源直链：path={}, resourceId={}, courseId={}, code={}",
                    relative, resource.getId(), resource.getCourseId(), e.getCode());
            deny(response, e);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 直接写响应体，不用 {@code response.sendError}：后者会触发容器的 ERROR 转发，
     * 而那次转发不带 SecurityContext，会被 {@code JwtAuthenticationEntryPoint} 判成匿名，
     * 最终读成 401「未授权，请先登录」而不是 403（2026-11-06 真栈实测到这一点）。
     */
    private void deny(HttpServletResponse response, BusinessException e) throws IOException {
        int status = e.getCode() == ApiCode.FORBIDDEN
                ? HttpServletResponse.SC_FORBIDDEN
                : HttpServletResponse.SC_BAD_REQUEST;
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        // 信封只写 code/message/data：ApiResponse 带 LocalDateTime，而 JsonUtils 用的是未注册
        // JavaTimeModule 的裸 mapper（实测直接抛 InvalidDefinitionException ⇒ 拒绝变 500）。
        Map<String, Object> envelope = new java.util.LinkedHashMap<>();
        envelope.put("code", e.getCode());
        envelope.put("message", e.getMessage());
        envelope.put("data", null);
        response.getWriter().write(JsonUtils.toJson(envelope));
    }

    /** 请求里去掉 context-path 后的 {@code /uploads/} 之后部分；不是直链则返回空串。 */
    static String uploadRelativePath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        int index = path.indexOf(UPLOAD_PREFIX);
        if (index < 0) {
            return "";
        }
        return path.substring(index + UPLOAD_PREFIX.length());
    }

    /**
     * 按落库形态回查资源行：上传分支写的是 {@code "/uploads/" + uniqueFileName}，
     * 这里两种形态都试一次，避免依赖字符串拼接的单一写法。
     */
    private Resource findResourceByStoredUrl(String relative) {
        if (relative.isEmpty() || relative.contains("..")) {
            return null;
        }
        return resourceMapper.selectOne(new LambdaQueryWrapper<Resource>()
                .in(Resource::getUrl, "/uploads/" + relative, relative)
                .last("LIMIT 1"));
    }
}
