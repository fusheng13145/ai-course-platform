package com.aicourse.service.impl;

import jakarta.mail.internet.InternetAddress;
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
 * 邮件服务单元测试：覆盖邮件类型文本、账号未配置早退、以及真实 SMTP 发送失败回退 RuntimeException 的分支。
 * 说明：Transport.send 为静态调用无法 mock，本测试通过未配置真实 SMTP 触发连接异常，
 * 覆盖 buildMailProperties / MailAuthenticator / catch 块 / RuntimeException 重抛路径。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceImplTest {

    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailServiceImpl();
        // 开发态默认：SMTP 未配置时仅把验证码打到日志、不抛异常（与 application.yaml 默认一致）
        ReflectionTestUtils.setField(service, "devLogCode", true);
    }

    // ===================== 账号未配置：早退分支 =====================
    @Test
    @DisplayName("sendVerificationCode: 未配置账号 -> 不抛异常直接返回")
    void sendVerificationCode_noConfig() {
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "123456", "REGISTER"));
    }

    @Test
    @DisplayName("sendVerificationCode: 各操作类型文本 switch 全分支")
    void sendVerificationCode_typeTextAll() {
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "1", "REGISTER"));
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "1", "RESET_PASSWORD"));
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "1", "FORGOT_PASSWORD"));
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "1", "OTHER"));
        assertDoesNotThrow(() -> service.sendVerificationCode("a@b.com", "1", null));
    }

    @Test
    @DisplayName("sendVerificationCode: 生产姿态未配置账号 -> 抛 IllegalStateException 而非静默成功")
    void sendVerificationCode_noConfig_prodFailClosed() {
        ReflectionTestUtils.setField(service, "devLogCode", false);
        assertThrows(IllegalStateException.class,
                () -> service.sendVerificationCode("a@b.com", "123456", "REGISTER"));
    }

    @Test
    @DisplayName("sendPasswordResetLink: 未配置账号 -> 早退")
    void sendPasswordResetLink_noConfig() {
        assertDoesNotThrow(() -> service.sendPasswordResetLink("a@b.com", "http://x/reset"));
    }

    // ===================== 账号已配置：实际发送（失败回退）分支 =====================
    @Test
    @DisplayName("sendVerificationCode: 账号已配置 -> 触发发送并因无 SMTP 抛 RuntimeException")
    void sendVerificationCode_sendFailure() {
        ReflectionTestUtils.setField(service, "username", "noreply@example.com");
        ReflectionTestUtils.setField(service, "password", "password");
        ReflectionTestUtils.setField(service, "fromName", "AI平台");
        ReflectionTestUtils.setField(service, "sslEnable", true);
        assertThrows(RuntimeException.class, () -> service.sendVerificationCode("a@b.com", "123456", "REGISTER"));
    }

    @Test
    @DisplayName("sendPasswordResetLink: 账号已配置 -> 触发发送并抛 RuntimeException")
    void sendPasswordResetLink_sendFailure() {
        ReflectionTestUtils.setField(service, "username", "noreply@example.com");
        ReflectionTestUtils.setField(service, "password", "password");
        ReflectionTestUtils.setField(service, "sslEnable", false);
        assertThrows(RuntimeException.class, () -> service.sendPasswordResetLink("a@b.com", "http://x/reset"));
    }

    @Test
    @DisplayName("InternetAddress 构造可被解析（构建邮件发件人分支）")
    void addressSanity() {
        assertDoesNotThrow(() -> new InternetAddress("a@b.com", null, "UTF-8"));
    }
}
