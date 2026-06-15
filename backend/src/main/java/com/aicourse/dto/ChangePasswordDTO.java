package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求DTO
 * 用于接收用户修改密码时的旧密码、新密码及确认密码
 */
@Schema(description = "修改密码请求")
public class ChangePasswordDTO {

    @NotBlank(message = "旧密码不能为空")
    @Schema(description = "当前登录密码", example = "OldPass123",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 30, message = "新密码长度需在6-30个字符之间")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "新密码必须同时包含字母和数字")
    @Schema(description = "新设置的密码", example = "NewPassword456",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;

    @NotBlank(message = "确认新密码不能为空")
    @Schema(description = "确认新密码（需与新密码一致）", example = "NewPassword456",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String confirmPassword;

    // ====================== Getter & Setter ======================

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
