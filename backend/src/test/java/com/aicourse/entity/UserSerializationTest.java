package com.aicourse.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户实体敏感字段脱敏验证：确认密码哈希等字段经 @JsonIgnore 后不会出现在序列化结果中。
 */
class UserSerializationTest {

    @Test
    void password_and_sensitive_fields_excluded_from_json() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPassword("bcrypt-hash-secret");
        user.setFailedLoginAttempts(3);
        user.setRegisterIp("10.0.0.1");

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(user);

        assertThat(json).doesNotContain("bcrypt-hash-secret");
        assertThat(json).doesNotContain("password");
        assertThat(json).doesNotContain("failedLoginAttempts");
        assertThat(json).doesNotContain("registerIp");
        // 非敏感字段正常输出
        assertThat(json).contains("alice");
    }
}
