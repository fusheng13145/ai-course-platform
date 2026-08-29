package com.aicourse.common;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RoleConstants 常量类覆盖：引用常量并实例化私有构造。
 */
class RoleConstantsTest {

    @Test
    void constants() {
        assertEquals("ADMIN", RoleConstants.ADMIN);
        assertEquals("TEACHER", RoleConstants.TEACHER);
        assertEquals("STUDENT", RoleConstants.STUDENT);
    }

    @Test
    void privateConstructor() throws Exception {
        Constructor<RoleConstants> c = RoleConstants.class.getDeclaredConstructor();
        c.setAccessible(true);
        c.newInstance();
    }
}
