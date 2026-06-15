package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录请求DTO
 * 用于接收前端用户登录时的账号密码及记住我选项
 */
@Schema(description = "用户登录请求")
public class UserLoginDTO {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名（支持用户名/邮箱/手机号登录）", example = "zhangsan",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "登录密码", example = "Password123",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "是否记住登录状态（7天有效期）", example = "false")
    private Boolean rememberMe = false;

    // ====================== Getter & Setter ======================

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(Boolean rememberMe) {
        this.rememberMe = rememberMe;
    }
}
