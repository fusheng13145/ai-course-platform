package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.UserMapper;
import com.aicourse.service.UserService;
import com.aicourse.vo.UserStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.aicourse.common.RoleConstants;

/**
 * 用户服务实现类
 * 实现用户资料管理、班级同学、学生列表、统计、搜索等核心业务逻辑
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /**
     * 教师学生花名册收敛依赖（修复 B5）：
     * 原实现仅按 {@code role=STUDENT AND is_active=1} 全量返回，完全忽略入参 teacherId，
     * 导致任意教师均可拉取全平台学生 PII（fullName/email/phone）。现按
     * 「教师所授课程 → 选课记录 → 学生」收敛，并复用选课有效口径（见 {@link CourseEnrollmentServiceImpl#hasApprovedEnrollment}）。
     */
    private final CourseMapper courseMapper;
    private final CourseEnrollmentMapper courseEnrollmentMapper;

    public UserServiceImpl(CourseMapper courseMapper, CourseEnrollmentMapper courseEnrollmentMapper) {
        this.courseMapper = courseMapper;
        this.courseEnrollmentMapper = courseEnrollmentMapper;
    }

    /**
     * 获取用户完整资料（含班级名称关联查询）
     * 密码字段置空返回以保护隐私
     */
    @Override
    @Cacheable(value = "users", key = "'profile_' + #userId")
    public User getUserProfile(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 密码字段置空返回
        user.setPassword(null);
        return user;
    }

    /**
     * 更新用户（管理端改角色/禁用/资料、登录时的 lastLogin 回写均经此路径）。
     *
     * <p>缺陷修复（2026-10-16 E2E 实测）：本方法此前直接继承 MyBatis-Plus 默认实现，
     * 不失效任何缓存——管理端把用户提为 ADMIN 后，{@code GET /admin/users/{id}}
     * （getUserProfile，users 缓存 30 分钟 TTL）仍返回**旧角色**，
     * {@code /auth/permissions}（permissions 缓存按 userId 键控）同样滞留旧角色与旧菜单，
     * 出现「改了角色、界面没变」的假象；而 REST 鉴权（JwtRequestFilter 直读用户表）
     * 已即时生效，两端再次分叉。故写路径统一按用户维度失效两类缓存：</p>
     * <ul>
     *   <li>{@code users}：键 {@code 'profile_' + userId}（与 getUserProfile 键口径一致）；</li>
     *   <li>{@code permissions}：键 {@code userId}（getUserPermissions 的缓存，含服务端角色与菜单）。</li>
     * </ul>
     */
    @Override
    @Caching(evict = {
            @CacheEvict(value = "users", key = "'profile_' + #entity.id"),
            @CacheEvict(value = "permissions", key = "#entity.id")
    })
    public boolean updateById(User entity) {
        return super.updateById(entity);
    }

    /**
     * 修改个人资料
     * 支持部分字段更新，忽略null值
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "users", key = "'profile_' + #userId")
    public void updateProfile(Long userId, User updates) {
        log.info("更新用户");

        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 部分字段更新，只更新非空字段
        if (updates.getFullName() != null) {
            user.setFullName(updates.getFullName());
        }
        if (updates.getPhone() != null) {
            user.setPhone(updates.getPhone());
        }
        if (updates.getAvatarUrl() != null) {
            user.setAvatarUrl(updates.getAvatarUrl());
        }
        if (updates.getBio() != null) {
            user.setBio(updates.getBio());
        }
        if (updates.getMajor() != null) {
            user.setMajor(updates.getMajor());
        }
        if (updates.getGrade() != null) {
            user.setGrade(updates.getGrade());
        }
        if (updates.getDepartment() != null) {
            user.setDepartment(updates.getDepartment());
        }
        if (updates.getTitle() != null) {
            user.setTitle(updates.getTitle());
        }
        if (updates.getNotificationSettings() != null) {
            user.setNotificationSettings(updates.getNotificationSettings());
        }

        this.updateById(user);
    }

    /**
     * 获取同班同学列表
     * 通过用户classId查找同一班级的其他用户
     */
    @Override
    public List<User> getClassmates(Long userId) {
        User user = this.getById(userId);
        if (user == null || user.getClassId() == null) {
            throw new BusinessException("用户不存在或未加入班级");
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getClassId, user.getClassId())
                .ne(User::getId, userId)  // 排除自己
                .eq(User::getIsActive, true)
                .orderByAsc(User::getUsername);

        List<User> classmates = this.list(queryWrapper);
        // 密码脱敏
        classmates.forEach(u -> u.setPassword(null));
        return classmates;
    }

    /**
     * 获取教师的学生列表（修复 B5：按教师实际授课范围收敛，杜绝全平台学生 PII 外泄）
     *
     * <p>调用方必须传入**当前认证主体**的教师ID（{@code Authentication#getPrincipal()}），
     * 服务端绝不再信任任何客户端可伪造的路径/请求参数。链路：
     * <ol>
     *   <li>查该教师所授全部课程（{@code Course.teacherId == teacherId}）；</li>
     *   <li>取这些课程下**有效且已审批通过**的选课记录
     *       （{@code is_active=1 AND approval_status='APPROVED'}，与
     *       {@link CourseEnrollmentServiceImpl#hasApprovedEnrollment} 保持同一口径）；</li>
     *   <li>按 studentId **去重**（同一学生选该教师多门课只返回一次）；</li>
     *   <li>仅返回角色为 STUDENT 且账号有效的用户，并置空密码。</li>
     * </ol></p>
     */
    @Override
    public List<User> getTeacherStudents(Long teacherId) {
        if (teacherId == null) {
            throw new BusinessException(400, "教师ID不能为空");
        }

        // 1) 该教师所授全部课程
        List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, teacherId));
        if (courses.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> courseIds = courses.stream().map(Course::getId).collect(Collectors.toList());

        // 2) 这些课程下的有效且已审批选课记录（口径同 CourseEnrollmentService#hasApprovedEnrollment）
        List<CourseEnrollment> enrollments = courseEnrollmentMapper.selectList(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .in(CourseEnrollment::getCourseId, courseIds)
                        .eq(CourseEnrollment::getIsActive, true)
                        .eq(CourseEnrollment::getApprovalStatus, "APPROVED"));
        if (enrollments.isEmpty()) {
            return new ArrayList<>();
        }

        // 3) 学生ID去重
        Set<Long> studentIds = enrollments.stream()
                .map(CourseEnrollment::getStudentId)
                .collect(Collectors.toSet());
        if (studentIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 4) 仅取学生角色、有效账号，并按用户名排序
        List<User> students = this.list(new LambdaQueryWrapper<User>()
                .in(User::getId, studentIds)
                .eq(User::getRole, RoleConstants.STUDENT)
                .eq(User::getIsActive, true)
                .orderByAsc(User::getUsername));
        // 密码脱敏
        students.forEach(u -> u.setPassword(null));
        return students;
    }

    /**
     * 用户数据统计
     * 返回用户基本信息统计
     */
    @Override
    public UserStatsVO getUserStats(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        UserStatsVO stats = new UserStatsVO();
        stats.setUserId(user.getId());
        stats.setUsername(user.getUsername());
        stats.setFullName(user.getFullName());
        stats.setRole(user.getRole());
        stats.setEmail(user.getEmail());
        stats.setIsActive(user.getIsActive());
        stats.setLastLogin(user.getLastLogin());
        stats.setCreatedAt(user.getCreatedAt());

        // 统计同班同学数量（如果是学生）
        if (user.getClassId() != null) {
            LambdaQueryWrapper<User> classmateWrapper = new LambdaQueryWrapper<User>()
                    .eq(User::getClassId, user.getClassId())
                    .ne(User::getId, userId);
            long classmateCount = this.count(classmateWrapper);
            stats.setClassmateCount(classmateCount);
        }

        return stats;
    }

    /**
     * 搜索用户
     * 支持按关键词和角色筛选
     */
    @Override
    public Page<User> searchUsers(String keyword, String role, int page, int size) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .eq(role != null && !role.isEmpty(), User::getRole, role)
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(User::getUsername, keyword)
                        .or().like(User::getFullName, keyword)
                        .or().like(User::getEmail, keyword)
                        .or().like(User::getStudentId, keyword))
                .eq(User::getIsActive, true)
                .orderByDesc(User::getCreatedAt);

        Page<User> userPage = this.page(Page.of(page, size), queryWrapper);
        // 密码脱敏
        userPage.getRecords().forEach(u -> u.setPassword(null));
        return userPage;
    }
}
