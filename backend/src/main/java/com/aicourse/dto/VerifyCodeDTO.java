package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 邮箱验证码校验请求
 * 对应前端 ForgotPassword.vue 第二步「验证邮箱」：独立校验验证码有效性（不消费）。
 * 真正的消费在 /auth/reset-password（重置密码）时由后端完成。
 */
@Schema(description = "邮箱验证码校验请求")
@Data
public class VerifyCodeDTO {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱", example = "zhangsan@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "验证码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

}
