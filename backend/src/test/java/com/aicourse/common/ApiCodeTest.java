package com.aicourse.common;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ApiCode 常量类覆盖：引用全部常量（指令覆盖）并实例化私有构造（覆盖构造行）。
 */
class ApiCodeTest {

    @Test
    void constants() {
        assertEquals(200, ApiCode.OK);
        assertEquals(400, ApiCode.BAD_REQUEST);
        assertEquals(401, ApiCode.UNAUTHORIZED);
        assertEquals(403, ApiCode.FORBIDDEN);
        assertEquals(404, ApiCode.NOT_FOUND);
        assertEquals(405, ApiCode.METHOD_NOT_ALLOWED);
        assertEquals(429, ApiCode.TOO_MANY_REQUESTS);
        assertEquals(503, ApiCode.SERVICE_UNAVAILABLE);
        assertEquals(500, ApiCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    void privateConstructor() throws Exception {
        Constructor<ApiCode> c = ApiCode.class.getDeclaredConstructor();
        c.setAccessible(true);
        c.newInstance();
    }
}
