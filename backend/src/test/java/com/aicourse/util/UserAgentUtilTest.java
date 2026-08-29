package com.aicourse.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * UserAgentUtil 单元测试：覆盖真实 UA 解析与空 UA 返回 "Unknown" 的分支。
 */
@ExtendWith(MockitoExtension.class)
class UserAgentUtilTest {

    @Mock
    private HttpServletRequest request;

    private static final String CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Test
    void parsesBrowserAndOs() {
        when(request.getHeader("User-Agent")).thenReturn(CHROME_UA);
        UserAgentUtil util = new UserAgentUtil();
        assertNotNull(util.getBrowser(request));
        assertNotNull(util.getOperatingSystem(request));
        assertNotNull(util.getDeviceName(request));
    }

    @Test
    void emptyUserAgent_returnsUnknown() {
        when(request.getHeader("User-Agent")).thenReturn(null);
        UserAgentUtil util = new UserAgentUtil();
        assertEquals("Unknown", util.getBrowser(request));
        assertEquals("Unknown", util.getOperatingSystem(request));
        assertEquals("Unknown", util.getDeviceName(request));
    }
}
