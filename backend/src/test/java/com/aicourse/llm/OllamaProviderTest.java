package com.aicourse.llm;

import com.aicourse.exception.LLMUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OllamaProvider 单元测试。
 * 覆盖：thinking 与 content 分离（字段式 + 内联标签式 + 跨 chunk 流式）、仅思考时按空响应失败、
 * 未配置返回 null、JSON Mode 请求体、404 模型不存在 / 连接失败 / 超时的可操作 503 文案。
 * 全程 mock RestClient 与 WebClient，不发真实 HTTP；真实链路由 OLLAMA_LIVE_TEST 开关的
 * {@link OllamaProviderLiveTest} 单独验证。
 */
class OllamaProviderTest {

    private static final String BASE_URL = "http://localhost:11434";
    private static final String MODEL = "deepseek-r1:8b";

    /** 持有 mocks 便于断言请求体。 */
    private record RestMock(RestClient client, RestClient.RequestBodySpec body, RestClient.ResponseSpec response) {
    }

    private RestMock restMock() {
        RestClient rc = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uri = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec body = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec rsp = mock(RestClient.ResponseSpec.class);
        when(rc.post()).thenReturn(uri);
        when(uri.uri("/api/chat")).thenReturn(body);
        when(body.body(any(Object.class))).thenReturn(body);
        when(body.retrieve()).thenReturn(rsp);
        return new RestMock(rc, body, rsp);
    }

    private OllamaProvider provider(RestMock mocks) {
        OllamaProvider p = new OllamaProvider(BASE_URL, MODEL, "", 5, 60, 0);
        ReflectionTestUtils.setField(p, "restClient", mocks.client());
        return p;
    }

    private OllamaProvider providerWithResponse(String content, String thinking) {
        RestMock mocks = restMock();
        Map<String, Object> message = new java.util.LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        if (thinking != null) {
            message.put("thinking", thinking);
        }
        when(mocks.response().body(eq(Map.class)))
                .thenReturn(Map.of("message", message, "done", true));
        return provider(mocks);
    }

    @Test
    void complete_returnsContentAndIgnoresThinking() {
        OllamaProvider p = providerWithResponse("答案：2", "先想一想……");
        assertEquals("答案：2", p.complete(List.of(Map.of("role", "user", "content", "1+1")), 0.7));
    }

    @Test
    void complete_stripsInlineThinkTags() {
        OllamaProvider p = providerWithResponse("<think>推导</think>最终答案：4", null);
        assertEquals("最终答案：4", p.complete(List.of(), null));

        OllamaProvider bracket = providerWithResponse("[THINK]内部推理[/THINK]答案 B", null);
        assertEquals("答案 B", bracket.complete(List.of(), null));

        OllamaProvider unclosed = providerWithResponse("正常开头<think>未闭合的推理", null);
        assertEquals("正常开头", unclosed.complete(List.of(), null));
    }

    @Test
    void complete_thinkingOnly_failsAsEmptyResponse() {
        OllamaProvider p = providerWithResponse("", "只在思考，没来得及输出答案");
        LLMUnavailableException e = assertThrows(LLMUnavailableException.class,
                () -> p.complete(List.of(), null));
        assertEquals(LLMUnavailableException.Kind.EMPTY_RESPONSE, e.kind());
        assertTrue(e.getMessage().contains("thinking="));
        assertFalse(e.getMessage().contains("只在思考"));
    }

    @Test
    void complete_unconfigured_returnsNull() {
        OllamaProvider blankUrl = new OllamaProvider("", MODEL, "", 5, 60, 0);
        assertFalse(blankUrl.isConfigured());
        assertNull(blankUrl.complete(List.of(), null));

        OllamaProvider blankModel = new OllamaProvider(BASE_URL, "  ", "", 5, 60, 0);
        assertNull(blankModel.complete(List.of(), null));
    }

    @Test
    void complete_modelNotFound_404_isActionable() {
        RestMock mocks = restMock();
        when(mocks.response().body(eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "model not found"));
        OllamaProvider p = provider(mocks);

        LLMUnavailableException e = assertThrows(LLMUnavailableException.class,
                () -> p.complete(List.of(), null));
        assertTrue(e.getMessage().contains("404"));
        assertTrue(e.getMessage().contains("ollama pull deepseek-r1:8b"));
    }

