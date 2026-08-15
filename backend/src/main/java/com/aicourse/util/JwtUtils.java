package com.aicourse.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JWT 工具类
 * 负责令牌生成、解析、校验、从请求头提取 Token
 */
@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    /** 访问令牌 Cookie 名（HttpOnly，JS 不可读，本质消除 XSS 窃取令牌风险） */
    public static final String ACCESS_TOKEN_COOKIE = "ai_course_token";
    /** 刷新令牌 Cookie 名（HttpOnly，path 收窄至刷新端点，进一步降低暴露面） */
    public static final String REFRESH_TOKEN_COOKIE = "ai_course_refresh";

    /** 令牌签发方（iss）——绑定本应用，防止令牌被跨服务/跨系统交叉接受 */
    private static final String ISSUER = "ai-course-platform";
    /** 令牌受众（aud）——绑定本应用 Web 端 */
    private static final String AUDIENCE = "ai-course-platform-web";

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
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
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
     * 计算令牌剩余有效期（秒）。
     * 用于 HttpOnly Cookie 的 maxAge 与 JWT 真实过期时间严格对齐，
     * 避免 Cookie 比令牌更久存在导致的安全隐患。
     * @param token JWT令牌
     * @return 剩余秒数（已过期或非法返回 0）
     */
    public long getRemainingSeconds(String token) {
        try {
            Date expiration = getClaims(token).getExpiration();
            long seconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            return seconds > 0 ? seconds : 0;
        } catch (JwtException | IllegalArgumentException e) {
            return 0;
        }
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
     * 校验签名、有效期，并强制校验 iss/aud（修复：旧实现仅验签，令牌可被
     * 其它使用同密钥的系统接受；现绑定本应用签发方与受众，跨系统令牌一律拒绝）。
     * @param token JWT令牌
     * @return 载荷对象 Claims
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
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

    /**
     * 对敏感令牌（如刷新令牌）进行 SHA-256 哈希，用于数据库存储。
     * 刷新令牌为高强度随机值，单向哈希后即便数据库泄漏也无法被还原利用，
     * 校验时仅需对传入明文再次哈希后等值比对。
     * @param input 待哈希明文
     * @return 小写十六进制 SHA-256 摘要
     */
    public static String sha256Hex(String input) {
        if (input == null) {
            return null;
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            log.error("计算 SHA-256 摘要失败: {}", e.getMessage(), e);
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}