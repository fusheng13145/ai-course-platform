package com.aicourse.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 * 负责令牌生成、解析、校验、从请求头提取 Token
 */
@Component
public class JwtUtils {

    /** JWT 加密密钥 */
    private final SecretKey secretKey;
    /** 记住我 - Token 有效期(毫秒) */
    private final long tokenExpiration;
    /** 普通会话 - Token 有效期(毫秒) */
    private final long sessionExpiration;

    /**
     * 构造注入配置参数，并初始化加密密钥
     * @param secret JWT 签名密钥
     * @param tokenExpiration 长时效 Token 过期时间
     * @param sessionExpiration 会话 Token 过期时间
     */
    public JwtUtils(@Value("${jwt.secret}") String secret,
                    @Value("${jwt.expiration}") long tokenExpiration,
                    @Value("${jwt.session-expiration}") long sessionExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenExpiration = tokenExpiration;
        this.sessionExpiration = sessionExpiration;
    }

    /**
     * 生成 JWT 令牌
     * @param userId 用户ID
     * @param username 用户名
     * @param role 角色标识
     * @param rememberMe 是否记住我
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username, String role, boolean rememberMe) {
        long expireTime = rememberMe ? tokenExpiration : sessionExpiration;
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expireTime);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从 Token 获取用户ID
     * @param token JWT令牌
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Token 获取用户名
     * @param token JWT令牌
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("username", String.class);
    }

    /**
     * 从 Token 获取用户角色
     * @param token JWT令牌
     * @return 角色标识
     */
    public String getRoleFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("role", String.class);
    }

    /**
     * 校验 Token 是否合法有效
     * @param token JWT令牌
     * @return true=合法，false=非法/过期/格式错误
     */
    public boolean validateToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // 令牌过期、签名错误、格式非法、空参数均判定为无效
            return false;
        }
    }

    /**
     * 解析 Token 获取载荷信息
     * @param token JWT令牌
     * @return 载荷对象 Claims
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Authorization 请求头提取 Bearer 后的 Token
     * @param header 请求头 Authorization 值
     * @return 纯Token字符串，格式异常返回 null
     */
    public String extractTokenFromHeader(String header) {
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}