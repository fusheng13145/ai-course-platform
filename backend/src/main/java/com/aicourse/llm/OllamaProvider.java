package com.aicourse.llm;

import com.aicourse.exception.LLMUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 本地 Ollama 供应商（默认 {@code deepseek-r1:8b}）。
 *
 * <p>与 DeepSeek / OpenAI / Qwen 不同，Ollama 使用原生 {@code POST /api/chat} 协议且<b>无需 API Key</b>，
 * 因此无法复用 {@link OpenAiCompatibleProvider}（后者以「Key 非空」作为可用性判据，且固定打
 * {@code /chat/completions}）。本类按 {@code LLMProvider} 契约独立实现，业务层无感。</p>
 *
 * <p><b>思考内容与正式答案分离</b>：deepseek-r1 属思考型模型。Ollama 0.6+ 会把推理过程放在
 * {@code message.thinking}，最终答案放在 {@code message.content}；部分旧模板则把
 * {@code <think>…</think>} / {@code [THINK]…[/THINK]} 内联在 {@code content} 里。
 * 本类只把清洗后的 {@code content} 作为返回值，{@code thinking} 永不进入正式输出，
 * 仅在日志里记录长度，便于排查「只思考不回答」与输出被截断两种形态。</p>
 *
 * <p>异常口径与 {@link OpenAiCompatibleProvider} 保持一致：未配置返回 {@code null}；
 * 调用失败一律抛 {@link LLMUnavailableException}（HTTP 503），并给出可操作的原因
 * （服务未启动 / 模型不存在 / 超时 / 上游状态码）。</p>
 */
