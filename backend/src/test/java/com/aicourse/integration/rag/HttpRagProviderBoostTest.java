package com.aicourse.integration.rag;

import com.aicourse.integration.ContractPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RAG HTTP 远端实现 {@link HttpRagProvider} 的覆盖率增强测试（最大化指令覆盖）。
 * <p>
 * 策略：构造 {@link HttpRagProvider} 后，通过反射将内部 final 的 {@code restClient} 替换为
 * 由 mock {@link ClientHttpRequestFactory} 支撑的「真实 RestClient」。这样所有 HTTP 动词
 * （GET/POST/PUT/DELETE）共用同一套桩，避免对 RestClient 流式泛型 API 逐个深度打桩。
 * <p>
 * 覆盖点（原 {@code RAGServiceImplBoostTest} 在门面重构后已无意义——{@code restClient} 下沉至
 * 本 Provider 层，故等价迁移到此）：
 * 1) 成功路径（executeWithRetry -> 正常返回，各 HTTP 动词）；
 * 2) 重试逻辑：非 SocketTimeout 异常按 maxRetries 重试；SocketTimeout 不重试；
 * 3) 重试耗尽 -> 返回 503 降级占位（Tracer.trace 分支）；
 * 4) healthCheck 成功/异常（异常分支返回 false）；
 * 5) listDocuments / ask / retrieve 中 courseId 为 null 与非 null、topK 边界分支。
 * <p>
 * 注：{@code enabled=false} 的「全部方法返回 503」分支在 {@link DisabledRagProviderTest} 中覆盖，
 * 此处仅针对「启用远端」路径。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpRagProviderBoostTest {

    private static final String HEALTH = "/api/v1/rag/health";
    private static final String BASE = "http://localhost:8002";

    private ClientHttpRequestFactory mockFactory;
    private ClientHttpRequest mockRequest;
    private ClientHttpResponse mockResponse;

    private HttpRagProvider buildProvider(int maxRetries) {
        return new HttpRagProvider(BASE, 5, 60, maxRetries, HEALTH, 5);
    }

    private void injectRestClient(HttpRagProvider provider, String body) throws Exception {
        mockFactory = mock(ClientHttpRequestFactory.class);
        mockRequest = mock(ClientHttpRequest.class);
        mockResponse = mock(ClientHttpResponse.class);
        when(mockFactory.createRequest(any(URI.class), any(HttpMethod.class))).thenReturn(mockRequest);
        when(mockRequest.getBody()).thenReturn(new ByteArrayOutputStream());
        when(mockRequest.getHeaders()).thenReturn(new HttpHeaders());
        when(mockRequest.getMethod()).thenReturn(HttpMethod.POST);
        when(mockRequest.execute()).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(HttpStatus.OK);
        when(mockResponse.getHeaders()).thenReturn(new HttpHeaders());
        when(mockResponse.getBody())
                .thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

        RestClient rc = RestClient.builder().baseUrl(BASE).requestFactory(mockFactory).build();
        ReflectionTestUtils.setField(provider, "restClient", rc);
    }

    private void injectRestClientThrowing(HttpRagProvider provider, Throwable ex) throws Exception {
        mockFactory = mock(ClientHttpRequestFactory.class);
        mockRequest = mock(ClientHttpRequest.class);
        when(mockFactory.createRequest(any(URI.class), any(HttpMethod.class))).thenReturn(mockRequest);
        when(mockRequest.getBody()).thenReturn(new ByteArrayOutputStream());
        when(mockRequest.getHeaders()).thenReturn(new HttpHeaders());
        when(mockRequest.getMethod()).thenReturn(HttpMethod.POST);
        if (ex instanceof RuntimeException re) {
            when(mockRequest.execute()).thenThrow(re);
        } else {
            when(mockRequest.execute()).thenThrow((IOException) ex);
        }
        RestClient rc = RestClient.builder().baseUrl(BASE).requestFactory(mockFactory).build();
        ReflectionTestUtils.setField(provider, "restClient", rc);
    }

    private void assert503(RagContract.RagQueryResult r) {
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_RAG_UNAVAILABLE, r.error());
    }

    // ===================== 成功路径 =====================

    @Test
    @DisplayName("healthCheck 成功返回 true（GET）")
    void healthCheck_ok() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClient(p, "{}");
        assertTrue(p.healthCheck().healthy());
        verify(mockFactory).createRequest(any(URI.class), eq(HttpMethod.GET));
    }

    @Test
    @DisplayName("ask 成功解析响应体（courseId 非空 + topK>0，POST）")
    void ask_ok() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClient(p, "{\"answer\":\"hello\"}");
        RagContract.RagQueryResult r = p.ask(new RagContract.RagAskRequest("问题", 7L, 4));
        assertEquals("hello", r.answer());
        assertEquals(ContractPaths.MODE_ENGINE, r.mode());
        verify(mockFactory).createRequest(any(URI.class), eq(HttpMethod.POST));
    }

    @Test
    @DisplayName("addDocument 成功解析响应体")
    void addDocument_ok() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClient(p, "{\"id\":1,\"document_name\":\"d\",\"course_id\":1,\"content\":\"c\"}");
        RagContract.RagDocument r = p.addDocument(1L, "doc", "c");
        assertEquals(1L, r.id());
        assertEquals(ContractPaths.MODE_ENGINE, r.mode());
    }

    @Test
    @DisplayName("deleteDocument 成功（DELETE）")
    void deleteDocument_ok() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClient(p, "{\"deleted\":true}");
        RagContract.RagDocument r = p.deleteDocument(9L);
        assertEquals(9L, r.id());
        verify(mockFactory).createRequest(any(URI.class), eq(HttpMethod.DELETE));
    }

    @Test
    @DisplayName("listDocuments 成功（courseId 非空分支）")
    void listDocuments_withCourse() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClient(p, "{\"documents\":[{\"id\":1,\"document_name\":\"d\"}]}");
        RagContract.RagDocumentList r = p.listDocuments(5L);
        assertEquals(1, r.documents().size());
        assertNull(r.code());
    }

    @Test
    @DisplayName("listDocuments 成功（courseId 为 null 分支）")
    void listDocuments_nullCourse() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClient(p, "{\"documents\":[{\"id\":1}]}");
        RagContract.RagDocumentList r = p.listDocuments(null);
        assertEquals(1, r.documents().size());
    }

    @Test
    @DisplayName("updateDocument 成功（PUT）")
    void updateDocument_ok() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClient(p, "{\"id\":9}");
        RagContract.RagDocument r = p.updateDocument(9L, "x");
        assertEquals(9L, r.id());
        verify(mockFactory).createRequest(any(URI.class), eq(HttpMethod.PUT));
    }

    @Test
    @DisplayName("buildIndex 成功")
    void buildIndex_ok() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClient(p, "{\"success\":true,\"indexed_chunks\":3}");
        RagContract.IndexResult r = p.buildIndex(1L);
        assertEquals(Boolean.TRUE, r.success());
        assertEquals(3, r.indexedChunks());
    }

    @Test
    @DisplayName("clearIndex 成功")
    void clearIndex_ok() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClient(p, "{\"success\":true,\"indexed_chunks\":0}");
        RagContract.IndexResult r = p.clearIndex(1L);
        assertEquals(Boolean.TRUE, r.success());
    }

    @Test
    @DisplayName("retrieve 成功（courseId 非空 + topK>0）")
    void retrieve_ok() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClient(p,
                "{\"results\":[{\"document_id\":1,\"document_name\":\"d\",\"relevance_score\":0.8,\"snippet\":\"s\"}]}");
        RagContract.RagRetrievalResult r = p.retrieve(new RagContract.RagRetrieveRequest("q", 3L, 5));
        assertEquals(1, r.results().size());
        assertEquals(0.8, r.results().get(0).relevanceScore(), 1e-9);
    }

    @Test
    @DisplayName("ask 分支覆盖（courseId 为 null + topK<=0 走 defaultTopK）")
    void ask_defaultTopK() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClient(p, "{\"answer\":\"d\"}");
        RagContract.RagQueryResult r = p.ask(new RagContract.RagAskRequest("q", null, 0));
        assertEquals("d", r.answer());
    }

    @Test
    @DisplayName("retrieve 分支覆盖（courseId 为 null + topK<=0 走 defaultTopK）")
    void retrieve_defaultTopK() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClient(p, "{\"results\":[]}");
        RagContract.RagRetrievalResult r = p.retrieve(new RagContract.RagRetrieveRequest("q", null, -1));
        assertNotNull(r.results());
    }

    // ===================== 失败/重试/降级 =====================

    @Test
    @DisplayName("SocketTimeoutException 不重试，直接返回 503")
    void ask_socketTimeout_noRetry() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClientThrowing(p, new SocketTimeoutException("read timed out"));
        assert503(p.ask(new RagContract.RagAskRequest("q", 1L, 3)));
        verify(mockRequest, times(1)).execute();
    }

    @Test
    @DisplayName("非 SocketTimeout 异常按 maxRetries 重试后返回 503")
    void ask_ioException_retryThen503() throws Exception {
        HttpRagProvider p = buildProvider(1); // maxRetries=1 -> 共 2 次尝试
        injectRestClientThrowing(p, new IOException("connection refused"));
        assert503(p.ask(new RagContract.RagAskRequest("q", 1L, 3)));
        verify(mockRequest, times(2)).execute();
    }

    @Test
    @DisplayName("所有重试耗尽（maxRetries=0）返回 503")
    void ask_ioException_noRetry_503() throws Exception {
        HttpRagProvider p = buildProvider(0);
        injectRestClientThrowing(p, new IOException("boom"));
        assert503(p.ask(new RagContract.RagAskRequest("q", 1L, 3)));
        verify(mockRequest, times(1)).execute();
    }

    @Test
    @DisplayName("healthCheck 抛异常返回 false（catch 分支）")
    void healthCheck_exception() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClientThrowing(p, new IOException("unreachable"));
        assertFalse(p.healthCheck().healthy());
    }

    @Test
    @DisplayName("addDocument 失败重试后返回 503")
    void addDocument_fail_503() throws Exception {
        HttpRagProvider p = buildProvider(2);
        injectRestClientThrowing(p, new IOException("down"));
        RagContract.RagDocument r = p.addDocument(1L, "d", "c");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
    }

    @Test
    @DisplayName("retrieve 失败重试后返回 503")
    void retrieve_fail_503() throws Exception {
        HttpRagProvider p = buildProvider(1);
        injectRestClientThrowing(p, new IOException("down"));
        RagContract.RagRetrievalResult r = p.retrieve(new RagContract.RagRetrieveRequest("q", 1L, 3));
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
    }
}
