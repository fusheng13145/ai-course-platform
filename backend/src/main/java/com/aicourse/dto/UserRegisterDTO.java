package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 用户注册请求DTO
 * 用于接收前端用户注册时的请求数据，包含账号信息、个人信息及协议确认
 */
@Schema(description = "用户注册请求")
public class UserRegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需在3-20个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    @Schema(description = "用户名", example = "zhangsan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50个字符")
    @Schema(description = "真实姓名", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号码", example = "13800138000")
    private String phone;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "电子邮箱", example = "zhangsan@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度需在6-30个字符之间")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "密码必须同时包含字母和数字")
    @Schema(description = "登录密码", example = "Password123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "确认密码不能为空")
    @Schema(description = "确认密码", example = "Password123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String confirmPassword;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(STUDENT|TEACHER)$", message = "角色只能是STUDENT或TEACHER")
    @Schema(description = "用户角色", example = "STUDENT",
            allowableValues = {"STUDENT", "TEACHER"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String role;

    @AssertTrue(message = "必须同意用户协议和隐私政策")
    @Schema(description = "是否同意用户协议", example = "true")
    private Boolean agreement;

    @NotBlank(message = "注册验证码不能为空")
    @Schema(description = "注册验证码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    // ====================== Getter & Setter ======================

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getAgreement() {
        return agreement;
    }

    public void setAgreement(Boolean agreement) {
        this.agreement = agreement;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
