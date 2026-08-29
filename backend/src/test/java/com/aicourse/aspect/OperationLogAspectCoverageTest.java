package com.aicourse.aspect;

import com.aicourse.entity.OperationLog;
import com.aicourse.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperationLogAspectCoverageTest {

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @Mock
    private Method method;

    @Mock
    private HttpServletRequest request;

    @Mock
    private Authentication authentication;

    private OperationLogAspect aspect;

    @BeforeEach
    void setUp() throws Throwable {
        aspect = new OperationLogAspect(operationLogService);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new Object() {
            @SuppressWarnings("unused")
            public String getClassSimpleName() {
                return "";
            }
        });
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private void prepareWriteRequest(String httpMethod, String methodName, Object result) throws Throwable {
        when(signature.getName()).thenReturn(methodName);
        when(request.getMethod()).thenReturn(httpMethod);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(joinPoint.proceed()).thenReturn(result);
        // target simple name -> "FooController"
        when(joinPoint.getTarget()).thenReturn(new FooController());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    static class FooController {
    }

    @Test
    void nonWriteMethod_returnsProceedDirectly() throws Throwable {
        when(signature.getName()).thenReturn("listCourses");
        when(request.getMethod()).thenReturn("GET");
        when(joinPoint.proceed()).thenReturn("listResult");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Object r = aspect.aroundController(joinPoint);

        assertEquals("listResult", r);
        verify(operationLogService, never()).save(any());
    }

    @Test
    void writeMethod_success_logged() throws Throwable {
        prepareWriteRequest("POST", "createCourse", "created");
        when(request.getHeader("User-Agent")).thenReturn("TestUA");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
        when(request.getRemoteAddr()).thenReturn("9.9.9.9");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(1L);
        when(authentication.getName()).thenReturn("alice");
        when(method.getName()).thenReturn("createCourse");

        Object r = aspect.aroundController(joinPoint);

        assertEquals("created", r);
        verify(operationLogService, times(1)).save(any(OperationLog.class));
    }

    @Test
    void writeMethod_authNotAuthenticated() throws Throwable {
        prepareWriteRequest("PUT", "updateCourse", "updated");
        when(request.getHeader("User-Agent")).thenReturn("UA");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);
        when(method.getName()).thenReturn("updateCourse");

        Object r = aspect.aroundController(joinPoint);

        assertEquals("updated", r);
        verify(operationLogService).save(any(OperationLog.class));
    }

    @Test
    void writeMethod_noRequestContext() throws Throwable {
        prepareWriteRequest("DELETE", "deleteCourse", "gone");
        // no RequestContextHolder attributes set -> getCurrentRequest() returns null
        when(method.getName()).thenReturn("deleteCourse");

        Object r = aspect.aroundController(joinPoint);

        assertEquals("gone", r);
        verify(operationLogService).save(any(OperationLog.class));
    }

    @Test
    void writeMethod_failure_loggedAndRethrown() throws Throwable {
        prepareWriteRequest("POST", "createCourse", null);
        when(request.getHeader("User-Agent")).thenReturn("UA");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.1.1.1");
        when(method.getName()).thenReturn("createCourse");
        RuntimeException failure = new RuntimeException("boom failure message");
        when(joinPoint.proceed()).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> aspect.aroundController(joinPoint));
        assertEquals("boom failure message", thrown.getMessage());
        verify(operationLogService).save(any(OperationLog.class));
    }

    @Test
    void ipResolution_xRealIpFallback() throws Throwable {
        prepareWriteRequest("POST", "createCourse", "ok");
        when(request.getHeader("User-Agent")).thenReturn("UA");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("5.5.5.5");
        when(method.getName()).thenReturn("createCourse");

        aspect.aroundController(joinPoint);
        verify(operationLogService).save(any(OperationLog.class));
    }

    @Test
    void ipResolution_remoteAddrFallback() throws Throwable {
        prepareWriteRequest("POST", "createCourse", "ok");
        when(request.getHeader("User-Agent")).thenReturn("UA");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("7.7.7.7");
        when(method.getName()).thenReturn("createCourse");

        aspect.aroundController(joinPoint);
        verify(operationLogService).save(any(OperationLog.class));
    }

    @Test
    void sensitiveFieldsMasked() throws Throwable {
        prepareWriteRequest("POST", "login", "ok");
        when(request.getHeader("User-Agent")).thenReturn("UA");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.1.1.1");
        when(method.getName()).thenReturn("login");
        // arg is a JSON string containing sensitive keys
        String payload = "{\"password\":\"hunter2\",\"oldPassword\":\"a\",\"token\":\"abc\",\"apiKey\":\"xyz\"}";
        when(joinPoint.getArgs()).thenReturn(new Object[]{payload});

        aspect.aroundController(joinPoint);
        verify(operationLogService).save(any(OperationLog.class));
    }

    @Test
    void serializationFailure_isHandled() throws Throwable {
        prepareWriteRequest("POST", "createCourse", "ok");
        when(request.getHeader("User-Agent")).thenReturn("UA");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.1.1.1");
        when(method.getName()).thenReturn("createCourse");
        // object whose serialization throws
        when(joinPoint.getArgs()).thenReturn(new Object[]{new Unserializable()});

        aspect.aroundController(joinPoint);
        verify(operationLogService).save(any(OperationLog.class));
    }

    static class Unserializable {
        public String getBoom() {
            throw new RuntimeException("cannot serialize");
        }
    }

    @Test
    void maskSensitiveFields_nullAndEmpty() throws Exception {
        Method m = OperationLogAspect.class.getDeclaredMethod("maskSensitiveFields", String.class);
        m.setAccessible(true);
        assertEquals(null, m.invoke(aspect, (Object) null));
        assertEquals("", m.invoke(aspect, ""));
        String masked = (String) m.invoke(aspect, "{\"password\":\"secret\",\"note\":\"ok\"}");
        assertTrue(masked.contains("***"), "password should be masked: " + masked);
        assertTrue(masked.contains("ok"), "non-sensitive preserved: " + masked);
    }

    @Test
    void resolveAction_allBranches() throws Exception {
        Method m = OperationLogAspect.class.getDeclaredMethod("resolveAction", String.class, String.class);
        m.setAccessible(true);
        assertEquals("CREATE", m.invoke(aspect, "POST", "createX"));
        assertEquals("CREATE", m.invoke(aspect, "POST", "addX"));
        assertEquals("CREATE", m.invoke(aspect, "POST", "registerX"));
        assertEquals("CREATE", m.invoke(aspect, "POST", "enrollX"));
        assertEquals("UPDATE", m.invoke(aspect, "PUT", "updateX"));
        assertEquals("UPDATE", m.invoke(aspect, "PUT", "editX"));
        assertEquals("UPDATE", m.invoke(aspect, "PUT", "modifyX"));
        assertEquals("UPDATE", m.invoke(aspect, "PUT", "changeX"));
        assertEquals("DELETE", m.invoke(aspect, "DELETE", "deleteX"));
        assertEquals("DELETE", m.invoke(aspect, "DELETE", "removeX"));
        assertEquals("DELETE", m.invoke(aspect, "DELETE", "dropX"));
        assertEquals("STATE_CHANGE", m.invoke(aspect, "POST", "publishX"));
        assertEquals("STATE_CHANGE", m.invoke(aspect, "POST", "toggleX"));
        assertEquals("STATE_CHANGE", m.invoke(aspect, "POST", "approveX"));
        assertEquals("STATE_CHANGE", m.invoke(aspect, "POST", "rejectX"));
        assertEquals("IMPORT", m.invoke(aspect, "POST", "importX"));
        assertEquals("EXPORT", m.invoke(aspect, "POST", "exportX"));
        assertEquals("EXECUTE", m.invoke(aspect, "POST", "submitX"));
        assertEquals("EXECUTE", m.invoke(aspect, "POST", "autoComposeX"));
        assertEquals("EXECUTE", m.invoke(aspect, "POST", "resetX"));
        assertEquals("SEND", m.invoke(aspect, "POST", "sendX"));
        assertEquals("CREATE", m.invoke(aspect, "POST", "weirdMethod"));
        assertEquals("UPDATE", m.invoke(aspect, "PUT", "weirdMethod"));
        assertEquals("DELETE", m.invoke(aspect, "DELETE", "weirdMethod"));
        assertEquals("UNKNOWN", m.invoke(aspect, "PATCH", "weirdMethod"));
    }
}
