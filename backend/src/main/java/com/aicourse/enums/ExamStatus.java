package com.aicourse.enums;

/**
 * 试卷状态枚举
 */
public enum ExamStatus {

    /** 草稿 */
    DRAFT("草稿"),
    /** 已发布 */
    PUBLISHED("已发布"),
    /** 已关闭 */
    CLOSED("已关闭");

    private final String desc;

    ExamStatus(String desc) {
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
    public static ExamStatus getByName(String name) {
        for (ExamStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return null;
    }
}