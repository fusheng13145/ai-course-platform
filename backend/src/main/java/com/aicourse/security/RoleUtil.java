package com.aicourse.security;

import com.aicourse.common.RoleConstants;
import org.springframework.security.core.Authentication;

/**
 * 角色判定工具（兼容门面）。
 *
 * <p>本类在阶段 3 之前是角色判定的实现点之一，与 {@link AccessSupport} 各自持有一套
 * 「剥 {@code ROLE_} 前缀 + 精确匹配」的逻辑（阶段 2 DUP-19 的同类问题）。
 * 现在**实现只在 {@link AccessSupport} 一处**，本类退化为转发门面：保留类名与签名是为了
 * 不制造大批调用点改动，避免把一次「实现收敛」变成一次「全仓重命名」。</p>
 *
 * <p>新代码请直接使用 {@link AccessSupport}；本类只维持既有调用点可用。</p>
 */
public final class RoleUtil {

    private RoleUtil() {
    }

    /**
     * 是否为教师或管理员（可查看题目答案等敏感信息）。
     */
    public static boolean isTeacherOrAdmin(Authentication auth) {
        return AccessSupport.hasRole(auth, RoleConstants.TEACHER) || AccessSupport.isAdmin(auth);
    }

    /**
     * 是否为管理员。管理员对跨用户资源拥有放行权限，用于 owner 校验时的特权豁免
     * （例如学生只能操作自己的学习路径，管理员可代查/代管）。
     */
    public static boolean isAdmin(Authentication auth) {
        return AccessSupport.isAdmin(auth);
    }

    /**
     * 当前用户角色（取第一个 {@code ROLE_} 角色），无则返回空串。
     */
    public static String roleOf(Authentication auth) {
        return AccessSupport.roleOf(auth);
    }
}
