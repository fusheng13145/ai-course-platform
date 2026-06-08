package com.aicourse.service;

/**
 * 邮件发送业务接口
 */
public interface EmailService {

    /**
     * 发送验证码邮件
     * @param toEmail 接收邮箱
     * @param code 验证码
     * @param type 验证码类型
     */
    void sendVerificationCode(String toEmail, String code, String type);

    /**
     * 发送密码重置链接邮件
     * @param toEmail 接收邮箱
     * @param resetLink 密码重置链接
     */
    void sendPasswordResetLink(String toEmail, String resetLink);
}