package com.aicourse;

import com.aicourse.entity.User;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证流程集成测试：验证数据库种子用户存在、密码加密匹配。
 * 使用 @SpringBootTest + local 环境（依赖已运行的本地 MySQL + Redis）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
class AuthFlowIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("admin种子用户存在且密码可验证")
    void adminUserExists() {
        User admin = userService.getOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
        assertThat(admin).isNotNull();
        assertThat(admin.getEmail()).isEqualTo("admin@aicourse.com");
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        assertThat(admin.getIsActive()).isTrue();
        assertThat(passwordEncoder.matches("123456", admin.getPassword())).isTrue();
    }

    @Test
    @DisplayName("teacher种子用户存在")
    void teacherUserExists() {
        User teacher = userService.getOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "teacher"));
        assertThat(teacher).isNotNull();
        assertThat(teacher.getRole()).isEqualTo("TEACHER");
        assertThat(teacher.getDepartment()).isEqualTo("计算机学院");
    }

    @Test
    @DisplayName("student种子用户存在")
    void studentUserExists() {
        User student = userService.getOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "student"));
        assertThat(student).isNotNull();
        assertThat(student.getRole()).isEqualTo("STUDENT");
        assertThat(student.getStudentId()).isEqualTo("2024001");
    }

    @Test
    @DisplayName("全部8个种子用户已创建")
    void allSeedUsersExist() {
        long count = userService.count(new LambdaQueryWrapper<>());
        assertThat(count).isEqualTo(8);
    }

    @Test
    @DisplayName("密码复杂度：BCrypt编码与验证正确")
    void passwordEncodingWorks() {
        String rawPassword = "Test@123456";
        String encoded = passwordEncoder.encode(rawPassword);
        assertThat(encoded).startsWith("$2a$");
        assertThat(passwordEncoder.matches(rawPassword, encoded)).isTrue();
        assertThat(passwordEncoder.matches("WrongPassword", encoded)).isFalse();
    }
}