    @Test
    void complete_connectionRefused_isActionable() {
        RestMock mocks = restMock();
        when(mocks.response().body(eq(Map.class)))
                .thenThrow(new ResourceAccessException("I/O error", new ConnectException("Connection refused")));
        OllamaProvider p = provider(mocks);

        LLMUnavailableException e = assertThrows(LLMUnavailableException.class,
                () -> p.complete(List.of(), null));
        assertEquals(LLMUnavailableException.Kind.UPSTREAM_UNREACHABLE, e.kind());
        assertTrue(e.getMessage().contains("ollama serve"));
        assertTrue(e.getMessage().contains(BASE_URL));
    }

    @Test
    void complete_timeout_isActionable() {
        RestMock mocks = restMock();
        when(mocks.response().body(eq(Map.class)))
                .thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out")));
        OllamaProvider p = provider(mocks);

        LLMUnavailableException e = assertThrows(LLMUnavailableException.class,
                () -> p.complete(List.of(), null));
        assertTrue(e.getMessage().contains("超时"));
        assertTrue(e.getMessage().contains("AI_READ_TIMEOUT"));
    }

    @Test
    void completeJson_sendsFormatAndSchemaInstruction() {
        RestMock mocks = restMock();
        when(mocks.response().body(eq(Map.class))).thenReturn(
                Map.of("message", Map.of("content", "{\"ok\":true}"), "done", true));
        OllamaProvider p = provider(mocks);

        p.completeJson(List.of(Map.of("role", "user", "content", "出题")), 0.2, "{\"type\":\"object\"}");

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(mocks.body()).body(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) captor.getValue();
        assertEquals(MODEL, sent.get("model"));
        assertEquals(Boolean.FALSE, sent.get("stream"));
        assertEquals("json", sent.get("format"));
        // 思考开关为三态：未显式配置时不下发，避免对非思考模型报错
        assertFalse(sent.containsKey("think"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) sent.get("messages");
        assertTrue(messages.get(0).get("content").contains("JSON"));
        assertTrue(messages.get(0).get("content").contains("{\"type\":\"object\"}"));
        assertEquals("出题", messages.get(1).get("content"));
    }

    @Test
    void stream_parsesNdjsonAndSeparatesThinking() {
        OllamaProvider p = new OllamaProvider(BASE_URL, MODEL, "", 5, 60, 0);
        stubStream(p, Flux.just(
                "{\"message\":{\"role\":\"assistant\",\"thinking\":\"推理中\"},\"done\":false}",
                "{\"message\":{\"content\":\"答案\"},\"done\":false}",
                "{\"message\":{\"content\":\"<think>隐藏\"},\"done\":false}",
                "{\"message\":{\"content\":\"</think>尾巴\"},\"done\":false}",
                "{\"done\":true}"));

        List<String> collected = p.stream(List.of(), null).collectList().block();
        assertEquals(List.of("答案", "尾巴"), collected);
    }

    @Test
    void stream_unconfigured_empty() {
        OllamaProvider p = new OllamaProvider("", MODEL, "", 5, 60, 0);
        assertEquals(List.of(), p.stream(List.of(), null).collectList().block());
    }

    @Test
    void thinkTagFilter_handlesSplitOpenTag() {
        OllamaProvider.ThinkTagFilter f = new OllamaProvider.ThinkTagFilter();
        assertNull(f.feed("<thi"));
        assertNull(f.feed("nk>推理"));
        assertEquals("答案", f.feed("</think>答案"));
    }

    private void stubStream(OllamaProvider p, Flux<String> flux) {
        WebClient wc = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uri = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec body = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headers = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec response = mock(WebClient.ResponseSpec.class);
        when(wc.post()).thenReturn(uri);
        when(uri.uri("/api/chat")).thenReturn(body);
        when(body.bodyValue(any(Object.class))).thenReturn(headers);
        when(headers.accept(any(org.springframework.http.MediaType[].class))).thenReturn(headers);
        when(headers.retrieve()).thenReturn(response);
        when(response.bodyToFlux(eq(String.class))).thenReturn(flux);
        ReflectionTestUtils.setField(p, "streamWebClient", wc);
    }
}
