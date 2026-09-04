package com.aicourse;

import com.aicourse.entity.Course;
import com.aicourse.entity.User;
import com.aicourse.service.CourseService;
import com.aicourse.service.UserService;
import com.aicourse.service.KnowledgePointService;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第一阶段+第三阶段 集成测试
 * 验证：自动组卷、操作日志、Redis缓存、核心CRUD
 *
 * <p>继承 {@link AbstractIntegrationTest}，基于 Testcontainers 提供的真实 MySQL + Redis 运行，
 * schema 与种子数据由 index.sql 初始化。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Phase1Phase3IntegrationTest extends AbstractIntegrationTest {

    @Autowired private CourseService courseService;
    @Autowired private UserService userService;
    @Autowired private KnowledgePointService knowledgePointService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Order(1)
    @DisplayName("上下文加载测试")
    void contextLoads() {
        assertNotNull(courseService);
        assertNotNull(userService);
        assertNotNull(knowledgePointService);
    }

    @Test
    @Order(2)
    @DisplayName("用户查询测试")
    void testUserQuery() {
        // 查询admin用户
        User user = userService.getById(1L);
        assertNotNull(user);
        assertEquals("admin", user.getUsername());
        assertEquals("ADMIN", user.getRole());
    }

    @Test
    @Order(3)
    @DisplayName("课程缓存测试")
    void testCourseCache() {
        // 第一次查询（走DB）
        Course course1 = courseService.getById(1L);
        assertNotNull(course1);

        // 第二次查询（应走Redis缓存）
        Course course2 = courseService.getById(1L);
        assertNotNull(course2);
        assertEquals(course1.getName(), course2.getName());
    }

    @Test
    @Order(4)
    @DisplayName("知识点查询测试")
    void testKnowledgePointQuery() {
        // 验证知识点服务可用
        var points = knowledgePointService.list();
        assertNotNull(points);
    }

    @Test
    @Order(5)
    @DisplayName("批量操作验证")
    @Transactional
    void testCourseCRUD() {
        // 创建
        Course course = new Course();
        course.setName("集成测试课程");
        course.setCode("TEST001");
        // 修复：courses.teacher_id 为 BIGINT NOT NULL 且无默认值（外键关联 users.id），
        // 未显式设置将触发 "Field 'teacher_id' doesn't have a default value"。
        // 此处使用 index.sql 种子数据中的教师账户（id=2，teacher@aicourse.com）以满足外键约束。
        course.setTeacherId(2L);
        boolean saved = courseService.save(course);
        assertTrue(saved);
        assertNotNull(course.getId());

        // 查询
        Course fetched = courseService.getById(course.getId());
        assertEquals("集成测试课程", fetched.getName());

        // 更新
        fetched.setName("更新后的课程");
        boolean updated = courseService.updateById(fetched);
        assertTrue(updated);

        // 删除
        boolean removed = courseService.removeById(course.getId());
        assertTrue(removed);
    }

    @Test
    @Order(6)
    @DisplayName("用户登录流测试")
    void testUserLoginFlow() {
        // 验证 admin 用户在数据库中存在
        User admin = userService.getById(1L);
        assertNotNull(admin, "admin 用户应存在于数据库");
        assertEquals("admin", admin.getUsername());
        assertEquals("ADMIN", admin.getRole());

        // 验证密码加密匹配
        // 种子数据中 admin 用户的密码应已通过 BCrypt 加密。
        // 修复：原硬编码 "admin123" 与 index.sql 实际种子哈希不符（测试从未在 CI 跑通过故未暴露）。
        // 经 bcrypt 实证校验，index.sql 中全部用户共用哈希对应的明文为 "123456"。
        // 安全提示：该默认密码强度极弱，生产部署前必须更换（见 docs 待办）。
        String rawPassword = "123456";
        boolean passwordMatches = passwordEncoder.matches(rawPassword, admin.getPassword());
        assertTrue(passwordMatches, "原始密码应能匹配数据库中加密存储的密码");

        // 验证用户信息可以通过 UserService 获取（getUserProfile 方法）
        User profile = userService.getUserProfile(1L);
        assertNotNull(profile, "通过 getUserProfile 应能获取用户信息");
        assertEquals("admin", profile.getUsername());
        // getUserProfile 应将密码置空返回
        assertNull(profile.getPassword(), "getUserProfile 不应返回密码");
    }
}
