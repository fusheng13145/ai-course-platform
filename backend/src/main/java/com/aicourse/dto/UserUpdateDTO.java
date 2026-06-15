package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户信息更新DTO
 * 用于接收前端更新个人资料时的请求数据
 * 注意：不包含username/password/role等敏感字段，这些需要单独接口处理
 */
@Schema(description = "用户信息更新请求")
public class UserUpdateDTO {

    @Size(max = 50, message = "姓名长度不能超过50个字符")
    @Schema(description = "真实姓名", example = "张三")
    private String fullName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号码", example = "13800138000")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "电子邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "头像URL地址", example = "https://example.com/avatar.jpg")
    private String avatar;

    @Size(max = 500, message = "个人简介不能超过500字")
    @Schema(description = "个人简介", example = "热爱编程的Java开发者")
    private String bio;

    // ====================== Getter & Setter ======================

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

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
