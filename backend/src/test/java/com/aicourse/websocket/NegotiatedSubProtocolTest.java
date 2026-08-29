package com.aicourse.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * NegotiatedSubProtocol 单元测试：ThreadLocal 存/取/清除。
 */
class NegotiatedSubProtocolTest {

    @BeforeEach
    void clearBefore() {
        // 防止线程池复用导致上一测试残留的子协议值泄漏
        NegotiatedSubProtocol.clear();
    }

    @Test
    void setGetClear() {
        assertNull(NegotiatedSubProtocol.get());
        NegotiatedSubProtocol.set("proto-x");
        assertEquals("proto-x", NegotiatedSubProtocol.get());
        NegotiatedSubProtocol.clear();
        assertNull(NegotiatedSubProtocol.get());
    }
}
