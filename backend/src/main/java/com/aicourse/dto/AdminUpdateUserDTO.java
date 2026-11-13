package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理员更新用户请求DTO（白名单字段，不含密码）
 *
 * <p>安全要点：
 * <ol>
 *     <li>不包含 {@code password} 字段——管理员修改密码必须走独立的修改密码接口，
 *         避免更新资料时把密码以明文/弱口令覆盖写入；</li>
 *     <li>不包含 {@code id} 字段——主键由 URL 路径 {@code /users/{id}} 决定，
 *         杜绝请求体注入任意主键（IDOR / 覆盖他人记录）；</li>
 *     <li>仅暴露业务允许变更的列，其余 User 实体字段（如失败登录计数、锁定时间、
 *         注册 IP 等）不可经此接口被外部篡改。</li>
 * </ol>
 */
@Schema(description = "管理员更新用户请求（不含密码，密码修改走独立接口）")
@Data
public class AdminUpdateUserDTO {

    @Size(min = 3, max = 20, message = "用户名长度需在3-20个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    @Schema(description = "用户名", example = "zhangsan")
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

    @Pattern(regexp = "^(STUDENT|TEACHER|ADMIN)$", message = "角色只能是STUDENT、TEACHER或ADMIN")
    @Schema(description = "用户角色", example = "STUDENT")
    private String role;

    @Schema(description = "是否启用", example = "true")
    private Boolean isActive;

    @Schema(description = "头像URL地址", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Size(max = 500, message = "个人简介不能超过500字")
    @Schema(description = "个人简介", example = "热爱编程的Java开发者")
    private String bio;

    /** 学院ID（V12 组织归属；院系管理员只能保持本院，校级管理员可调整） */
    @Schema(description = "学院ID（院系管理员只能为本院）", example = "1")
    private Long collegeId;

}
