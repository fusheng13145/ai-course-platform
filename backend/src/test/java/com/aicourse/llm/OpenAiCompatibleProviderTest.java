package com.aicourse.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OpenAiCompatibleProvider 单元测试（抽象类的匿名子类，覆盖 417 行缺口）。
 * 通过反射覆盖构造出的 restClient / streamWebClient 字段，避免真实 HTTP。
 * 覆盖：complete 成功 / 各失败分支 / 重试 / 中断 / 默认模型；stream 未配置 / 成功 /
 * 过滤 [DONE] 与空行 / 解析异常；completeJson 注入 system 提示的两种分支。
 */
@ExtendWith(MockitoExtension.class)
class OpenAiCompatibleProviderTest {

    private OpenAiCompatibleProvider provider;
    private OpenAiCompatibleProvider unconfiguredProvider;

    @Mock
    private RestClient.RequestBodyUriSpec uriSpec;
    @Mock
    private RestClient.RequestBodySpec bodySpec;
    @Mock
    private RestClient.ResponseSpec respSpec;

    @BeforeEach
    void setUp() {
        // 构造会真实创建 RestClient/WebClient（仅配置，不发请求），随后用 mock 覆盖字段
        provider = new OpenAiCompatibleProvider("test", "sk-key", "http://localhost/v1", "model-x", 5, 60, 2) {};
        unconfiguredProvider = new OpenAiCompatibleProvider("test", "", "http://localhost/v1", "", 5, 60, 0) {};
    }

    private RestClient mockRestClientReturning(Map<String, Object> resp) {
        RestClient rc = mock(RestClient.class);
        when(rc.post()).thenReturn(uriSpec);
        when(uriSpec.uri("/chat/completions")).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(respSpec);
        when(respSpec.body(eq(Map.class))).thenReturn(resp);
        return rc;
    }

    private RestClient mockRestClientThrowing() {
        RestClient rc = mock(RestClient.class);
        when(rc.post()).thenReturn(uriSpec);
        when(uriSpec.uri("/chat/completions")).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(respSpec);
        when(respSpec.body(eq(Map.class))).thenThrow(new RuntimeException("boom"));
        return rc;
    }

    private WebClient mockStreamWebClient(Flux<String> flux) {
        WebClient wc = mock(WebClient.class);
        WebClient.RequestBodyUriSpec wUri = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec wBody = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec wHeaders = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec wResp = mock(WebClient.ResponseSpec.class);
        when(wc.post()).thenReturn(wUri);
        when(wUri.uri("/chat/completions")).thenReturn(wBody);
        when(wBody.bodyValue(any(Object.class))).thenReturn(wHeaders);
        when(wHeaders.accept(any(org.springframework.http.MediaType[].class))).thenReturn(wHeaders);
        when(wHeaders.retrieve()).thenReturn(wResp);
        when(wResp.bodyToFlux(eq(String.class))).thenReturn(flux);
        return wc;
    }

    // ===================== name / isConfigured / defaultModel =====================

    @Test
    void name_and_isConfigured() {
        assertEquals("test", provider.name());
        assertEquals(true, provider.isConfigured());
        assertEquals(false, unconfiguredProvider.isConfigured());
    }

    @Test
    void defaultModelUsedWhenModelBlank() {
        // unconfiguredProvider 构造时 model 为 "" -> 使用 defaultModel()
        assertNotNull(unconfiguredProvider);
        assertEquals("test", unconfiguredProvider.name());
    }

    // ===================== complete 成功与各失败分支 =====================

    @Test
    void complete_success() {
        Map<String, Object> choice = Map.of("message", Map.of("content", "hello"));
        Map<String, Object> resp = Map.of("choices", List.of(choice));
        ReflectionTestUtils.setField(provider, "restClient", mockRestClientReturning(resp));

        assertEquals("hello", provider.complete(List.of(Map.of("role", "user", "content", "hi")), 0.5));
    }

    @Test
    void complete_respNull() {
        ReflectionTestUtils.setField(provider, "restClient", mockRestClientReturning(null));
        assertNull(provider.complete(List.of(Map.of("role", "user", "content", "hi")), null));
    }

    @Test
    void complete_noChoicesKey() {
        ReflectionTestUtils.setField(provider, "restClient", mockRestClientReturning(Map.of("foo", "bar")));
        assertNull(provider.complete(List.of(Map.of("role", "user", "content", "hi")), null));
    }