@Slf4j
@Component
public class OllamaProvider implements LLMProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 内联思考块：兼容 {@code <think>} / {@code <thinking>}、方括号写法与 DeepSeek 特殊 token。 */
    private static final Pattern INLINE_THINK = Pattern.compile(
            "(?is)<think(?:ing)?>.*?</think(?:ing)?>"
                    + "|\\[think\\].*?\\[/think\\]"
                    + "|<\\|begin_of_thought\\|>.*?<\\|end_of_thought\\|>"
                    + "|<｜begin▁of▁thinking｜>.*?<｜end▁of▁thinking｜>");

    /** 未闭合的思考块：整段丢弃，宁可返回空响应触发 503，也不把推理过程当答案。 */
    private static final Pattern UNCLOSED_THINK = Pattern.compile(
            "(?is)<think(?:ing)?>.*$|\\[think\\].*$|<\\|begin_of_thought\\|>.*$|<｜begin▁of▁thinking｜>.*$");

    private final String baseUrl;
    private final String model;
    /** 三态：null=不下发（由模型模板决定），true/false=显式要求 Ollama 开启/关闭思考。 */
    private final Boolean think;
    private final RestClient restClient;
    private final WebClient streamWebClient;
    private final int maxRetries;

    public OllamaProvider(
            @Value("${ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ai.ollama.model:deepseek-r1:8b}") String model,
            @Value("${ai.ollama.think:}") String think,
            @Value("${ai.connect-timeout:5}") int connectTimeout,
            @Value("${ai.read-timeout:60}") int readTimeout,
            @Value("${ai.max-retries:2}") int maxRetries) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.model = model == null ? "" : model.trim();
        this.think = think == null || think.isBlank() ? null : Boolean.valueOf(think.trim());
        this.maxRetries = Math.max(0, maxRetries);

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));

        // Ollama 无鉴权头；base-url 与模型名均可由 OLLAMA_* 环境变量覆盖。
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(factory)
                .build();
        this.streamWebClient = WebClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.debug("OllamaProvider 初始化完成, base-url={}, model={}, think={}", this.baseUrl, this.model, think);
    }

    @Override
    public String name() {
        return "ollama";
    }

    /**
     * Ollama 不需要 API Key：base-url 与模型名非空即视为可用。
     * 任一为空时返回 {@code null}，上层按「未配置」给出 503 与可操作文案。
     */
    @Override
    public boolean isConfigured() {
        return !baseUrl.isBlank() && !model.isBlank();
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
        return Flux.defer(() -> {
            ThinkTagFilter filter = new ThinkTagFilter();
            var body = buildBody(messages, temperature, false, true);
            Flux<String> content = streamWebClient.post()
                    .uri("/api/chat")
                    .bodyValue(body)
                    .accept(MediaType.APPLICATION_NDJSON)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .flatMap(line -> {
                        String delta = parseStreamLine(line, filter);
                        // Reactor 的 map 不允许 null：thinking / 结束行 / 空增量直接跳过
                        return delta == null ? Flux.<String>empty() : Flux.just(delta);
                    })
                    .onErrorMap(this::toUnavailable);
            return content.concatWith(Flux.defer(() -> {
                String tail = filter.flush();
                return tail == null ? Flux.<String>empty() : Flux.just(tail);
            }));
        });
    }

    private String doComplete(List<Map<String, String>> messages, Double temperature,
                              boolean jsonMode, String jsonSchema) {
        if (!isConfigured()) {
            // 与既有供应商契约一致：null 是「未配置」的唯一信号，上层转 503 NOT_CONFIGURED
            return null;
        }
        List<Map<String, String>> effective = messages;
        if (jsonSchema != null && !jsonSchema.isBlank()) {
            effective = withJsonInstruction(messages, jsonSchema);
        }

        int attempt = 0;
        Exception last = null;
        while (attempt <= maxRetries) {
            try {
                var body = buildBody(effective, temperature, jsonMode, false);
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restClient.post()
                        .uri("/api/chat")
                        .body(body)
                        .retrieve()
                        .body(Map.class);
                return extractContent(resp);
            } catch (LLMUnavailableException e) {
                throw e;
            } catch (HttpClientErrorException e) {
                // 4xx 是确定性失败（模型不存在、请求体不合法），重试只会拉长响应时间
                throw failureWithStatus(e.getStatusCode().value(), e);
            } catch (Exception e) {
                last = e;
                attempt++;
                log.warn("[ollama] 请求失败，第{}次重试: {}", attempt, e.getMessage());
                if (attempt <= maxRetries) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        last = ie;
                        break;
                    }
                }
            }
        }
        throw transportFailure(last);
    }

    /** Ollama 的采样参数放在 {@code options} 内；{@code format=json} 等价于 JSON Mode。 */
    private Map<String, Object> buildBody(List<Map<String, String>> messages, Double temperature,
                                           boolean jsonMode, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", stream);
        if (think != null) {
            body.put("think", think);
        }
        if (temperature != null) {
            body.put("options", Map.of("temperature", temperature));
        }
        if (jsonMode) {
            body.put("format", "json");
        }
        return body;
    }

    /**
     * 从非流式响应中取最终答案。
     * <p>只有 {@code message.content} 算正式输出；{@code message.thinking} 仅记录长度。
     * content 为空（例如模型只思考、或 num_predict 截断在思考段）按 EMPTY_RESPONSE 失败，
     * 绝不回退把 thinking 当成答案。</p>
     */
    private String extractContent(Map<String, Object> resp) {
        if (resp == null) {
            throw empty("大模型 [ollama] 返回体为空");
        }
        Object messageObj = resp.get("message");
        if (!(messageObj instanceof Map<?, ?> msg)) {
            throw empty("大模型 [ollama] 返回体缺少 message 字段");
        }
        String content = msg.get("content") instanceof String s ? s : null;
        String thinking = msg.get("thinking") instanceof String s ? s : null;
        String answer = stripThink(content);
        if (answer == null || answer.isBlank()) {
            log.warn("[ollama] 模型 [{}] 未产出最终答案（thinking={} 字符, done_reason={}）",
                    model, thinking == null ? 0 : thinking.length(), resp.get("done_reason"));
            throw empty("大模型 [ollama] 未返回最终答案（thinking="
                    + (thinking == null ? 0 : thinking.length())
                    + " 字符，仅思考内容不算输出；若持续如此可调大 AI_READ_TIMEOUT 或减小输入长度）");
        }
        return answer;
    }

    /** 清洗内联思考块；返回 null 表示没有可用内容。 */
    static String stripThink(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String cleaned = INLINE_THINK.matcher(content).replaceAll("");
        cleaned = UNCLOSED_THINK.matcher(cleaned).replaceAll("");
        return cleaned.trim();
    }

    /** 解析 Ollama 流式 NDJSON 单行：跳过 thinking 与结束行，只回传清洗后的 content 增量。 */
    private String parseStreamLine(String line, ThinkTagFilter filter) {
        if (line == null || line.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(line);
            if (root.path("done").asBoolean(false)) {
                return null;
            }
            JsonNode message = root.get("message");
            if (message == null) {
                return null;
            }
            JsonNode content = message.get("content");
            if (content == null || content.isNull()) {
                return null;
            }
            String text = content.asText();
            return text.isEmpty() ? null : filter.feed(text);
        } catch (Exception e) {
            log.debug("[ollama] 流式数据解析失败: {} | raw={}", e.getMessage(),
                    line.length() > 120 ? line.substring(0, 120) + "…" : line);
            return null;
        }
    }

    private LLMUnavailableException failureWithStatus(int status, Exception e) {
        if (status == 404) {
            return new LLMUnavailableException(LLMUnavailableException.Kind.UPSTREAM_UNREACHABLE,
                    String.format("Ollama 端点或模型不存在（HTTP 404）：请确认 base-url=%s 指向 Ollama 服务，"
                            + "并已执行 `ollama pull %s`", baseUrl, model), e);
        }
        var kind = LLMUnavailableException.kindOfStatus(status);
        return new LLMUnavailableException(kind,
                String.format("大模型 [ollama] 上游返回 HTTP %d（base-url=%s, model=%s）", status, baseUrl, model), e);
    }

    /** 传输层失败 → 可操作的 503 文案（区分「服务未启动」与「推理超时」）。 */
    private LLMUnavailableException transportFailure(Throwable e) {
        Throwable root = e;
        while (root != null && root.getCause() != null) {
            root = root.getCause();
        }
        if (root instanceof ConnectException) {
            return new LLMUnavailableException(LLMUnavailableException.Kind.UPSTREAM_UNREACHABLE,
                    String.format("无法连接 Ollama 服务（base-url=%s）：请确认已在本机执行 `ollama serve`，"
                            + "且模型 %s 已通过 `ollama pull %s` 安装", baseUrl, model, model), e);
        }
        if (root instanceof SocketTimeoutException) {
            return new LLMUnavailableException(LLMUnavailableException.Kind.UPSTREAM_UNREACHABLE,
                    String.format("调用 Ollama 超时（base-url=%s, model=%s）：本地推理可能过慢或模型仍在加载，"
                            + "可调大 AI_READ_TIMEOUT（当前 %d 次重试）", baseUrl, model, maxRetries), e);
        }
        return new LLMUnavailableException(LLMUnavailableException.Kind.UPSTREAM_UNREACHABLE,
                String.format("大模型 [ollama] 调用失败：重试 %d 次仍未成功（base-url=%s, 最近原因：%s）",
                        maxRetries, baseUrl, e == null ? "未知" : e.getMessage()), e);
    }

    private LLMUnavailableException toUnavailable(Throwable t) {
        if (t instanceof LLMUnavailableException e) {
            return e;
        }
        if (t instanceof WebClientResponseException e) {
            return failureWithStatus(e.getStatusCode().value(), e);
        }
        if (t instanceof WebClientRequestException e) {
            return transportFailure(e);
        }
        return new LLMUnavailableException(LLMUnavailableException.Kind.UPSTREAM_UNREACHABLE,
                String.format("大模型 [ollama] 流式调用失败（base-url=%s）：%s", baseUrl, t.getMessage()), t);
    }

    private LLMUnavailableException empty(String message) {
        return new LLMUnavailableException(LLMUnavailableException.Kind.EMPTY_RESPONSE, message);
    }

    /** 与 OpenAiCompatibleProvider 相同的 JSON Schema 注入口径。 */
    private List<Map<String, String>> withJsonInstruction(List<Map<String, String>> messages, String jsonSchema) {
        String instruction = "你必须且仅能返回一个合法的 JSON 对象，不要包含任何额外解释文字。"
                + "JSON 结构要求如下：\n" + jsonSchema;
        boolean hasSystem = messages.stream().anyMatch(m -> "system".equals(m.get("role")));
        var out = new java.util.ArrayList<Map<String, String>>();
        if (!hasSystem) {
            out.add(Map.of("role", "system", "content", instruction));
            out.addAll(messages);
            return out;
        }
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

    /**
     * 流式内联思考块过滤器：跨 chunk 保持状态，输出中绝不出现思考内容。
     * <p>{@code message.thinking} 已在解析层丢弃；本类只处理 content 里内联的
     * {@code <think>}/{@code [THINK]} 标记，且容忍标记被拆到相邻 chunk。</p>
     */
    static final class ThinkTagFilter {

        private static final String OPEN_ANGLE = "<think";
        private static final String OPEN_BRACKET = "[think]";
        private static final String CLOSE_ANGLE = "</think";
        private static final String CLOSE_BRACKET = "[/think]";

        private final StringBuilder buffer = new StringBuilder();
        private boolean inThink = false;

        String feed(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return null;
            }
            buffer.append(chunk);
            StringBuilder out = new StringBuilder();
            while (true) {
                String lower = buffer.toString().toLowerCase(Locale.ROOT);
                if (inThink) {
                    int angle = lower.indexOf(CLOSE_ANGLE);
                    int bracket = lower.indexOf(CLOSE_BRACKET);
                    if (angle >= 0 && (bracket < 0 || angle <= bracket)) {
                        int gt = lower.indexOf('>', angle);
                        if (gt < 0) {
                            buffer.delete(0, angle);
                            break;
                        }
                        buffer.delete(0, gt + 1);
                        inThink = false;
                    } else if (bracket >= 0) {
                        buffer.delete(0, bracket + CLOSE_BRACKET.length());
                        inThink = false;
                    } else {
                        // 保留可能是半个闭合标记的尾巴，其余思考内容直接丢弃
                        int keep = Math.min(buffer.length(), CLOSE_ANGLE.length());
                        buffer.delete(0, buffer.length() - keep);
                        break;
                    }
                } else {
                    int angle = lower.indexOf(OPEN_ANGLE);
                    int bracket = lower.indexOf(OPEN_BRACKET);
                    if (angle >= 0 && (bracket < 0 || angle <= bracket)) {
                        int gt = lower.indexOf('>', angle);
                        if (gt < 0) {
                            // 开口标记尚未收全（如 "<thi"），先攒着
                            buffer.delete(0, angle);
                            break;
                        }
                        out.append(buffer, 0, angle);
                        buffer.delete(0, gt + 1);
                        inThink = true;
                    } else if (bracket >= 0) {
                        out.append(buffer, 0, bracket);
                        buffer.delete(0, bracket + OPEN_BRACKET.length());
                        inThink = true;
                    } else {
                        int keep = partialOpenSuffix(lower);
                        out.append(buffer, 0, buffer.length() - keep);
                        buffer.delete(0, buffer.length() - keep);
                        break;
                    }
                }
            }
            return out.length() == 0 ? null : out.toString();
        }

        String flush() {
            String rest = buffer.toString();
            buffer.setLength(0);
            if (inThink) {
                return null;
            }
            return rest.isEmpty() ? null : rest;
        }

        private int partialOpenSuffix(String lower) {
            int max = Math.min(lower.length(), OPEN_ANGLE.length() - 1);
            for (int len = max; len > 0; len--) {
                if (OPEN_ANGLE.startsWith(lower.substring(lower.length() - len))) {
                    return len;
                }
            }
            int maxBracket = Math.min(lower.length(), OPEN_BRACKET.length() - 1);
            for (int len = maxBracket; len > 0; len--) {
                if (OPEN_BRACKET.startsWith(lower.substring(lower.length() - len))) {
                    return len;
                }
            }
            return 0;
        }
    }
}
