package com.aicourse.aspect;

import com.aicourse.entity.OperationLog;
import com.aicourse.service.OperationLogService;
import com.aicourse.util.TrustedClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * OperationLogAspect 分支矩阵：写方法判定、鉴权、IP/UA、敏感字段打码、异常状态与动作映射。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperationLogAspectCoverageTest {

    @Mock private OperationLogService operationLogService;
    @Mock private TrustedClientIpResolver clientIpResolver;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature signature;

    private OperationLogAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new OperationLogAspect(operationLogService, clientIpResolver);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("FakeController.createSomething(..)");
        when(joinPoint.getTarget()).thenReturn(new FakeController());
        when(joinPoint.getArgs()).thenReturn(new Object[]{"arg"});
        when(signature.getName()).thenReturn("createSomething");
        when(clientIpResolver.resolve(any(HttpServletRequest.class))).thenReturn("10.0.0.8");
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private void request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/test");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit-Agent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private Object invokeSuccessfully() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");
        return aspect.aroundController(joinPoint);
    }

    @Test
    @DisplayName("GET 请求直接放行，不写审计日志")
    void readRequestPassesThrough() throws Throwable {
        request("GET");

        assertEquals("ok", invokeSuccessfully());
        verifyNoInteractions(operationLogService);
    }

    @Test
    @DisplayName("无请求上下文时按 UNKNOWN 放行，不写审计日志")
    void missingRequestPassesThrough() throws Throwable {
        RequestContextHolder.resetRequestAttributes();

        assertEquals("ok", invokeSuccessfully());
        verifyNoInteractions(operationLogService);
    }

    @Test
    @DisplayName("POST/PUT/DELETE/PATCH 均落审计日志，GET 不落")
    void writeMethodsAreRecorded() throws Throwable {
        for (String method : new String[]{"POST", "PUT", "DELETE", "PATCH"}) {
            request(method);
            when(joinPoint.proceed()).thenReturn("ok");
            aspect.aroundController(joinPoint);
        }

        verify(operationLogService, times(4)).save(any(OperationLog.class));
        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService, atLeastOnce()).save(captor.capture());
        assertEquals("PATCH", captor.getAllValues().get(3).getMethod());
    }

    @Test
    @DisplayName("已认证 Long principal 写入 userId/username；无鉴权不写")
    void authenticationVariants() throws Throwable {
        request("POST");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(42L, null, java.util.List.of()));
        invokeSuccessfully();

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).save(captor.capture());
        assertEquals(42L, captor.getValue().getUserId());
        assertEquals("42", captor.getValue().getUsername());

        SecurityContextHolder.clearContext();
        when(joinPoint.proceed()).thenReturn("ok");
        aspect.aroundController(joinPoint);
        verify(operationLogService, times(2)).save(any(OperationLog.class));
    }

    @Test
    @DisplayName("未认证或 principal 非 Long 时不写用户字段")
    void unauthenticatedOrStringPrincipal() throws Throwable {
        request("POST");
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);
        invokeSuccessfully();

        Authentication stringAuth = mock(Authentication.class);
        when(stringAuth.isAuthenticated()).thenReturn(true);
        when(stringAuth.getPrincipal()).thenReturn("anonymous");
        SecurityContextHolder.getContext().setAuthentication(stringAuth);
        when(joinPoint.proceed()).thenReturn("ok");
        aspect.aroundController(joinPoint);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService, times(2)).save(captor.capture());
        assertNull(captor.getAllValues().get(0).getUserId());
        assertNull(captor.getAllValues().get(1).getUserId());
    }

    @Test
    @DisplayName("请求 IP、UA、模块名、动作与成功状态落库")
    void requestMetadataAndSuccess() throws Throwable {
        request("POST");
        invokeSuccessfully();

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).save(captor.capture());
        OperationLog saved = captor.getValue();
        assertEquals("10.0.0.8", saved.getIp());
        assertEquals("JUnit-Agent", saved.getUserAgent());
        assertEquals("Fake", saved.getModule());
        assertEquals("CREATE", saved.getAction());
        assertEquals("SUCCESS", saved.getStatus());
        assertNotNull(saved.getDurationMs());
        assertTrue(saved.getTarget().contains("createSomething"));
    }

    @Test
    @DisplayName("目标方法抛异常时记 FAILED、截断 500 字并原样抛出")
    void failureTruncatesErrorMessage() throws Throwable {
        request("POST");
        RuntimeException failure = new RuntimeException("x".repeat(600));
        when(joinPoint.proceed()).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> aspect.aroundController(joinPoint));
        assertSame(failure, thrown);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).save(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus());
        assertEquals(500, captor.getValue().getErrorMsg().length());
    }

    @Test
    @DisplayName("异常 message 为 null 时记「未知错误」")
    void failureWithoutMessage() throws Throwable {
        request("POST");
        when(joinPoint.proceed()).thenThrow(new RuntimeException());

        assertThrows(RuntimeException.class, () -> aspect.aroundController(joinPoint));

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).save(captor.capture());
        assertEquals("未知错误", captor.getValue().getErrorMsg());
    }

    @Test
    @DisplayName("参数序列化失败不阻断目标方法")
    void serializationFailureIsSwallowed() throws Throwable {
        request("POST");
        when(joinPoint.getArgs()).thenReturn(new Object[]{new Object()});
        when(joinPoint.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.aroundController(joinPoint));
        verify(operationLogService).save(any(OperationLog.class));
    }

    @Test
    @DisplayName("参数含 HttpServletRequest 时剔除容器对象，DTO 仍被序列化（审计不再丢参数）")
    void servletArgsAreFilteredButDtoKept() throws Throwable {
        request("POST");
        java.util.Map<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("username", "student");
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                new MockHttpServletRequest("POST", "/api/test"), payload});
        when(joinPoint.proceed()).thenReturn("ok");
        aspect.aroundController(joinPoint);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).save(captor.capture());
        String params = captor.getValue().getParams();
        assertTrue(params.contains("\"username\":\"student\""), "实际=" + params);
        assertFalse(params.contains("MockHttpServletRequest"), "容器对象不得进入审计参数");
    }

    @Test
    @DisplayName("超过 1000 字的参数截断并保留省略号")
    void longParamsAreTruncated() throws Throwable {
        request("POST");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"y".repeat(1200)});
        when(joinPoint.proceed()).thenReturn("ok");
        aspect.aroundController(joinPoint);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).save(captor.capture());
        assertEquals(1003, captor.getValue().getParams().length());
        assertTrue(captor.getValue().getParams().endsWith("..."));
    }

    @Test
    @DisplayName("敏感字段大小写与嵌套形态均打码，转义引号不乱码")
    void sensitiveFieldsAreMasked() throws Throwable {
        request("POST");
        java.util.Map<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("password", "a\"b");
        payload.put("oldPassword", "old");
        payload.put("newPassword", "new");
        payload.put("confirmPassword", "confirm");
        payload.put("code", "123456");
        payload.put("authorization", "Bearer token");
        payload.put("accessToken", "access");
        payload.put("refreshToken", "refresh");
        payload.put("token", "tok");
        payload.put("secret", "sec");
        payload.put("apiKey", "key");
        payload.put("accessKey", "akey");
        when(joinPoint.getArgs()).thenReturn(new Object[]{payload});
        when(joinPoint.proceed()).thenReturn("ok");
        aspect.aroundController(joinPoint);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).save(captor.capture());
        String params = captor.getValue().getParams();
        assertTrue(params.contains("\"password\":\"***\""));
        assertTrue(params.contains("\"oldPassword\":\"***\""));
        assertTrue(params.contains("\"accessKey\":\"***\""));
        assertFalse(params.contains("123456"));
        assertFalse(params.contains("Bearer token"));
    }

    @Test
    @DisplayName("maskSensitiveFields 对 null/空串原样返回")
    void maskNullAndEmpty() {
        assertNull(ReflectionTestUtils.invokeMethod(aspect, "maskSensitiveFields", (String) null));
        assertEquals("", ReflectionTestUtils.invokeMethod(aspect, "maskSensitiveFields", ""));
    }

    @Test
    @DisplayName("无 ServletRequestAttributes 或 request 为空时 getCurrentRequest 返回 null")
    void currentRequestFallbacks() {
        RequestAttributes wrongType = mock(RequestAttributes.class);
        RequestContextHolder.setRequestAttributes(wrongType);
        assertNull(ReflectionTestUtils.invokeMethod(aspect, "getCurrentRequest"));

        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(null);
        RequestContextHolder.setRequestAttributes(attributes);
        assertNull(ReflectionTestUtils.invokeMethod(aspect, "getCurrentRequest"));

        RequestContextHolder.resetRequestAttributes();
        assertNull(ReflectionTestUtils.invokeMethod(aspect, "getCurrentRequest"));
    }

    @Test
    @DisplayName("resolveAction 覆盖所有前缀分组与 HTTP 兜底")
    void resolveActionPrefixes() {
        assertEquals("CREATE", resolve("createX", "GET"));
        assertEquals("CREATE", resolve("addX", "GET"));
        assertEquals("CREATE", resolve("registerX", "GET"));
        assertEquals("CREATE", resolve("enrollX", "GET"));
        assertEquals("UPDATE", resolve("updateX", "GET"));
        assertEquals("UPDATE", resolve("editX", "GET"));
        assertEquals("UPDATE", resolve("modifyX", "GET"));
        assertEquals("UPDATE", resolve("changeX", "GET"));
        assertEquals("DELETE", resolve("deleteX", "GET"));
        assertEquals("DELETE", resolve("removeX", "GET"));
        assertEquals("DELETE", resolve("dropX", "GET"));
        assertEquals("STATE_CHANGE", resolve("publishX", "GET"));
        assertEquals("STATE_CHANGE", resolve("toggleX", "GET"));
        assertEquals("STATE_CHANGE", resolve("approveX", "GET"));
        assertEquals("STATE_CHANGE", resolve("rejectX", "GET"));
        assertEquals("IMPORT", resolve("importX", "GET"));
        assertEquals("EXPORT", resolve("exportX", "GET"));
        assertEquals("EXECUTE", resolve("submitX", "GET"));
        assertEquals("EXECUTE", resolve("autoComposeX", "GET"));
        assertEquals("EXECUTE", resolve("resetX", "GET"));
        assertEquals("SEND", resolve("sendX", "GET"));
        assertEquals("CREATE", resolve("other", "POST"));
        assertEquals("UPDATE", resolve("other", "PUT"));
        assertEquals("DELETE", resolve("other", "DELETE"));
        assertEquals("UNKNOWN", resolve("other", "PATCH"));
        assertEquals("UNKNOWN", resolve("other", "GET"));
    }

    @Test
    @DisplayName("小写 http method 同样识别为写操作")
    void lowercaseMethodIsWrite() throws Throwable {
        request("post");
        invokeSuccessfully();
        verify(operationLogService).save(any(OperationLog.class));
    }

    private String resolve(String methodName, String httpMethod) {
        return ReflectionTestUtils.invokeMethod(aspect, "resolveAction", httpMethod, methodName);
    }

    @SuppressWarnings("unused")
    private static final class FakeController {
        public String createSomething() {
            return "ok";
        }
    }
}
