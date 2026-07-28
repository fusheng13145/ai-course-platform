package com.aicourse.llm;

import com.aicourse.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 路由型 LLM 供应商（支持运行时热切换）
 * <p>
 * 持有全部底层供应商实现（DeepSeek / OpenAI / Qwen），在每次调用时依据
 * {@code system_configs} 中 {@code ai.provider} 的当前值（缺省回退启动配置 {@code ai.provider}）动态选择，
 * 从而实现「管理员在配置页切换模型 → 立即生效、无需重启」。各底层供应商仍由
 * {@link LLMProviderConfig} 以单例方式构建并注入本类，业务层 {@code AIService} 仅依赖 {@link LLMProvider} 接口。
 */
@Component
@Slf4j
public class RoutingLLMProvider implements LLMProvider {

    private static final String CFG_PROVIDER = "ai.provider";

    private final LLMProvider deepSeek;
    private final LLMProvider openAi;
    private final LLMProvider qwen;
    private final SystemConfigService systemConfigService;
    private final String defaultProvider;

    public RoutingLLMProvider(DeepSeekProvider deepSeek,
                             OpenAiProvider openAi,
                             QwenProvider qwen,
                             SystemConfigService systemConfigService,
                             @Value("${ai.provider:deepseek}") String defaultProvider) {
        this.deepSeek = deepSeek;
        this.openAi = openAi;
        this.qwen = qwen;
        this.systemConfigService = systemConfigService;
        this.defaultProvider = defaultProvider;
    }

    /**
     * 解析当前应启用的底层供应商（实时读取配置，实现热切换）
     */
    private LLMProvider resolve() {
        String key = defaultProvider;
        try {
            String cfg = systemConfigService.getConfigValue(CFG_PROVIDER);
            if (cfg != null && !cfg.isBlank()) {
                key = cfg.trim().toLowerCase();
            }
        } catch (Exception e) {
            log.debug("读取 ai.provider 失败，回退启动配置: {}", e.getMessage());
        }
        return switch (key) {
            case "openai" -> openAi;
            case "qwen", "qwen2", "dashscope" -> qwen;
            default -> deepSeek;
        };
    }

    @Override
    public String name() {
        return "router(" + resolve().name() + ")";
    }

    @Override
    public boolean isConfigured() {
        return resolve().isConfigured();
    }

    @Override
    public String complete(List<Map<String, String>> messages, Double temperature) {
        return resolve().complete(messages, temperature);
    }

    @Override
    public String completeJson(List<Map<String, String>> messages, Double temperature, String jsonSchema) {
        return resolve().completeJson(messages, temperature, jsonSchema);
    }

    @Override
    public Flux<String> stream(List<Map<String, String>> messages, Double temperature) {
        return resolve().stream(messages, temperature);
    }

    /**
     * 供管理端热切换：持久化 ai.provider 到 system_configs（不存在则新建）
     */
    public void switchTo(String provider) {
        if (provider == null || provider.isBlank()) return;
        String existing = systemConfigService.getConfigValue(CFG_PROVIDER);
        if (existing == null) {
            com.aicourse.entity.SystemConfig cfg = new com.aicourse.entity.SystemConfig();
            cfg.setConfigKey(CFG_PROVIDER);
            cfg.setConfigValue(provider.trim().toLowerCase());
            cfg.setConfigGroup("ai");
            cfg.setConfigType("STRING");
            cfg.setDescription("当前启用的 LLM 供应商（运行时热切换）");
            cfg.setIsPublic(false);
            cfg.setSortOrder(0);
            systemConfigService.save(cfg);
        } else {
            systemConfigService.updateConfig(CFG_PROVIDER, provider.trim().toLowerCase());
        }
    }

    /**
     * 供管理端连通性测试：用当前供应商做一次最小补全
     */
    public boolean testConnection() {
        if (!isConfigured()) return false;
        try {
            String r = resolve().complete(
                    List.of(Map.of("role", "user", "content", "ping")), 0.0);
            return r != null && !r.isBlank();
        } catch (Exception e) {
            log.warn("LLM 连通性测试失败: {}", e.getMessage());
            return false;
        }
    }
}
