package com.aicourse.config;

import com.aicourse.mapper.ResourceMapper;
import com.aicourse.security.CourseAccessControl;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 受限资源直链过滤器的注册（2026-11-06）。
 * <p>
 * 为什么不用 {@code @Component}：{@code @WebMvcTest} 会把 {@code Filter} 类型的组件一并纳入切片，
 * 而本过滤器需要 {@link ResourceMapper} 与 {@link CourseAccessControl}——那会让全部 web 切片
 * （本仓 20+ 个）因缺 bean 而无法启动上下文（实测 {@code No qualifying bean of type 'ResourceMapper'}）。
 * 显式注册在非切片配置里，切片不加载它，真实应用照常加载。
 * <p>
 * order 取 {@code 0}，必须晚于 Spring Security 过滤器链（默认 {@code -100}），
 * 否则 {@code SecurityContextHolder} 尚未填充，判定会一律按匿名走。
 */
@Configuration
public class UploadResourceFilterConfig {

    @Bean
    public FilterRegistrationBean<UploadResourceAccessFilter> uploadResourceAccessFilterRegistration(
            ResourceMapper resourceMapper, CourseAccessControl courseAccessControl) {
        FilterRegistrationBean<UploadResourceAccessFilter> registration =
                new FilterRegistrationBean<>(new UploadResourceAccessFilter(resourceMapper, courseAccessControl));
        registration.addUrlPatterns("/uploads/*");
        registration.setOrder(0);
        registration.setName("uploadResourceAccessFilter");
        return registration;
    }
}
