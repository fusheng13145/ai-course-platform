package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 密码重置请求DTO（支持邮箱或手机号，至少其一由业务层判断）
 * 对应前端 ForgotPassword.vue 发送的 { email, code, newPassword }
 * <p>修复：密码复杂度不再由 DTO 用弱规则（6-30 字母+数字）校验，
 * 统一收口到 {@code com.aicourse.util.PasswordPolicy}（8-32 大小写+数字+特殊字符），
 * 避免重置通道成为弱口令旁路。
 */
@Schema(description = "密码重置请求（邮箱或手机号）")
@Data
public class ResetPasswordDTO {

    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱（与手机号二选一）", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "手机号（与邮箱二选一）", example = "13800138000")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "验证码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Schema(description = "新密码", example = "NewPassword456!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;

}
