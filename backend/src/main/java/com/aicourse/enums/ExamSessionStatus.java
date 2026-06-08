package com.aicourse.enums;

/**
 * 考试作答状态枚举
 */
public enum ExamSessionStatus {

    /** 考试进行中 */
    STARTED("考试进行中"),
    /** 已提交试卷 */
    SUBMITTED("已提交试卷"),
    /** 已批改完成 */
    GRADED("已批改完成");

    private final String desc;

    ExamSessionStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据枚举名称匹配对应实例
     * @param name 枚举名称
     * @return 匹配的枚举，无匹配返回 null
     */
    public static ExamSessionStatus getByName(String name) {
        for (ExamSessionStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return null;
    }
}