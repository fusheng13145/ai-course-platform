package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用户服务单元测试（@InjectMocks + @Mock + 显式 baseMapper）
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserMapper userMapper;
    // 修复 B5：UserServiceImpl 新增「教师授课范围」依赖，构造注入需要这两个 mock
    @Mock private CourseMapper courseMapper;
    @Mock private CourseEnrollmentMapper courseEnrollmentMapper;
    @InjectMocks private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);

        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setUsername("zhangsan");
        sampleUser.setPassword("secret");
        sampleUser.setFullName("张三");
        sampleUser.setEmail("zhangsan@example.com");
        sampleUser.setRole("STUDENT");
        sampleUser.setClassId(100L);
        sampleUser.setIsActive(true);
        sampleUser.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("获取个人资料 - 成功返回且密码脱敏")
    void testGetUserProfile_success() {
        when(userMapper.selectById(1L)).thenReturn(sampleUser);

        User result = userService.getUserProfile(1L);

        assertEquals("zhangsan", result.getUsername());
        assertNull(result.getPassword(), "密码必须置空");
    }

    @Test
    @DisplayName("获取个人资料 - 用户不存在抛异常")
    void testGetUserProfile_notFound() {
        when(userMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> userService.getUserProfile(99L));
    }

    @Test
    @DisplayName("更新个人资料 - 部分更新")
    void testUpdateProfile_partialUpdate() {
        when(userMapper.selectById(1L)).thenReturn(sampleUser);

        User updates = new User();
        updates.setFullName("张三丰");
        updates.setPhone("13900139000");

        userService.updateProfile(1L, updates);

        // 验证更新后的值
        assertEquals("张三丰", sampleUser.getFullName());
    }

    @Test
    @DisplayName("更新个人资料 - 用户不存在抛异常")
    void testUpdateProfile_notFound() {
        when(userMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> userService.updateProfile(99L, new User()));
    }

    @Test
    @DisplayName("获取同班同学 - 排除自己且密码脱敏")
    void testGetClassmates_success() {
        when(userMapper.selectById(1L)).thenReturn(sampleUser);
        User classmate = new User(); classmate.setId(2L); classmate.setUsername("lisi");
        classmate.setPassword("secret2"); classmate.setClassId(100L); classmate.setIsActive(true);
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(classmate));

        List<User> classmates = userService.getClassmates(1L);

        assertEquals(1, classmates.size());
        assertNull(classmates.get(0).getPassword());
    }

    @Test
    @DisplayName("获取同班同学 - 未加入班级抛异常")
    void testGetClassmates_noClass() {
        sampleUser.setClassId(null);
        when(userMapper.selectById(1L)).thenReturn(sampleUser);
        assertThrows(BusinessException.class, () -> userService.getClassmates(1L));
    }

    @Test
    @DisplayName("获取教师学生 - 仅返回该教师授课范围内有效选课的学生，且密码脱敏（修复 B5）")
    void testGetTeacherStudents_scopedToOwnCourses() {
        // 该教师名下有 1 门课程
        Course course = new Course();
        course.setId(100L);
        course.setTeacherId(10L);
        when(courseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(course));

        // 该课程下有 1 条有效且已审批的选课记录
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourseId(100L);
        enrollment.setStudentId(2L);
        enrollment.setIsActive(true);
        enrollment.setApprovalStatus("APPROVED");
        when(courseEnrollmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(enrollment));

        User s = new User();
        s.setId(2L);
        s.setUsername("stu1");
        s.setPassword("p1");
        s.setRole("STUDENT");
        s.setIsActive(true);
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));

        List<User> students = userService.getTeacherStudents(10L);

        assertEquals(1, students.size());
        assertEquals("stu1", students.get(0).getUsername());
        assertNull(students.get(0).getPassword(), "密码必须置空");
        // 课程查询必须以 teacherId 收敛（修复前完全忽略该入参）
        verify(courseMapper).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("获取教师学生 - 该教师无课程时返回空且不查询学生（修复 B5）")
    void testGetTeacherStudents_noCourses_returnsEmpty() {
        when(courseMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        List<User> students = userService.getTeacherStudents(10L);

        assertTrue(students.isEmpty());
        verify(courseEnrollmentMapper, never()).selectList(any(Wrapper.class));
        verify(userMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("获取教师学生 - teacherId 为空抛业务异常")
    void testGetTeacherStudents_nullTeacherId() {
        assertThrows(BusinessException.class, () -> userService.getTeacherStudents(null));
    }

    @Test
    @DisplayName("用户统计 - 基本信息+同学数")
    void testGetUserStats() {
        when(userMapper.selectById(1L)).thenReturn(sampleUser);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(5L);

        com.aicourse.vo.UserStatsVO stats = userService.getUserStats(1L);

        assertEquals(1L, stats.getUserId());
        assertEquals(5L, stats.getClassmateCount());
    }

    @Test
    @DisplayName("搜索用户 - 分页返回并脱敏")
    void testSearchUsers() {
        Page<User> page = new Page<>(1, 10);
        page.setRecords(List.of(sampleUser));
        page.setTotal(1);
        when(userMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        Page<User> result = userService.searchUsers("zhang", "STUDENT", 1, 10);

        assertEquals(1, result.getTotal());
        assertNull(result.getRecords().get(0).getPassword());
    }

    // ==================== 缓存失效契约（回归 2026-10-16 E2E 实测） ====================

    /**
     * 回归背景：updateById 此前直接继承 MyBatis-Plus 默认实现，不失效任何缓存——
     * 管理端把用户提为 ADMIN 后，GET /admin/users/{id}（getUserProfile @Cacheable）
     * 在 users 缓存 30 分钟 TTL 内仍返回旧角色；/auth/permissions（@Cacheable key=userId）
     * 同样可能返回旧角色的菜单/权限。表现为「改了角色、界面没变」，
     * 而 REST 鉴权（JwtRequestFilter 直读用户表）已即时生效，两端再次分叉。
     *
     * <p>注解语义需要 AOP 代理，纯单测观测不到拦截过程（与 PermissionServiceImplTest
     * 同口径：缓存语义由集成测试覆盖）；此处用反射把「写路径必须声明缓存失效」固化为护栏。</p>
     */
    @Test
    @DisplayName("updateById 必须声明 users(profile_+id) 与 permissions(userId) 两类缓存失效")
    void testUpdateById_declaresCacheEviction() throws Exception {
        // 覆写后声明形参为 User；未覆写时继承的是泛型擦除后的 updateById(Object)。
        // 两种形态都要能拿到「实际生效的方法」：优先取声参为 User 的声明方法，
        // 回退到擦除形态（其注解为空 → 断言失败，即该测得的回归）。
        Method updateById = Arrays.stream(UserServiceImpl.class.getMethods())
                .filter(m -> "updateById".equals(m.getName()) && m.getParameterCount() == 1)
                .filter(m -> m.getParameterTypes()[0] == User.class)
                .findFirst()
                .orElseGet(() -> {
                    try {
                        return UserServiceImpl.class.getMethod("updateById", Object.class);
                    } catch (NoSuchMethodException e) {
                        throw new IllegalStateException(e);
                    }
                });

        List<CacheEvict> evicts = new ArrayList<>();
        CacheEvict single = updateById.getAnnotation(CacheEvict.class);
        if (single != null) {
            evicts.add(single);
        }
        Caching caching = updateById.getAnnotation(Caching.class);
        if (caching != null) {
            evicts.addAll(Arrays.asList(caching.evict()));
        }

        assertFalse(evicts.isEmpty(),
                "updateById 必须声明缓存失效：管理端改角色/禁用后，users 与 permissions 缓存不得滞留旧值");

        // 注意：value/cacheNames 是 @AliasFor 别名，原始反射不会互映（AnnotationUtils 才会合成），
        // 这里合并两个属性读取，兼容两种书写风格（本项目 updateProfile 用 value、PermissionServiceImpl 用 cacheNames）。
        assertTrue(evicts.stream().anyMatch(e ->
                        cacheNamesOf(e).contains("users") && e.key().contains("profile_")),
                "必须按 'profile_' + id 失效 users 缓存（getUserProfile 的键口径）");
        assertTrue(evicts.stream().anyMatch(e ->
                        cacheNamesOf(e).contains("permissions")),
                "必须失效 permissions 缓存（getUserPermissions 按 userId 键控，含服务端角色与菜单）");
    }

    /** 合并 @CacheEvict 的 value 与 cacheNames 两个别名属性（原始反射不合成别名）。 */
    private static List<String> cacheNamesOf(CacheEvict evict) {
        List<String> names = new ArrayList<>(Arrays.asList(evict.cacheNames()));
        names.addAll(Arrays.asList(evict.value()));
        return names;
    }
}
