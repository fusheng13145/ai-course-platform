package com.aicourse.enums;

/**
 * 题目/课程难度等级枚举
 */
public enum DifficultyLevel {

    /** 简单 */
    EASY("简单"),
    /** 中等 */
    MEDIUM("中等"),
    /** 困难 */
    HARD("困难");

    private final String desc;

    DifficultyLevel(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据枚举名称获取对应枚举
     * @param name 枚举名称
     * @return 对应枚举，无匹配返回 null
     */
    public static DifficultyLevel getByName(String name) {
        for (DifficultyLevel level : values()) {
            if (level.name().equals(name)) {
                return level;
            }
        }
        return null;
    }
}