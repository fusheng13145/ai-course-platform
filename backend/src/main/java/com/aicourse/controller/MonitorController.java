package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 前端错误监控上报接口（匿名引导型端点）。
 *
 * 承接前端 {@code monitoring} 模块上报的运行时异常（组件渲染异常 / 异步错误），
 * 服务端仅记录结构化 WARN 日志供生产排障，不落业务表、不做持久化，
 * 以避免放大攻击面（匿名可写路径最小化）。
 *
 * 安全约定（见 SecurityConfig / DoubleSubmitCsrfFilter）：
 * - {@code /api/monitor/**} 加入 Security publicPaths 匿名放行；
 * - 该端点加入 CSRF 双提交校验豁免前缀（引导型上报，浏览器场景无既有会话令牌）。
 *
 * 关闭或未配置前端上报（VITE_MONITOR_ENDPOINT 为空）时，本端点无调用方，静默不产生日志。
 */
@RestController
@RequestMapping("/api/monitor")
@Tag(name = "前端监控上报")
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
