package com.aicourse.security;

import com.aicourse.common.AdminScope;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.User;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 管理员作用域判定组件（V12 / B-12-P-08 WP1）。
 *
 * <p>数据来源：作用域 authority 由 {@link JwtRequestFilter} 每请求按数据库现行用户写入
 * SecurityContext；院系 ID 仍从 users 表现行数据读取。因此管理员改作用域或改学院后
 * 立即生效，不依赖令牌 claim，也不需要重签存量令牌。</p>
 *
 * <p>兼容口径：没有 {@code SCOPE_ADMIN_COLLEGE} 的 ADMIN 视为校级（含 V12 回填前的旧账号），
 * 保持改造前管理员的既有行为。</p>
 */
@Component
public class AdminScopeGuard {

    private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    private final UserService userService;

    public AdminScopeGuard(UserService userService) {
        this.userService = userService;
    }

    /**
     * 当前登录用户ID（JwtRequestFilter 以 userId 作为 principal）。
     * @return 用户ID
     * @throws AccessDeniedException 未认证
     */
    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new AccessDeniedException("未认证或认证主体不合法");
        }
        return userId;
    }

    /**
     * 当前用户是否为院系管理员。
     * @return true=院系级
     */
    public boolean isCollegeAdmin() {
        return hasAuthority(AdminScope.AUTHORITY_COLLEGE);
    }

    /**
     * 当前用户是否为校级管理员（含未显式标注作用域的存量 ADMIN）。
     * @return true=校级
     */
    public boolean isSchoolAdmin() {
        return hasAuthority(ROLE_AUTHORITY_PREFIX + RoleConstants.ADMIN) && !isCollegeAdmin();
    }

    /**
     * 要求当前用户是校级管理员。
     * @throws AccessDeniedException 非校级管理员
     */
    public void assertSchoolOnly() {
        if (!isSchoolAdmin()) {
            throw new AccessDeniedException("仅校级管理员可执行该操作");
        }
    }

    /**
     * 要求当前用户可访问指定学院（校级放行，院系级仅限本学院）。
     * @param collegeId 目标学院ID
     * @throws AccessDeniedException 越权或学院ID为空
     */
    public void assertCollegeAccess(Long collegeId) {
        if (!isCollegeAdmin()) {
            return;
        }
        if (collegeId == null || !Objects.equals(currentCollegeId(), collegeId)) {
            throw new AccessDeniedException("无权访问其他学院的数据");
        }
    }

    /**
     * 当前管理员的有效作用域。
     * @return 院系管理员返回 {@link AdminScope#COLLEGE}，其余 ADMIN（含存量为空作用域）返回 {@link AdminScope#SCHOOL}
     */
    public String currentAdminScope() {
        return isCollegeAdmin() ? AdminScope.COLLEGE : AdminScope.SCHOOL;
    }

    /**
     * 查询过滤条件：院系管理员返回本学院ID，校级管理员返回 null（不过滤）。
     * @return 学院过滤ID或 null
     */
    public Long currentCollegeFilter() {
        return isCollegeAdmin() ? currentCollegeId() : null;
    }

    /**
     * 查询过滤条件：院系管理员返回本院用户ID列表，校级管理员返回 null（不过滤）。
     *
     * <p>列表为空表示本院暂无可归属用户；调用方应把空列表转换为「查不到任何数据」
     * （例如 {@code eq(userId, -1L)}），不得退化为全校查询。</p>
     * @return 本院用户ID列表或 null
     */
    public List<Long> currentCollegeUserIds() {
        if (!isCollegeAdmin()) {
            return null;
        }
        Long collegeId = currentCollegeId();
        return userService.list(new LambdaQueryWrapper<User>()
                        .eq(User::getCollegeId, collegeId))
                .stream()
                .map(User::getId)
                .toList();
    }

    /**
     * 要求当前用户可访问指定用户ID（校级放行，院系级仅限本学院）。
     * @param userId 目标用户ID
     * @throws AccessDeniedException 越权或目标用户不存在/未绑定学院
     */
    public void assertUserAccessById(Long userId) {
        if (!isCollegeAdmin()) {
            return;
        }
        if (userId == null) {
            throw new AccessDeniedException("无权访问其他学院的用户");
        }
        assertUserAccess(userService.getById(userId));
    }

    /**
     * 要求当前用户可访问指定用户（校级放行，院系级仅限本学院）。
     * @param target 目标用户
     * @throws AccessDeniedException 越权或目标未绑定学院
     */
    public void assertUserAccess(User target) {
        if (!isCollegeAdmin()) {
            return;
        }
        if (target == null || target.getCollegeId() == null
                || !Objects.equals(currentCollegeId(), target.getCollegeId())) {
            throw new AccessDeniedException("无权访问其他学院的用户");
        }
    }

    /**
     * 要求当前用户可管理指定用户：除学院边界外，院系管理员不得触碰 ADMIN 账号，
     * 防止通过改角色/改状态进行提权。
     * @param target 目标用户
     * @throws AccessDeniedException 越权或目标为管理员
     */
    public void assertUserManageable(User target) {
        assertUserAccess(target);
        if (isCollegeAdmin() && RoleConstants.ADMIN.equals(target.getRole())) {
            throw new AccessDeniedException("院系管理员不能管理管理员账号");
        }
    }

    /**
     * 要求当前用户可访问指定课程（校级放行，院系级按课程所属学院判定）。
     *
     * <p>V13 起课程具备 {@code college_id}（由负责人学院回填），优先按该列比较；
     * 未回填的历史数据回退授课教师 {@code users.college_id} 口径。</p>
     * @param course 目标课程
     * @throws AccessDeniedException 越权或课程/教师未绑定学院
     */
    public void assertCourseAccess(Course course) {
        if (!isCollegeAdmin()) {
            return;
        }
        if (course == null) {
            throw new AccessDeniedException("无权访问其他学院的课程");
        }
        if (course.getCollegeId() != null) {
            if (!Objects.equals(currentCollegeId(), course.getCollegeId())) {
                throw new AccessDeniedException("无权访问其他学院的课程");
            }
            return;
        }
        if (course.getTeacherId() == null) {
            throw new AccessDeniedException("无权访问其他学院的课程");
        }
        User teacher = userService.getById(course.getTeacherId());
        if (teacher == null || teacher.getCollegeId() == null
                || !Objects.equals(currentCollegeId(), teacher.getCollegeId())) {
            throw new AccessDeniedException("无权访问其他学院的课程");
        }
    }

    /**
     * 当前院系管理员的学院ID。
     * @return 学院ID
     * @throws AccessDeniedException 用户不存在或未绑定学院
     */
    public Long currentCollegeId() {
        User user = userService.getById(currentUserId());
        if (user == null) {
            throw new AccessDeniedException("当前用户不存在");
        }
        if (user.getCollegeId() == null) {
            throw new AccessDeniedException("当前院系管理员未绑定学院");
        }
        return user.getCollegeId();
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
