package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.common.RoleConstants;
import com.aicourse.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * 认证信息读取的唯一实现点（ACL 公共件）。
 *
 * <p>背景：{@code roleOf}/{@code isAdmin}/{@code hasRole} 这组三行逻辑此前在
 * {@code ExamPaperAccessControl}、{@code ExamSessionAccessControl}、{@code SubmissionAccessControl}、
 * {@code KnowledgeAccessSupport}、{@code ResourceAccessSupport}、{@code ActivityVisibilityGuard}、
 * {@code CourseAccessControl} 里各抄了一份（阶段 2 DUP-16/18/19，方法体字节级同构）。
 * 权限判定抄多份的直接后果是“一处放宽即越权”，且审计时无法确认哪份是准的。
 *
 * <p>本类只做**纯读取**，不含任何业务判断，因此可以安全地被各域 ACL 复用；
 * 域类仍保留各自的“归属/边界”语义，不合并成巨型门面（避免循环依赖与回归面爆炸）。
 */
public final class AccessSupport {

    private AccessSupport() {
    }

    /**
     * 取当前用户的主角色（去掉 {@code ROLE_} 前缀）。
     *
     * @param auth 认证信息，可为 null
     * @return 主角色名；无角色时返回空串（不返回 null，避免调用方 NPE）
     */
    public static String roleOf(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) {
            return "";
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse("");
    }

    /**
     * 是否管理员。
     *
     * @param auth 认证信息，可为 null
     * @return true 表示持有 {@code ROLE_ADMIN}
     */
    public static boolean isAdmin(Authentication auth) {
        return hasRole(auth, RoleConstants.ADMIN);
    }

    /**
     * 是否持有指定角色。
     *
     * @param auth 认证信息，可为 null
     * @param role 角色名（不带 {@code ROLE_} 前缀）
     * @return true 表示持有该角色
     */
    public static boolean hasRole(Authentication auth, String role) {
        if (auth == null || auth.getAuthorities() == null || role == null || role.isEmpty()) {
            return false;
        }
        String expected = "ROLE_" + role;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(expected::equals);
    }

    /**
     * 取当前用户 ID。
     *
     * <p>统一在此处做类型转换与空值判定：历史实现散落在各 ACL 里，
     * 一旦 principal 不是 {@code Long}（例如匿名 token）就会抛 {@link ClassCastException}。
     *
     * @param auth 认证信息，可为 null
     * @return 用户 ID；无法取得时返回 null
     */
    public static Long principalId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof Long id ? id : null;
    }

    /**
     * 是否持有指定**授权项原文**（含前缀，如 {@code ROLE_ADMIN}、{@code SCOPE_ADMIN_COLLEGE}）。
     *
     * <p>给「作用域/权限项」判定使用：这类授权项不是角色（不带 {@code ROLE_} 语义），
     * 用 {@link #hasRole} 表达不了，但同样不能各自手写 authorities 遍历 ——
     * 否则作用域判定会像角色判定一样出现「一处放宽即越权」。</p>
     *
     * @param auth      认证信息，可为 null
     * @param authority 授权项原文
     * @return true 表示持有该授权项
     */
    public static boolean hasAuthority(Authentication auth, String authority) {
        if (auth == null || auth.getAuthorities() == null || authority == null || authority.isEmpty()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    /**
     * 取当前用户 ID，且**必取到**：取不到直接抛 401。
     *
     * <p>给 ACL 断言使用。历史实现是 {@code return (Long) auth.getPrincipal()}，
     * 一旦 principal 不是 {@code Long}（匿名令牌/异常桩）就抛 {@link ClassCastException} → 500，
     * 既无法定位也无法被客户端正确区分。此处统一为 fail-closed 的 401：语义是「没有可用身份」，
     * 与 {@code DiscussionServiceImpl#principalId} 的既有口径一致。</p>
     *
     * @param auth 认证信息，可为 null
     * @return 非空用户 ID
     * @throws BusinessException 401 未认证
     */
    public static Long requirePrincipalId(Authentication auth) {
        Long id = principalId(auth);
        if (id == null) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "未登录或登录态无效");
        }
        return id;
    }
}
