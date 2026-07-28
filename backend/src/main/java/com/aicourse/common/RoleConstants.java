package com.aicourse.common;

/**
 * 角色常量集中定义，消除散落的 "ADMIN" / "TEACHER" / "STUDENT" 字符串字面量。
 *
 * <p>说明：项目已存在枚举 {@code enums.UserRole}（含 ADMIN/TEACHER/STUDENT 枚举项），
 * 其 {@code name()} 同样返回这些字符串。此处另立编译期常量类，便于在注解
 * （如 {@code @Schema(allowableValues = ...)}）与普通 Java 表达式中直接引用，
 * 避免与枚举语义耦合，也不破坏现有 {@code UserRole} 枚举的用法。</p>
 */
public final class RoleConstants {

    public static final String ADMIN = "ADMIN";
    public static final String TEACHER = "TEACHER";
    public static final String STUDENT = "STUDENT";

    private RoleConstants() {
    }
}
