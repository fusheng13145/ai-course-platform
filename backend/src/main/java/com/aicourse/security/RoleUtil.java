package com.aicourse.security;

import com.aicourse.common.RoleConstants;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

/**
 * 角色判定工具：集中处理 {@link Authentication} 中的角色提取，与 {@link RoleConstants} 保持一致，
 * 避免在各 Controller / 访问控制组件中重复散落 ROLE_ 前缀裁剪逻辑。
 */
public final class RoleUtil {

    private RoleUtil() {
    }

    /**
     * 是否为教师或管理员（可查看题目答案等敏感信息）。
     */
    public static boolean isTeacherOrAdmin(Authentication auth) {
        if (auth == null) {
            return false;
        }
        List<String> roles = extractRoles(auth);
        return roles.contains(RoleConstants.TEACHER) || roles.contains(RoleConstants.ADMIN);
    }

    /**
     * 是否为管理员。管理员对跨用户资源拥有放行权限，用于 owner 校验时的特权豁免
     * （例如学生只能操作自己的学习路径，管理员可代查/代管）。
     */
    public static boolean isAdmin(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return extractRoles(auth).contains(RoleConstants.ADMIN);
    }

    /**
     * 当前用户角色（取第一个 ROLE_ 角色），无则返回空串。
     */
    public static String roleOf(Authentication auth) {
        if (auth == null) {
            return "";
        }
        List<String> roles = extractRoles(auth);
        return roles.isEmpty() ? "" : roles.get(0);
    }

    private static List<String> extractRoles(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();
    }
}