    @Test
    void complete_emptyChoices() {
        ReflectionTestUtils.setField(provider, "restClient", mockRestClientReturning(Map.of("choices", List.of())));
        assertNull(provider.complete(List.of(Map.of("role", "user", "content", "hi")), null));
    }

    @Test
    void complete_messageOrContentBlank() {
        // message 为 null
        ReflectionTestUtils.setField(provider, "restClient",
                mockRestClientReturning(Map.of("choices", List.of(Map.of()))));
        assertNull(provider.complete(List.of(Map.of("role", "user", "content", "hi")), null));
        // content 为空串
        ReflectionTestUtils.setField(provider, "restClient",
                mockRestClientReturning(Map.of("choices", List.of(Map.of("message", Map.of("content", "  "))))));
        assertNull(provider.complete(List.of(Map.of("role", "user", "content", "hi")), null));
    }

    @Test
    void complete_unconfiguredReturnsNull() {
        assertNull(unconfiguredProvider.complete(List.of(Map.of("role", "user", "content", "hi")), null));
    }

    // ===================== 重试 / 中断 =====================

    @Test
    void complete_retriesThenSucceeds() {
        Map<String, Object> choice = Map.of("message", Map.of("content", "ok"));
        Map<String, Object> resp = Map.of("choices", List.of(choice));
        RestClient rc = mock(RestClient.class);
        RestClient.ResponseSpec rsp = mock(RestClient.ResponseSpec.class);
        when(rc.post()).thenReturn(uriSpec);
        when(uriSpec.uri("/chat/completions")).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(rsp);
        // 第一次抛，第二次成功
        when(rsp.body(eq(Map.class))).thenThrow(new RuntimeException("x")).thenReturn(resp);
        ReflectionTestUtils.setField(provider, "restClient", rc);

        assertEquals("ok", provider.complete(List.of(Map.of("role", "user", "content", "hi")), null));
    }

    @Test
    void complete_maxRetriesExhausted() {
        ReflectionTestUtils.setField(provider, "restClient", mockRestClientThrowing());
        assertNull(provider.complete(List.of(Map.of("role", "user", "content", "hi")), null));
    }

    @Test
    void complete_interruptedDuringRetry() {
        ReflectionTestUtils.setField(provider, "restClient", mockRestClientThrowing());
        // 中断当前线程，使重试中的 Thread.sleep 抛 InterruptedException -> break
        Thread.currentThread().interrupt();
        assertNull(provider.complete(List.of(Map.of("role", "user", "content", "hi")), null));
        // 清除中断标记，避免影响其它测试
        Thread.interrupted();
    }

    // ===================== completeJson 注入分支 =====================

    @Test
    void completeJson_withExistingSystemMessage() {
        Map<String, Object> choice = Map.of("message", Map.of("content", "{\"a\":1}"));
        Map<String, Object> resp = Map.of("choices", List.of(choice));
        ReflectionTestUtils.setField(provider, "restClient", mockRestClientReturning(resp));

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "be helpful"),
                Map.of("role", "user", "content", "q"));
        assertEquals("{\"a\":1}", provider.completeJson(messages, 0.3, "{\"schema\":\"x\"}"));
    }

    @Test
    void completeJson_withoutSystemMessage() {
        Map<String, Object> choice = Map.of("message", Map.of("content", "{\"b\":2}"));
        Map<String, Object> resp = Map.of("choices", List.of(choice));
        ReflectionTestUtils.setField(provider, "restClient", mockRestClientReturning(resp));

        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "q"));
        assertEquals("{\"b\":2}", provider.completeJson(messages, 0.3, "{\"schema\":\"y\"}"));
    }

    // ===================== stream =====================

    @Test
    void stream_unconfiguredReturnsEmpty() {
        assertEquals(0L, unconfiguredProvider.stream(List.of(Map.of("role", "user", "content", "hi")), null).count().block());
    }

    @Test
    void stream_successExtractsDeltas() {
        Flux<String> flux = Flux.just(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\" World\"}}]}",
                "[DONE]",
                "not-a-data-line");
        ReflectionTestUtils.setField(provider, "streamWebClient", mockStreamWebClient(flux));

        List<String> collected = provider.stream(List.of(Map.of("role", "user", "content", "hi")), null)
                .collectList().block();
        assertEquals(List.of("Hello", " World"), collected);
    }
}
