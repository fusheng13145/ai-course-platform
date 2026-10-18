package com.aicourse.util;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtUtils 单元测试：覆盖令牌生成 / 解析 / 各 getter / 剩余有效期 / 校验（含空与非法）/
 * 从 Header 提取 Bearer / sha256Hex（含 null）。
 * 注意：密钥需 >= 32 字节以满足 HS256 最小长度要求。
 */
class JwtUtilsTest {

    private final JwtUtils jwtUtils = new JwtUtils(
            "my-super-secret-key-for-jwt-tests-1234567890",
            3_600_000L, 3_600_000L);

    @Test
    void generateAndValidate() {
        String token = jwtUtils.generateToken(1L, "alice", "STUDENT", false);
        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));
        assertEquals(1L, jwtUtils.getUserIdFromToken(token));
        assertEquals("alice", jwtUtils.getUsernameFromToken(token));
        assertEquals("STUDENT", jwtUtils.getRoleFromToken(token));
        assertTrue(jwtUtils.getRemainingSeconds(token) > 0);
    }

    @Test
    @DisplayName("同用户同秒两次签发必须得到不同令牌（每次登录有独立 jti，避免 user_sessions 出现同令牌重复行）")
    void generateToken_isUniquePerInvocation() {
        String first = jwtUtils.generateToken(1L, "alice", "TEACHER", false);
        String second = jwtUtils.generateToken(1L, "alice", "TEACHER", false);
        assertNotEquals(first, second);
        // 加了随机 jti 后令牌仍可正常解析出原有声明
        assertEquals(1L, jwtUtils.getUserIdFromToken(second));
        assertEquals("alice", jwtUtils.getUsernameFromToken(second));
        assertEquals("TEACHER", jwtUtils.getRoleFromToken(second));
        assertTrue(jwtUtils.validateToken(second));
    }

    @Test
    void validate_nullAndGarbage() {
        assertFalse(jwtUtils.validateToken(null));
        assertFalse(jwtUtils.validateToken(""));
        assertFalse(jwtUtils.validateToken("not.a.jwt"));
    }

    @Test
    void getRemainingSeconds_invalidReturnsZero() {
        assertEquals(0, jwtUtils.getRemainingSeconds("garbage.token.here"));
    }

    @Test
    void extractTokenFromHeader() {
        assertEquals("abc", jwtUtils.extractTokenFromHeader("Bearer abc"));
        assertNull(jwtUtils.extractTokenFromHeader("Basic abc"));
        assertNull(jwtUtils.extractTokenFromHeader(null));
    }

    @Test
    void sha256Hex() {
        String hash = JwtUtils.sha256Hex("abc");
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertNull(JwtUtils.sha256Hex(null));
    }
}
