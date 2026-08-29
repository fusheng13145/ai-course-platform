package com.aicourse.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 邮件服务单元测试（补充版，自包含）。
 * 覆盖：账号未配置时的早退分支（含用户名/密码各种 null 与 blank 组合，触达 OR 短路各分支）、
 * 各操作类型 getTypeText 全分支、buildMailProperties 的 ssl 开/关分支、
 * 以及账号已配置时真实 SMTP 发送失败 -> catch -> 重抛 RuntimeException 的路径。
 *
 * <p>说明：{@code Transport.send(message)} 为静态调用，本环境无 mockito-inline（静态 mock 不可用），
 * 且无内网 SMTP 服务，故「发送成功」分支（{@code log.info("邮件发送成功...")}）无法在单测中覆盖，
 * 该 1~2 条指令缺口已在汇报中说明。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceImplCoverageTest {

    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailServiceImpl();
    }

    // ---------- 账号未配置：早退分支（OR 短路组合） ----------

    @Test
    @DisplayName("未配置：username/password 均默认 null -> 早退")
    void noConfig_bothNull() {
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "123456", "REGISTER"));
        assertDoesNotThrow(() -> service.sendPasswordResetLink("a@b.com", "http://x/reset"));
    }

    @Test
    @DisplayName("未配置：username 有值但 password 为 blank -> 早退（命中 password.isBlank 分支）")
    void noConfig_passwordBlank() {
        ReflectionTestUtils.setField(service, "username", "noreply@example.com");
        ReflectionTestUtils.setField(service, "password", "   ");
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "123456", "REGISTER"));
    }

    @Test
    @DisplayName("未配置：username 为 blank 但 password 有值 -> 早退（命中 username.isBlank 分支）")
    void noConfig_usernameBlank() {
        ReflectionTestUtils.setField(service, "username", "");
        ReflectionTestUtils.setField(service, "password", "secret");
        assertDoesNotThrow(() -> service.sendPasswordResetLink("a@b.com", "http://x/reset"));
    }

    @Test
    @DisplayName("未配置：username 为 null 但 password 非空 -> 早退（命中 username==null 分支）")
    void noConfig_usernameNull() {
        ReflectionTestUtils.setField(service, "password", "secret");
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "123456", "REGISTER"));
    }

    // ---------- 操作类型文本 switch 全分支 ----------

    @Test
    @DisplayName("sendVerificationCode: 各 type 文本 switch 分支 + null 默认分支")
    void typeText_allBranches() {
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "1", "REGISTER"));
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "1", "RESET_PASSWORD"));
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "1", "FORGOT_PASSWORD"));
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "1", "OTHER_UNKNOWN"));
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "1", null));
    }

    // ---------- 账号已配置：触发发送（失败回退）分支 ----------

    @Test
    @DisplayName("sendVerificationCode: 已配置 + sslEnable=true -> 触达 buildMailProperties(ssl 开) 并发送失败抛 RuntimeException")
    void sendFailure_sslTrue() {
        ReflectionTestUtils.setField(service, "username", "noreply@example.com");
        ReflectionTestUtils.setField(service, "password", "password");
        ReflectionTestUtils.setField(service, "fromName", "AI平台");
        ReflectionTestUtils.setField(service, "sslEnable", true);
        ReflectionTestUtils.setField(service, "host", "127.0.0.1");
        ReflectionTestUtils.setField(service, "port", 9);
        assertThrows(RuntimeException.class, () -> service.sendVerificationCode("a@b.com", "123456", "REGISTER"));
    }

    @Test
    @DisplayName("sendVerificationCode: 已配置 + sslEnable=false -> 触达 buildMailProperties(ssl 关) 并发送失败抛 RuntimeException")
    void sendFailure_sslFalse() {
        ReflectionTestUtils.setField(service, "username", "noreply@example.com");
        ReflectionTestUtils.setField(service, "password", "password");
        ReflectionTestUtils.setField(service, "sslEnable", false);
        ReflectionTestUtils.setField(service, "host", "127.0.0.1");
        ReflectionTestUtils.setField(service, "port", 9);
        assertThrows(RuntimeException.class, () -> service.sendVerificationCode("a@b.com", "123456", "RESET_PASSWORD"));
    }

    @Test
    @DisplayName("sendPasswordResetLink: 已配置 + sslEnable=true -> 发送失败抛 RuntimeException")
    void sendResetFailure_sslTrue() {
        ReflectionTestUtils.setField(service, "username", "noreply@example.com");
        ReflectionTestUtils.setField(service, "password", "password");
        ReflectionTestUtils.setField(service, "fromName", "AI平台");
        ReflectionTestUtils.setField(service, "sslEnable", true);
        ReflectionTestUtils.setField(service, "host", "127.0.0.1");
        ReflectionTestUtils.setField(service, "port", 9);
        assertThrows(RuntimeException.class, () -> service.sendPasswordResetLink("a@b.com", "http://x/reset?t=abc"));
    }

    @Test
    @DisplayName("sendPasswordResetLink: 已配置 + sslEnable=false -> 发送失败抛 RuntimeException")
    void sendResetFailure_sslFalse() {
        ReflectionTestUtils.setField(service, "username", "noreply@example.com");
        ReflectionTestUtils.setField(service, "password", "password");
        ReflectionTestUtils.setField(service, "sslEnable", false);
        ReflectionTestUtils.setField(service, "host", "127.0.0.1");
        ReflectionTestUtils.setField(service, "port", 9);
        assertThrows(RuntimeException.class, () -> service.sendPasswordResetLink("a@b.com", "http://x/reset"));
    }

    @Test
    @DisplayName("账号已配置但 host 不可达 -> 连接异常经 catch 重抛 RuntimeException")
    void sendFailure_unreachableHost() {
        ReflectionTestUtils.setField(service, "username", "noreply@example.com");
        ReflectionTestUtils.setField(service, "password", "password");
        ReflectionTestUtils.setField(service, "sslEnable", false);
        // 指向一个几乎必然无法建立 SMTP 会话的地址，确保 Transport.send 抛异常
        ReflectionTestUtils.setField(service, "host", "127.0.0.1");
        ReflectionTestUtils.setField(service, "port", 9);
        assertThrows(RuntimeException.class, () -> service.sendPasswordResetLink("a@b.com", "http://x/reset"));
    }
}
