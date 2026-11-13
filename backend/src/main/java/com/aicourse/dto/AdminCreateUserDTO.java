package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理员创建用户请求DTO（白名单字段）
 * 仅暴露允许管理员设置的字段，杜绝将整个 User 实体直接绑定请求体导致的
 * 越权赋值（Mass Assignment）：例如通过 JSON 注入 id、is_deleted、created_by 等列，
 * 或将密码以明文写入。
 */
@Schema(description = "管理员创建用户请求")
@Data
public class AdminCreateUserDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需在3-20个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    @Schema(description = "用户名", example = "zhangsan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Size(max = 50, message = "姓名长度不能超过50个字符")
    @Schema(description = "真实姓名", example = "张三")
    private String fullName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号码", example = "13800138000")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "电子邮箱", example = "zhangsan@example.com")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度需在6-30个字符之间")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "密码必须同时包含字母和数字")
    @Schema(description = "初始登录密码", example = "Password123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(STUDENT|TEACHER|ADMIN)$", message = "角色只能是STUDENT、TEACHER或ADMIN")
    @Schema(description = "用户角色", example = "STUDENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String role;

    @Schema(description = "是否启用（默认启用）", example = "true")
    private Boolean isActive = true;

    /** 学院ID（V12 组织归属；院系管理员缺省填本院，校级管理员可选填） */
    @Schema(description = "学院ID（院系管理员缺省为本院）", example = "1")
    private Long collegeId;

}
