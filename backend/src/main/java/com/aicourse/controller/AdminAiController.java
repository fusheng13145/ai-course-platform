package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.common.ApiResponse;
import com.aicourse.llm.RoutingLLMProvider;
import com.aicourse.vo.AdminAiCurrentVO;
import com.aicourse.vo.AdminAiTestVO;
import com.aicourse.vo.ProviderSwitchVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.aicourse.common.ApiCode;

/**
 * 管理端 AI 供应商运行时热切换控制器
 * 基础路径：/api/admin/ai
 * 管理员可在此切换底层大模型（deepseek / openai / qwen）并即时生效，无需重启。
 */
@Tag(name = "AI 供应商管理", description = "管理端大模型供应商热切换（deepseek/openai/qwen）")
@RestController
@RequestMapping("/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiController {

    private static final Logger log = LoggerFactory.getLogger(AdminAiController.class);

    private final RoutingLLMProvider routingLLMProvider;

    public AdminAiController(RoutingLLMProvider routingLLMProvider) {
        this.routingLLMProvider = routingLLMProvider;
    }

    /**
     * 查询当前供应商与配置状态
     * GET /api/admin/ai/current
     */
    @GetMapping("/current")
    public ApiResponse<AdminAiCurrentVO> current() {
        return ApiResponse.success(new AdminAiCurrentVO(
                currentKey(), routingLLMProvider.name(), routingLLMProvider.isConfigured()));
    }

    /**
     * 切换底层大模型（运行时热切换，持久化至 system_configs.ai.provider）
     * POST /api/admin/ai/provider  body: { "provider": "deepseek" | "openai" | "qwen" }
     */
    @PostMapping("/provider")
    public ApiResponse<ProviderSwitchVO> switchProvider(@Valid @RequestBody Map<String, String> body) {
        String provider = body.get("provider");
        log.debug("切换AI供应商 provider={}", provider);
        if (provider == null || provider.isBlank()) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "provider 不能为空");
        }
        routingLLMProvider.switchTo(provider);
        return ApiResponse.success("切换成功，已即时生效",
                new ProviderSwitchVO(currentKey(), routingLLMProvider.name()));
    }

    /**
     * 连通性测试：用当前供应商做一次最小补全
     * GET /api/admin/ai/test
     */
    @GetMapping("/test")
    public ApiResponse<AdminAiTestVO> test() {
        boolean ok = routingLLMProvider.testConnection();
        String message = ok ? "连接正常"
                : (routingLLMProvider.isConfigured() ? "调用失败，请检查 API Key / 网络" : "未配置 API Key");
        return ApiResponse.success(new AdminAiTestVO(
                ok, currentKey(), routingLLMProvider.isConfigured(), message));
    }

    private String currentKey() {
        // 与 RoutingLLMProvider 解析逻辑保持一致
        String n = routingLLMProvider.name(); // 形如 router(deepseek)
        int s = n.indexOf('(');
        int e = n.lastIndexOf(')');
        return (s >= 0 && e > s) ? n.substring(s + 1, e) : n;
    }
}
