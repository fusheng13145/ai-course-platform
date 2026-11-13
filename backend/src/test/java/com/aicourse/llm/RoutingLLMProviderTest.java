package com.aicourse.llm;

import com.aicourse.entity.SystemConfig;
import com.aicourse.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RoutingLLMProvider 单元测试。
 * 覆盖：resolve() 按 system_configs 动态选择底层供应商（默认 / openai / qwen / qwen2 /
 * dashscope / ollama / local / 读取异常回退）、name() / isConfigured() / complete / completeJson / stream 委托、
 * switchTo() 的空值 / 新建 / 更新分支、testConnection() 的已配置成功 / 未配置 / 异常分支。
 * 通过反射将各底层供应商的 restClient / streamWebClient 替换为 mock，避免真实 HTTP。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoutingLLMProviderTest {

    @Mock
    private SystemConfigService systemConfigService;

    private DeepSeekProvider deepSeek;
    private OpenAiProvider openAi;
    private QwenProvider qwen;
    private OllamaProvider ollama;

    private RoutingLLMProvider makeProvider(String cfgValue) {
        deepSeek = new DeepSeekProvider("sk", "http://x", "model", 5, 60, 2);
        openAi = new OpenAiProvider("sk", "http://x", "model", 5, 60, 2);
        qwen = new QwenProvider("sk", "http://x", "model", 5, 60, 2);
        ollama = new OllamaProvider("http://x", "deepseek-r1:8b", "", 5, 60, 2);
        stubRestClient(deepSeek, "ds-content");
        stubRestClient(openAi, "oi-content");
        stubRestClient(qwen, "qw-content");
        stubOllamaRestClient(ollama, "ol-content");
        stubStream(deepSeek, Flux.just("data: {\"choices\":[{\"delta\":{\"content\":\"ds-stream\"}}]}"));
        stubStream(openAi, Flux.just("data: {\"choices\":[{\"delta\":{\"content\":\"oi-stream\"}}]}"));
        stubStream(qwen, Flux.just("data: {\"choices\":[{\"delta\":{\"content\":\"qw-stream\"}}]}"));
        stubOllamaStream(ollama, Flux.just("{\"message\":{\"content\":\"ol-stream\"},\"done\":false}"));
        lenient().when(systemConfigService.getConfigValue("ai.provider")).thenReturn(cfgValue);
        return new RoutingLLMProvider(deepSeek, openAi, qwen, ollama, systemConfigService, "deepseek");
    }

    private RoutingLLMProvider makeProviderThrowing() {
        deepSeek = new DeepSeekProvider("sk", "http://x", "model", 5, 60, 2);
        openAi = new OpenAiProvider("sk", "http://x", "model", 5, 60, 2);
        qwen = new QwenProvider("sk", "http://x", "model", 5, 60, 2);
        ollama = new OllamaProvider("http://x", "deepseek-r1:8b", "", 5, 60, 2);
        stubRestClient(deepSeek, "ds-content");
        stubRestClient(openAi, "oi-content");
        stubRestClient(qwen, "qw-content");
        stubOllamaRestClient(ollama, "ol-content");
        lenient().when(systemConfigService.getConfigValue(any())).thenThrow(new RuntimeException("db down"));
        return new RoutingLLMProvider(deepSeek, openAi, qwen, ollama, systemConfigService, "deepseek");
    }

    private void stubOllamaRestClient(OllamaProvider p, String content) {
        Map<String, Object> resp = Map.of("message", Map.of("role", "assistant", "content", content), "done", true);
        RestClient rc = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uri = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec body = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec rsp = mock(RestClient.ResponseSpec.class);
        when(rc.post()).thenReturn(uri);
        when(uri.uri("/api/chat")).thenReturn(body);
        when(body.body(any(Object.class))).thenReturn(body);
        when(body.retrieve()).thenReturn(rsp);
        when(rsp.body(eq(Map.class))).thenReturn(resp);
        ReflectionTestUtils.setField(p, "restClient", rc);
    }

    private void stubOllamaStream(OllamaProvider p, Flux<String> flux) {
        org.springframework.web.reactive.function.client.WebClient wc = mock(org.springframework.web.reactive.function.client.WebClient.class);
        org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec wUri = mock(org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec.class);
        org.springframework.web.reactive.function.client.WebClient.RequestBodySpec wBody = mock(org.springframework.web.reactive.function.client.WebClient.RequestBodySpec.class);
        org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec wHeaders = mock(org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec.class);
        org.springframework.web.reactive.function.client.WebClient.ResponseSpec wResp = mock(org.springframework.web.reactive.function.client.WebClient.ResponseSpec.class);
        when(wc.post()).thenReturn(wUri);
        when(wUri.uri("/api/chat")).thenReturn(wBody);
        when(wBody.bodyValue(any(Object.class))).thenReturn(wHeaders);
        when(wHeaders.accept(any(org.springframework.http.MediaType[].class))).thenReturn(wHeaders);
        when(wHeaders.retrieve()).thenReturn(wResp);
        when(wResp.bodyToFlux(eq(String.class))).thenReturn(flux);
        ReflectionTestUtils.setField(p, "streamWebClient", wc);
    }

    private void stubRestClient(OpenAiCompatibleProvider p, String content) {
        Map<String, Object> resp = Map.of("choices", List.of(Map.of("message", Map.of("content", content))));
        RestClient rc = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uri = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec body = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec rsp = mock(RestClient.ResponseSpec.class);
        when(rc.post()).thenReturn(uri);
        when(uri.uri("/chat/completions")).thenReturn(body);
        when(body.body(any(Object.class))).thenReturn(body);
        when(body.retrieve()).thenReturn(rsp);
        when(rsp.body(eq(Map.class))).thenReturn(resp);
        ReflectionTestUtils.setField(p, "restClient", rc);
    }

    private void stubStream(OpenAiCompatibleProvider p, Flux<String> flux) {
        org.springframework.web.reactive.function.client.WebClient wc = mock(org.springframework.web.reactive.function.client.WebClient.class);
        org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec wUri = mock(org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec.class);
        org.springframework.web.reactive.function.client.WebClient.RequestBodySpec wBody = mock(org.springframework.web.reactive.function.client.WebClient.RequestBodySpec.class);
        org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec wHeaders = mock(org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec.class);
        org.springframework.web.reactive.function.client.WebClient.ResponseSpec wResp = mock(org.springframework.web.reactive.function.client.WebClient.ResponseSpec.class);
        when(wc.post()).thenReturn(wUri);
        when(wUri.uri("/chat/completions")).thenReturn(wBody);
        when(wBody.bodyValue(any(Object.class))).thenReturn(wHeaders);
        when(wHeaders.accept(any(org.springframework.http.MediaType[].class))).thenReturn(wHeaders);
        when(wHeaders.retrieve()).thenReturn(wResp);
        when(wResp.bodyToFlux(eq(String.class))).thenReturn(flux);
        ReflectionTestUtils.setField(p, "streamWebClient", wc);
    }

    @Test
    void resolve_defaultToDeepSeek() {
        RoutingLLMProvider p = makeProvider(null);
        assertEquals("ds-content", p.complete(List.of(Map.of("role", "user", "content", "hi")), null));
        assertTrue(p.name().startsWith("router(deepseek)"));
    }

    @Test
    void resolve_openai() {
        RoutingLLMProvider p = makeProvider("openai");
        assertEquals("oi-content", p.complete(List.of(Map.of("role", "user", "content", "hi")), null));
        assertTrue(p.name().contains("openai"));
    }

    @Test
    void resolve_qwen_variants() {
        assertEquals("qw-content", makeProvider("qwen").complete(List.of(), null));
        assertEquals("qw-content", makeProvider("qwen2").complete(List.of(), null));
        assertEquals("qw-content", makeProvider("dashscope").complete(List.of(), null));
    }

    @Test
    void resolve_ollamaVariants() {
        assertEquals("ol-content", makeProvider("ollama").complete(List.of(), null));
        assertEquals("ol-content", makeProvider("local").complete(List.of(), null));
        assertTrue(makeProvider("ollama").name().contains("ollama"));
    }

    @Test
    void resolve_readErrorFallsBackToDefault() {
        RoutingLLMProvider p = makeProviderThrowing();
        assertEquals("ds-content", p.complete(List.of(), null));
    }

    @Test
    void isConfigured_true() {
        assertTrue(makeProvider(null).isConfigured());
    }

    @Test
    void completeJson_delegates() {
        RoutingLLMProvider p = makeProvider("openai");
        assertEquals("oi-content", p.completeJson(List.of(), null, "{}"));
    }

    @Test
    void stream_delegates() {
        RoutingLLMProvider p = makeProvider("qwen");
        List<String> collected = p.stream(List.of(), null).collectList().block();
        assertEquals(List.of("qw-stream"), collected);
    }

    @Test
    void switchTo_nullOrBlank_noInteraction() {
        RoutingLLMProvider p = makeProvider(null);
        p.switchTo(null);
        p.switchTo("");
        p.switchTo("   ");
        verify(systemConfigService, org.mockito.Mockito.never()).save(any());
        verify(systemConfigService, org.mockito.Mockito.never()).updateConfig(any(), any());
    }

    @Test
    void switchTo_existingNull_createsNewConfig() {
        RoutingLLMProvider p = makeProvider(null);
        when(systemConfigService.getConfigValue("ai.provider")).thenReturn(null);
        p.switchTo("OpenAI");
        verify(systemConfigService).save(any(SystemConfig.class));
    }

    @Test
    void switchTo_existingPresent_updatesConfig() {
        RoutingLLMProvider p = makeProvider("deepseek");
        when(systemConfigService.getConfigValue("ai.provider")).thenReturn("deepseek");
        p.switchTo("qwen");
        verify(systemConfigService).updateConfig("ai.provider", "qwen");
    }

    @Test
    void testConnection_configuredAndHealthy_returnsTrue() {
        RoutingLLMProvider p = makeProvider(null);
        assertTrue(p.testConnection());
    }

    @Test
    void testConnection_unconfigured_returnsFalse() {
        // 令默认底层供应商（deepseek）未配置
        deepSeek = new DeepSeekProvider("", "http://x", "model", 5, 60, 2);
        openAi = new OpenAiProvider("sk", "http://x", "model", 5, 60, 2);
        qwen = new QwenProvider("sk", "http://x", "model", 5, 60, 2);
        ollama = new OllamaProvider("http://x", "deepseek-r1:8b", "", 5, 60, 2);
        stubRestClient(deepSeek, "x");
        stubRestClient(openAi, "x");
        stubRestClient(qwen, "x");
        stubOllamaRestClient(ollama, "x");
        RoutingLLMProvider p = new RoutingLLMProvider(deepSeek, openAi, qwen, ollama, systemConfigService, "deepseek");
        assertFalse(p.testConnection());
    }

    @Test
    void testConnection_exception_returnsFalse() {
        RoutingLLMProvider p = makeProvider("openai");
        // openai 的 restClient 抛异常 -> complete 返回 null -> false
        org.springframework.web.client.RestClient rc = mock(org.springframework.web.client.RestClient.class);
        org.springframework.web.client.RestClient.RequestBodyUriSpec uri = mock(org.springframework.web.client.RestClient.RequestBodyUriSpec.class);
        org.springframework.web.client.RestClient.RequestBodySpec body = mock(org.springframework.web.client.RestClient.RequestBodySpec.class);
        org.springframework.web.client.RestClient.ResponseSpec rsp = mock(org.springframework.web.client.RestClient.ResponseSpec.class);
        when(rc.post()).thenReturn(uri);
        when(uri.uri("/chat/completions")).thenReturn(body);
        when(body.body(any(Object.class))).thenReturn(body);
        when(body.retrieve()).thenReturn(rsp);
        when(rsp.body(eq(Map.class))).thenThrow(new RuntimeException("down"));
        ReflectionTestUtils.setField(openAi, "restClient", rc);
        assertFalse(p.testConnection());
    }
}
