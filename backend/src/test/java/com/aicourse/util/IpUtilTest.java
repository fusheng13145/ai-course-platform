package com.aicourse.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * IpUtil 单元测试：覆盖多层代理头回退、unknown 跳过、getRemoteAddr 兜底、多级代理取首个 IP。
 */
@ExtendWith(MockitoExtension.class)
class IpUtilTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void xForwardedFor_valid() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
        assertEquals("1.2.3.4", new IpUtil().getClientIp(request));
    }

    @Test
    void xForwardedFor_unknownFallsThrough() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("Proxy-Client-IP")).thenReturn("5.6.7.8");
        assertEquals("5.6.7.8", new IpUtil().getClientIp(request));
    }

    @Test
    void proxyAndWlProxy_unknownFallsThrough() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("Proxy-Client-IP")).thenReturn("unknown");
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn("9.10.11.12");
        assertEquals("9.10.11.12", new IpUtil().getClientIp(request));
    }

    @Test
    void allUnknown_fallsBackToRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("Proxy-Client-IP")).thenReturn("unknown");
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn("unknown");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        assertEquals("192.168.1.1", new IpUtil().getClientIp(request));
    }

    @Test
    void multiLevelProxy_takesFirst() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.1.1.1, 2.2.2.2, 3.3.3.3");
        assertEquals("1.1.1.1", new IpUtil().getClientIp(request));
    }
}
