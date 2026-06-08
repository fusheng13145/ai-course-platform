package com.aicourse.enums;

/**
 * 题目类型枚举
 */
public enum QuestionType {

    /** 单选题 */
    SINGLE("单选题"),
    /** 多选题 */
    MULTIPLE("多选题"),
    /** 判断题 */
    JUDGE("判断题"),
    /** 简答题 */
    ESSAY("简答题"),
    /** 填空题 */
    FILL("填空题");

    private final String desc;

    QuestionType(String desc) {
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
    public static QuestionType getByName(String name) {
        for (QuestionType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }
}