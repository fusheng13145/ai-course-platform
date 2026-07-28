package com.aicourse.llm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

/**
 * 兼容 OpenAI Chat Completions 协议的 LLM 供应商基类
 * <p>
 * DeepSeek、OpenAI、通义千问(Qwen) 等均提供与 OpenAI 一致的 {@code /chat/completions} 接口，
 * 因此共用同一套请求/重试/超时/JSON-Mode 逻辑，仅 base-url、模型名、鉴权头不同。
 * 新增同类供应商时继承本类并传入对应参数即可。
 */
@Slf4j
public abstract class OpenAiCompatibleProvider implements LLMProvider {


    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final WebClient streamWebClient;
    private final int maxRetries;

    protected OpenAiCompatibleProvider(String name,
                                        String apiKey,
                                        String baseUrl,
                                        String model,
                                        int connectTimeoutSec,
                                        int readTimeoutSec,
                                        int maxRetries) {
        this.name = name;
        this.apiKey = apiKey;
        this.model = model == null || model.isBlank() ? defaultModel() : model;
        this.maxRetries = Math.max(0, maxRetries);

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSec));
        // 读取超时：AI 生成可能较慢，默认给到 60s，避免长文本被中途掐断
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSec));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(factory)
                .build();

        // WebClient 用于 SSE 流式请求（长连接，不设 read timeout）
        this.streamWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 子类可覆盖，提供在无配置模型名时的默认模型
     */
    protected String defaultModel() {
        return "gpt-4o-mini";
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(List<Map<String, String>> messages, Double temperature) {
        return doComplete(messages, temperature, false, null);
    }

    @Override
    public String completeJson(List<Map<String, String>> messages, Double temperature, String jsonSchema) {
        return doComplete(messages, temperature, true, jsonSchema);
    }

    @Override
    public Flux<String> stream(List<Map<String, String>> messages, Double temperature) {
        if (!isConfigured()) {
            return Flux.empty();
        }

        var body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", (Object) (temperature != null ? temperature : 0.7),
                "stream", true
        );

        return streamWebClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> line != null && line.startsWith("data: "))
                .map(line -> line.substring(6).trim())
                .filter(data -> !"[DONE]".equals(data))
                .map(this::extractStreamDelta)
                .filter(Objects::nonNull);
    }

    /**
     * 从 SSE 流式响应 JSON 行中提取 content delta
     * <p>
     * OpenAI 兼容协议的流式 chunk 格式：
     * {@code {"choices":[{"delta":{"content":"..."}}]}}
     */
    private String extractStreamDelta(String data) {
        try {
            JsonNode root = MAPPER.readTree(data);
            if (!root.has("choices")) return null;
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) return null;
            JsonNode delta = choices.get(0).get("delta");
            if (delta == null) return null;
            JsonNode content = delta.get("content");
            if (content == null || content.isNull()) return null;
            return content.asText();
        } catch (Exception e) {
            log.debug("[{}] 流式数据解析失败: {} | raw={}", name, e.getMessage(),
                    data.length() > 120 ? data.substring(0, 120) + "…" : data);
            return null;
        }
    }

    private String doComplete(List<Map<String, String>> messages, Double temperature,
                              boolean jsonMode, String jsonSchema) {
        if (apiKey == null || apiKey.isBlank()) {
            return null; // 交由上层 AIService 处理“未配置”降级文案
        }
        if (jsonSchema != null && !jsonSchema.isBlank()) {
            messages = withJsonInstruction(messages, jsonSchema);
        }

        int attempt = 0;
        Exception last = null;
        while (attempt <= maxRetries) {
            try {
                var body = Map.of(
                        "model", model,
                        "messages", messages,
                        "temperature", temperature != null ? temperature : 0.7,
                        "response_format", Map.of("type", jsonMode ? "json_object" : "text")
                );
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restClient.post()
                        .uri("/chat/completions")
                        .body(body)
                        .retrieve()
                        .body(Map.class);
                if (resp == null || !resp.containsKey("choices")) {
                    log.warn("[{}] 返回异常，无 choices 字段", name);
                    return null;
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
                if (choices == null || choices.isEmpty()) {
                    return null;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                String content = msg == null ? null : (String) msg.get("content");
                if (content == null || content.isBlank()) {
                    return null;
                }
                return content;
            } catch (Exception e) {
                last = e;
                attempt++;
                log.warn("[{}] 请求失败，第{}次重试: {}", name, attempt, e.getMessage());
                if (attempt <= maxRetries) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("[{}] 多次失败，已达最大重试次数", name, last);
        return null;
    }

    /**
     * 将 JSON Schema 说明注入为一条 system 提示，引导模型以 JSON 对象返回
     */
    private List<Map<String, String>> withJsonInstruction(List<Map<String, String>> messages, String jsonSchema) {
        boolean hasSystem = messages.stream().anyMatch(m -> "system".equals(m.get("role")));
        String instruction = "你必须且仅能返回一个合法的 JSON 对象，不要包含任何额外解释文字。"
                + "JSON 结构要求如下：\n" + jsonSchema;
        if (hasSystem) {
            // 追加到第一条 system 消息之后
            var out = new java.util.ArrayList<Map<String, String>>();
            boolean inserted = false;
            for (var m : messages) {
                out.add(m);
                if (!inserted && "system".equals(m.get("role"))) {
                    out.add(Map.of("role", "system", "content", instruction));
                    inserted = true;
                }
            }
            return out;
        }
        var out = new java.util.ArrayList<Map<String, String>>();
        out.add(Map.of("role", "system", "content", instruction));
        out.addAll(messages);
        return out;
    }
}
