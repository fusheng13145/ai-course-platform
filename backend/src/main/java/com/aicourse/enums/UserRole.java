package com.aicourse.enums;

/**
 * 用户角色枚举
 */
public enum UserRole {

    /** 管理员 */
    ADMIN("管理员"),
    /** 教师 */
    TEACHER("教师"),
    /** 学生 */
    STUDENT("学生");

    private final String desc;

    UserRole(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据枚举名称获取对应实例
     * @param name 枚举名称
     * @return 匹配枚举，无结果返回 null
     */
    public static UserRole getByName(String name) {
        for (UserRole role : values()) {
            if (role.name().equals(name)) {
                return role;
            }
        }
        return null;
    }
}