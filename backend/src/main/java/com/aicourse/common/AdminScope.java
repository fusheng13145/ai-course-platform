package com.aicourse.common;

import java.util.Locale;

/**
 * 管理员作用域常量（V12 / B-12-P-08 WP1）。
 *
 * <p>角色仍只有 {@code ADMIN} 一个，作用域是管理员的「管辖边界」属性，
 * 不是第二个登录角色：</p>
 * <ul>
 *   <li>{@code SCHOOL}：校级管理员，管辖全校；</li>
 *   <li>{@code COLLEGE}：院系管理员，仅管辖自己的学院；</li>
 *   <li>{@code ALL}：权限项的适用范围，任何管理员都可见。</li>
 * </ul>
 *
 * <p>JWT 过滤器按数据库现行用户的 {@code admin_scope} 附加
 * {@code SCOPE_ADMIN_SCHOOL}/{@code SCOPE_ADMIN_COLLEGE} authority，
 * 不写进令牌——存量已登录会话因此不受影响。</p>
 */
public final class AdminScope {

    /** 权限项适用范围：所有管理员可见 */
    public static final String ALL = "ALL";

    /** 校级管理员 */
    public static final String SCHOOL = "SCHOOL";

    /** 院系管理员 */
    public static final String COLLEGE = "COLLEGE";

    /** 校级管理员的 Spring Security authority */
    public static final String AUTHORITY_SCHOOL = "SCOPE_ADMIN_SCHOOL";

    /** 院系管理员的 Spring Security authority */
    public static final String AUTHORITY_COLLEGE = "SCOPE_ADMIN_COLLEGE";

    private AdminScope() {
    }

    /**
     * 规范化作用域字符串（去空白并转大写）。
     * @param scope 原始值
     * @return 规范化值；null/空白返回 null
     */
    public static String normalize(String scope) {
        if (scope == null || scope.isBlank()) {
            return null;
        }
        return scope.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 是否为合法的管理员作用域（SCHOOL/COLLEGE）。
     * @param scope 作用域
     * @return true=合法
     */
    public static boolean isValid(String scope) {
        String normalized = normalize(scope);
        return SCHOOL.equals(normalized) || COLLEGE.equals(normalized);
    }

    /**
     * 计算某角色应附加的作用域 authority。
     * @param role 角色标识
     * @param scope 用户 admin_scope
     * @return authority；非 ADMIN 或作用域非法时返回 null
     */
    public static String authorityOf(String role, String scope) {
        if (!RoleConstants.ADMIN.equals(role) || !isValid(scope)) {
            return null;
        }
        return COLLEGE.equals(normalize(scope)) ? AUTHORITY_COLLEGE : AUTHORITY_SCHOOL;
    }
}
