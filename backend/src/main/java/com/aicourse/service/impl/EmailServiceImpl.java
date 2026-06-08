package com.aicourse.service.impl;

import com.aicourse.service.EmailService;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * 邮件服务实现类
 * 支持发送验证码邮件、密码重置链接邮件，基于SMTP协议实现HTML格式邮件推送
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    // 邮件配置项
    @Value("${mail.host:smtp.qq.com}")
    private String host;

    @Value("${mail.port:587}")
    private int port;

    @Value("${mail.username:}")
    private String username;

    @Value("${mail.password:}")
    private String password;

    @Value("${mail.from-name:AI智能课程平台}")
    private String fromName;

    @Value("${mail.ssl-enable:true}")
    private boolean sslEnable;

    // 常量定义
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONTENT_TYPE_HTML = "text/html;charset=UTF-8";

    @Override
    public void sendVerificationCode(String toEmail, String code, String type) {
        String subject = "AI智能课程平台 - 验证码";
        String content = String.format(
                "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px'>" +
                "<h2 style='color:#667eea'>AI智能课程平台</h2>" +
                "<p>您好！</p>" +
                "<p>您正在进行<strong>%s</strong>操作，验证码为：</p>" +
                "<div style='background:#f5f5f5;padding:15px;font-size:24px;font-weight:bold;text-align:center;letter-spacing:5px;margin:20px 0'>%s</div>" +
                "<p style='color:#999;font-size:12px'>验证码有效期为5分钟，请勿将验证码告知他人。</p>" +
                "<p style='color:#999;font-size:12px'>如非本人操作，请忽略此邮件。</p>" +
                "<hr style='border:none;border-top:1px solid #eee;margin:30px 0'>" +
                "<p style='color:#999;font-size:12px;text-align:center'>此邮件由系统自动发送，请勿回复</p>" +
                "</div>",
                getTypeText(type), code
        );
        sendHtmlEmail(toEmail, subject, content);
    }

    @Override
    public void sendPasswordResetLink(String toEmail, String resetLink) {
        String subject = "AI智能课程平台 - 密码重置";
        String content = String.format(
                "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px'>" +
                "<h2 style='color:#667eea'>AI智能课程平台</h2>" +
                "<p>您好！</p>" +
                "<p>您申请了密码重置，请点击下方链接重置密码：</p>" +
                "<div style='margin:20px 0;text-align:center'>" +
                "<a href='%s' style='display:inline-block;background:#667eea;color:#fff;padding:12px 30px;text-decoration:none;border-radius:5px'>点击重置密码</a>" +
                "</div>" +
                "<p style='color:#999;font-size:12px'>链接有效期为30分钟，如无法点击请复制链接到浏览器打开：</p>" +
                "<p style='color:#666;font-size:12px;word-break:break-all'>%s</p>" +
                "<p style='color:#999;font-size:12px'>如非本人操作，请忽略此邮件。</p>" +
                "<hr style='border:none;border-top:1px solid #eee;margin:30px 0'>" +
                "<p style='color:#999;font-size:12px;text-align:center'>此邮件由系统自动发送，请勿回复</p>" +
                "</div>",
                resetLink, resetLink
        );
        sendHtmlEmail(toEmail, subject, content);
    }

    /**
     * 发送HTML格式邮件通用方法
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param htmlContent HTML正文
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        // 校验账号密码配置
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            logger.warn("邮件服务账号未配置，终止发送，收件人：{}", to);
            logger.info("【模拟发送】收件人：{}，主题：{}", to, subject);
            return;
        }

        try {
            Properties props = buildMailProperties();
            Session session = Session.getInstance(props, new MailAuthenticator());

            MimeMessage message = new MimeMessage(session);
            // 发件人
            message.setFrom(new InternetAddress(username, fromName, CHARSET_UTF8));
            // 收件人
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            // 主题 & 正文
            message.setSubject(subject, CHARSET_UTF8);
            message.setContent(htmlContent, CONTENT_TYPE_HTML);

            Transport.send(message);
            logger.info("邮件发送成功，收件人：{}，主题：{}", to, subject);
        } catch (Exception e) {
            logger.error("邮件发送失败，收件人：{}，异常信息：{}", to, e.getMessage(), e);
            throw new RuntimeException("邮件发送失败：" + e.getMessage(), e);
        }
    }

    /**
     * 构建SMTP配置参数
     */
    private Properties buildMailProperties() {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", Boolean.TRUE.toString());
        props.put("mail.smtp.starttls.enable", Boolean.TRUE.toString());
        if (sslEnable) {
            props.put("mail.smtp.ssl.enable", Boolean.TRUE.toString());
        }
        return props;
    }

    /**
     * 邮件账号认证器
     */
    private class MailAuthenticator extends Authenticator {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(username, password);
        }
    }

    /**
     * 转换操作类型为中文描述
     * @param type 操作类型标识
     * @return 中文文本
     */
    private String getTypeText(String type) {
        if (type == null) {
            return "身份验证";
        }
        return switch (type) {
            case "REGISTER" -> "账号注册";
            case "RESET_PASSWORD" -> "密码重置";
            case "FORGOT_PASSWORD" -> "找回密码";
            default -> "身份验证";
        };
    }
}