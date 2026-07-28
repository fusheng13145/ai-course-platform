package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 密码重置请求DTO（支持邮箱或手机号，至少其一由业务层判断）
 * 对应前端 ForgotPassword.vue 发送的 { email, code, newPassword }
 */
@Schema(description = "密码重置请求（邮箱或手机号）")
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱（与手机号二选一）", example = "zhangsan@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "手机号（与邮箱二选一）", example = "13800138000")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "验证码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 30, message = "新密码长度需在6-30个字符之间")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "新密码必须同时包含字母和数字")
    @Schema(description = "新密码", example = "NewPassword456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;

}
