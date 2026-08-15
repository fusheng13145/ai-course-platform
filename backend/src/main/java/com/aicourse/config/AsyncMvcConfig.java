package com.aicourse.config;

import com.aicourse.security.AiQuotaInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MVC 异步支持配置
 * <p>
 * AI 类接口调用大模型耗时较长（秒级）。将 Controller 返回 {@code Callable} 时，Spring 会把
 * 实际执行放到独立的异步线程池，从而释放 Tomcat 请求线程，避免长耗时 AI 调用耗尽 Web 容器线程、
 * 拖垮登录/课程等非 AI 接口。客户端 HTTP 连接仍保持，结果就绪后自动返回。
 */
@Configuration
public class AsyncMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncMvcConfig.class);

    private final AiQuotaInterceptor aiQuotaInterceptor;

    public AsyncMvcConfig(AiQuotaInterceptor aiQuotaInterceptor) {
        this.aiQuotaInterceptor = aiQuotaInterceptor;
    }

    /**
     * 将 AI 配额拦截器绑定到 {@code /ai/**}（相对 servlet context-path=/api，实际为 /api/ai/**）。
     * 按认证 userId 施加每日请求次数与 token 预算的双重熔断，与按 IP 的通用限流互补。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(aiQuotaInterceptor).addPathPatterns("/ai/**");
    }

    /**
     * 异步执行线程池：AI 请求上限受此池约束，形成背压而非无限占用 Tomcat 线程。
     * <p>
     * 拒绝策略由 {@code CallerRunsPolicy} 改为 {@code DiscardOldestPolicy}：
     * 当队列与线程池均饱和时，丢弃最老的待处理 AI 任务并尝试提交新任务，
     * 避免在 Tomcat 请求线程上执行重耗时模型调用导致整个 Web 容器阻塞。
     * 被丢弃的请求会触发 {@link org.springframework.web.context.request.async.AsyncRequestTimeoutException}
     * 或容器级 503，客户端可据此做降级/重试。
     */
    @Bean(name = "mvcAsyncExecutor")
    public ThreadPoolTaskExecutor mvcAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mvc-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        log.info("[AsyncMvcConfig] AI 异步执行线程池初始化完成（core={}, max={}, queue={}）",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor;
    }

    /**
     * 绑定异步执行线程池与整体超时（默认 120s），超时则客户端收到 503。
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor());
        // 异步请求整体超时（毫秒）：超过则客户端收到 503，避免无限挂起
        configurer.setDefaultTimeout(120_000L);
    }
}
