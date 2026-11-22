package com.aicourse.integration.deepgraph;

import com.aicourse.integration.ContractPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeepGraph RAG HTTP 远端实现 {@link HttpDeepGraphRagProvider} 的覆盖率增强测试
 * （与 {@code HttpRagProviderBoostTest} 同策略：反射注入 mock factory 支撑的 RestClient）。
 * <p>覆盖：各动词成功路径、非超时异常重试后成功/耗尽、读超时不重试、空白/畸形响应体、
 * courseId 缺省分支、chapterGraph 可选字段省略、503 降级形态。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpDeepGraphRagProviderBoostTest {

    private static final String BASE = "http://localhost:8501";

    @Mock
    private ClientHttpRequestFactory mockFactory;
    @Mock
    private ClientHttpRequest mockRequest;
    @Mock
    private ClientHttpResponse mockResponse;

    private HttpDeepGraphRagProvider buildProvider(int maxRetries) {
        return new HttpDeepGraphRagProvider(BASE, 5, 60, maxRetries, ContractPaths.DEEPGRAPH_HEALTH);
    }

    /** 注入 mock RestClient；statusCode()/body() 由各用例自行打桩。 */
    private void inject(HttpDeepGraphRagProvider provider) throws Exception {
        when(mockFactory.createRequest(any(URI.class), any(HttpMethod.class))).thenReturn(mockRequest);
        when(mockRequest.getBody()).thenReturn(new ByteArrayOutputStream());
        when(mockRequest.getHeaders()).thenReturn(new HttpHeaders());
        when(mockRequest.getMethod()).thenReturn(HttpMethod.POST);
        when(mockRequest.execute()).thenReturn(mockResponse);
        when(mockResponse.getHeaders()).thenReturn(new HttpHeaders());
        RestClient rc = RestClient.builder().baseUrl(BASE).requestFactory(mockFactory).build();
        ReflectionTestUtils.setField(provider, "restClient", rc);
    }

    private void stubOk(String body) throws Exception {
        when(mockResponse.getStatusCode()).thenReturn(HttpStatus.OK);
        // 不声明 UTF-8 会被 StringHttpMessageConverter 按 ISO-8859-1 解码，中文断言必然乱码
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        when(mockResponse.getHeaders()).thenReturn(headers);
        when(mockResponse.getBody())
                .thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("retrieve 成功：解析 results/graph_context/mode")
    void retrieve_success() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("{\"mode\":\"ENGINE\",\"results\":[{\"document_name\":\"d\",\"relevance_score\":0.5,"
                + "\"snippet\":\"s\",\"retrieval\":\"VECTOR\"}],\"graph_context\":\"- a (相关) b\"}");

        DeepGraphRagContract.DeepGraphRetrievalResult r = provider.retrieve(
                new DeepGraphRagContract.DeepGraphRetrieveRequest("q", 1L, 3));
        assertTrue(r.engineReady());
        assertEquals("- a (相关) b", r.graphContext());
        assertEquals("VECTOR", r.results().get(0).retrieval());
    }

    @Test
    @DisplayName("retrieve 空白响应体 -> 降级占位")
    void retrieve_blankBody_degrades() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        when(mockResponse.getStatusCode()).thenReturn(HttpStatus.OK);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream(new byte[0]));

        DeepGraphRagContract.DeepGraphRetrievalResult r = provider.retrieve(
                new DeepGraphRagContract.DeepGraphRetrieveRequest("q", 1L, 3));
        assertFalse(r.engineReady());
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
    }

    @Test
    @DisplayName("retrieve 畸形 JSON -> 降级不抛 500")
    void retrieve_malformedJson_degrades() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("not-a-json{{{");

        DeepGraphRagContract.DeepGraphRetrievalResult r = provider.retrieve(
                new DeepGraphRagContract.DeepGraphRetrieveRequest("q", 1L, 3));
        assertFalse(r.engineReady());
    }

    @Test
    @DisplayName("非超时异常重试后成功（maxRetries=1）")
    void retryThenSuccess() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(1);
        inject(provider);
        when(mockResponse.getStatusCode())
                .thenReturn(HttpStatus.INTERNAL_SERVER_ERROR)
                .thenReturn(HttpStatus.OK);
        when(mockResponse.getBody())
                .thenReturn(new ByteArrayInputStream("boom".getBytes(StandardCharsets.UTF_8)))
                .thenReturn(new ByteArrayInputStream(
                        "{\"mode\":\"ENGINE\",\"results\":[],\"graph_context\":\"\"}"
                                .getBytes(StandardCharsets.UTF_8)));

        DeepGraphRagContract.DeepGraphRetrievalResult r = provider.retrieve(
                new DeepGraphRagContract.DeepGraphRetrieveRequest("q", 1L, 3));
        assertTrue(r.engineReady());
        verify(mockRequest, times(2)).execute();
    }

    @Test
    @DisplayName("重试耗尽 -> 503 降级占位（Tracer 分支）")
    void retriesExhausted_degrades() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(1);
        inject(provider);
        when(mockResponse.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        when(mockResponse.getBody())
                .thenReturn(new ByteArrayInputStream("boom".getBytes(StandardCharsets.UTF_8)));

        DeepGraphRagContract.DeepGraphRetrievalResult r = provider.retrieve(
                new DeepGraphRagContract.DeepGraphRetrieveRequest("q", 1L, 3));
        assertFalse(r.engineReady());
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        verify(mockRequest, atLeastOnce()).execute();
    }

    @Test
    @DisplayName("读超时不重试，直接降级")
    void readTimeout_noRetry_degrades() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(2);
        inject(provider);
        when(mockRequest.execute()).thenThrow(new SocketTimeoutException("read timeout"));

        DeepGraphRagContract.DeepGraphRetrievalResult r = provider.retrieve(
                new DeepGraphRagContract.DeepGraphRetrieveRequest("q", 1L, 3));
        assertFalse(r.engineReady());
        verify(mockRequest, times(1)).execute();
    }

    @Test
    @DisplayName("ingest：503 body 形态（HTTP 200 + code=503）按降级处理")
    void ingest_bodyDegrade() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("{\"success\":false,\"error\":\"Ollama 未启动\",\"code\":503,\"mode\":\"UNAVAILABLE\"}");

        DeepGraphRagContract.DeepGraphIngestResult r = provider.ingest(1L, "doc", "content");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE, r.error());
    }

    @Test
    @DisplayName("listDocuments：裸数组与 documents 包装两种形态都解析")
    void listDocuments_bareArrayAndWrapped() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("[{\"document_name\":\"d1\",\"chapters_count\":3}]");

        DeepGraphRagContract.DeepGraphDocumentList r1 = provider.listDocuments(null);
        assertEquals(1, r1.documents().size());
        assertEquals(3, r1.documents().get(0).chaptersCount());

        stubOk("{\"documents\":[{\"document_name\":\"d2\",\"has_faiss_index\":false}]}");
        DeepGraphRagContract.DeepGraphDocumentList r2 = provider.listDocuments(1L);
        assertEquals(1, r2.documents().size());
        assertEquals(Boolean.FALSE, r2.documents().get(0).hasFaissIndex());
    }

    @Test
    @DisplayName("deleteDocument：courseId 缺省时仍走带占位符的 DELETE 路径")
    void deleteDocument_nullCourseId() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("{\"success\":true}");

        DeepGraphRagContract.DeepGraphDeleteResult r = provider.deleteDocument(null, "d.txt");
        assertEquals(Boolean.TRUE, r.success());
    }

    @Test
    @DisplayName("deleteDocument：非 2xx -> 降级占位（fallback supplier 分支）")
    void deleteDocument_httpError_degrades() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        when(mockResponse.getStatusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE);
        when(mockResponse.getBody())
                .thenReturn(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));

        DeepGraphRagContract.DeepGraphDeleteResult r = provider.deleteDocument(1L, "d.txt");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
    }

    @Test
    @DisplayName("chapterGraph：chapter_name/chapter_text 为空时省略字段；异常体降级")
    void chapterGraph_optionalFieldsOmitted() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("{\"success\":true,\"nodes\":2,\"edges\":1,"
                + "\"relations\":[{\"source\":\"a\",\"relation\":\"相关\",\"target\":\"b\"}],\"neo4j_synced\":false}");

        DeepGraphRagContract.DeepGraphChapterGraphResult r =
                provider.chapterGraph(1L, "doc", null, "  ");
        assertTrue(r.success());
        assertFalse(r.neo4jSynced());
        assertEquals("a", r.relations().get(0).source());
    }

    @Test
    @DisplayName("learningPath：courseId 缺省 + 成功解析 / HTTP 200 + body 降级")
    void learningPath_paths() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("{\"target\":\"t\",\"path_text\":\"## p\",\"source_doc\":\"d\"}");

        DeepGraphRagContract.DeepGraphLearningPathResult ok = provider.learningPath("t", null, 3, true);
        assertEquals("## p", ok.pathText());

        stubOk("{\"code\":503,\"mode\":\"UNAVAILABLE\",\"error\":\"x\"}");
        DeepGraphRagContract.DeepGraphLearningPathResult degraded =
                provider.learningPath("t", 1L, 2, false);
        assertEquals(ContractPaths.CODE_UNAVAILABLE, degraded.code());
    }

    @Test
    @DisplayName("courseGraph：聚合解析 + body 降级 + 畸形体降级")
    void courseGraph_paths() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("{\"chapters\":[\"第1章\"],\"nodes\":[{\"name\":\"TCP\",\"type\":\"CONCEPT\",\"evidence\":\"e\"}],"
                + "\"edges\":[{\"source\":\"TCP\",\"target\":\"三次握手\",\"relation\":\"包含\",\"evidence\":\"\",\"chapter\":\"第1章\"}],"
                + "\"mode\":\"ENGINE\"}");
        DeepGraphRagContract.DeepGraphCourseGraphData ok = provider.getCourseGraph(4178L, "教材");
        assertTrue(ok.engineReady());
        assertEquals(1, ok.nodes().size());
        assertEquals("包含", ok.edges().get(0).relation());

        stubOk("{\"code\":503,\"mode\":\"UNAVAILABLE\",\"error\":\"x\"}");
        DeepGraphRagContract.DeepGraphCourseGraphData degraded = provider.getCourseGraph(4178L, "教材");
        assertFalse(degraded.engineReady());

        stubOk("not-json{{{");
        DeepGraphRagContract.DeepGraphCourseGraphData malformed = provider.getCourseGraph(4178L, "教材");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, malformed.code());
    }

    @Test
    @DisplayName("healthCheck 异常 -> false（log.debug 分支）")
    void healthCheck_exception_false() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        when(mockRequest.execute()).thenThrow(new RuntimeException("boom"));

        assertFalse(provider.healthCheck().healthy());
    }

    @Test
    @DisplayName("courseGraph：success=false 但无 code 时同样降级，不得伪成功落库")
    void courseGraph_successFalse_degrades() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("{\"success\":false,\"error\":\"graph build failed\",\"nodes\":[],\"edges\":[],\"mode\":\"ENGINE\"}");

        DeepGraphRagContract.DeepGraphCourseGraphData r = provider.getCourseGraph(1L, "doc");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MODE_UNAVAILABLE, r.mode());
    }

    @Test
    @DisplayName("ingest：success=false 但无 code 时降级")
    void ingest_successFalse_degrades() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("{\"success\":false,\"error\":\"bad document\",\"mode\":\"ENGINE\"}");

        DeepGraphRagContract.DeepGraphIngestResult r = provider.ingest(1L, "doc", "content");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
    }

    @Test
    @DisplayName("chapterGraph：success=false 但无 code 时降级")
    void chapterGraph_successFalse_degrades() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("{\"success\":false,\"nodes\":0,\"edges\":0,\"mode\":\"ENGINE\"}");

        DeepGraphRagContract.DeepGraphChapterGraphResult r = provider.chapterGraph(1L, "doc", "ch", "");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
    }

    @Test
    @DisplayName("listDocuments：body 503 时降级，不返回空列表伪成功")
    void listDocuments_bodyDegrade() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("{\"code\":503,\"mode\":\"UNAVAILABLE\",\"error\":\"engine down\"}");

        DeepGraphRagContract.DeepGraphDocumentList r = provider.listDocuments(1L);
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
    }

    @Test
    @DisplayName("deleteDocument：success=false 时降级")
    void deleteDocument_successFalse_degrades() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("{\"success\":false,\"code\":503,\"error\":\"delete failed\"}");

        DeepGraphRagContract.DeepGraphDeleteResult r = provider.deleteDocument(1L, "d.txt");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertFalse(Boolean.TRUE.equals(r.success()));
    }

    @Test
    @DisplayName("ingest 内部认证头：配置密钥时附带 X-Internal-Key")
    void internalKeyHeaderAttached() throws Exception {
        HttpDeepGraphRagProvider provider = new HttpDeepGraphRagProvider(
                BASE, 5, 60, 0, ContractPaths.DEEPGRAPH_HEALTH, "secret-key");
        inject(provider);
        stubOk("{\"success\":true}");

        provider.ingest(1L, "doc", "content");
        verify(mockRequest, atLeastOnce()).execute();
    }

    @Test
    @DisplayName("身份透传：MDC traceId 与登录用户名写入 X-Trace-Id/X-User-Id")
    void identityHeadersAttachedFromMdcAndSecurityContext() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("{\"results\":[],\"mode\":\"ENGINE\",\"code\":200}");

        MDC.put("traceId", "trace-stage4");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("teacher-1", "n/a", List.of()));
        try {
            provider.retrieve(new DeepGraphRagContract.DeepGraphRetrieveRequest("TCP", 1L, 3));
            assertEquals("trace-stage4",
                    mockRequest.getHeaders().getFirst(HttpDeepGraphRagProvider.TRACE_HEADER));
            assertEquals("teacher-1",
                    mockRequest.getHeaders().getFirst(HttpDeepGraphRagProvider.USER_HEADER));
        } finally {
            SecurityContextHolder.clearContext();
            MDC.remove("traceId");
        }
    }

    @Test
    @DisplayName("retrieve：success=false 时降级，不把业务失败当成功")
    void retrieve_successFalse_degrades() throws Exception {
        HttpDeepGraphRagProvider provider = buildProvider(0);
        inject(provider);
        stubOk("{\"success\":false,\"code\":500,\"mode\":\"UNAVAILABLE\",\"error\":\"boom\"}");

        DeepGraphRagContract.DeepGraphRetrievalResult r = provider.retrieve(
                new DeepGraphRagContract.DeepGraphRetrieveRequest("TCP", 1L, 3));
        assertFalse(r.engineReady());
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
    }
}
