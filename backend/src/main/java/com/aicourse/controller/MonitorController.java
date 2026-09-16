package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 前端错误监控上报接口（登录后可用）。
 *
 * 承接前端 {@code monitoring} 模块上报的运行时异常（组件渲染异常 / 异步错误），
 * 服务端仅记录结构化 WARN 日志供生产排障，不落业务表、不做持久化。
 *
 * 安全约定：本端点要求登录态（{@code isAuthenticated()}）。历史版本曾将其匿名放行，
 * 但匿名可写上报端点会放大攻击面，现收敛为与全仓一致的鉴权策略。
 * {@code /api/monitor/**} 已从 Security publicPaths 移除，CSRF 校验豁免前缀亦已移除。
 */
@RestController
@RequestMapping("/monitor")
@Tag(name = "前端监控上报")
@PreAuthorize("isAuthenticated()")
public class MonitorController {

    private static final Logger log = LoggerFactory.getLogger(MonitorController.class);

    /**
     * 前端运行时错误上报。
     *
     * @param payload 前端监控模块采集的异常上下文（message / stack / component / info / url / userAgent 等）
     * @return 恒为 success（上报类端点不做业务校验，避免把前端故障二次放大为 5xx）
     */
    @Operation(summary = "前端运行时错误上报（匿名）")
    @PostMapping("/frontend-error")
    public ApiResponse<Void> reportFrontendError(@RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            log.warn("[FrontendMonitor] 收到空上报（payload 缺失），忽略。");
            return ApiResponse.success();
        }
        // 字段可能为 null：统一经 valueOf 收敛，避免字符串拼接抛 NPE
        log.warn("[FrontendMonitor] message={}, component={}, info={}, url={}, userAgent={}, stack={}",
                String.valueOf(payload.get("message")),
                String.valueOf(payload.get("component")),
                String.valueOf(payload.get("info")),
                String.valueOf(payload.get("url")),
                String.valueOf(payload.get("userAgent")),
                String.valueOf(payload.get("stack")));
        return ApiResponse.success();
    }
}
