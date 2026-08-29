package com.aicourse.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BusinessExceptionCoverageTest {

    @Test
    void defaultCodeConstructor_setsCode400() {
        BusinessException ex = new BusinessException("boom");
        assertEquals(400, ex.getCode());
        assertEquals("boom", ex.getMessage());
        assertNull((Object) ex.getCause(), "super(message) 未设置 cause");
    }

    @Test
    void customCodeConstructor_setsCode() {
        BusinessException ex = new BusinessException(409, "conflict");
        assertEquals(409, ex.getCode());
        assertEquals("conflict", ex.getMessage());
    }

    @Test
    void wrappedConstructor_setsCodeAndCause() {
        Throwable cause = new IllegalStateException("root");
        BusinessException ex = new BusinessException(500, "server error", cause);
        assertEquals(500, ex.getCode());
        assertEquals("server error", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void isRuntimeException() {
        BusinessException ex = new BusinessException("x");
        assertEquals(RuntimeException.class, ex.getClass().getSuperclass());
    }
}
