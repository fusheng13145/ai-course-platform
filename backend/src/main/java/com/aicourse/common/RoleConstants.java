package com.aicourse.common;

/**
 * 角色常量集中定义，消除散落的 "ADMIN" / "TEACHER" / "STUDENT" 字符串字面量。
 *
 * <p>说明：角色字符串在系统内有 71 处引用，统一收敛到本常量类，便于在注解
 * （如 {@code @Schema(allowableValues = ...)}）与普通 Java 表达式中直接引用，
 * 避免拼写不一致与散落字面量难以检索的问题。</p>
 */
public final class RoleConstants {

    public static final String ADMIN = "ADMIN";
    public static final String TEACHER = "TEACHER";
    public static final String STUDENT = "STUDENT";

    private RoleConstants() {
    }
}
