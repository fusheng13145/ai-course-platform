package com.aicourse.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ResourceNotFoundExceptionCoverageTest {

    @Test
    void messageConstructor() {
        ResourceNotFoundException ex = new ResourceNotFoundException("not found");
        assertEquals("not found", ex.getMessage());
        assertNull((Object) ex.getCause(), "no cause");
    }

    @Test
    void wrappedConstructor_setsCause() {
        Throwable cause = new NullPointerException("missing");
        ResourceNotFoundException ex = new ResourceNotFoundException("not found", cause);
        assertEquals("not found", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void isRuntimeException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("x");
        assertEquals(RuntimeException.class, ex.getClass().getSuperclass());
    }
}
