package com.aicourse.enums;

/**
 * 作业/作答提交状态枚举
 */
public enum SubmissionStatus {

    /** 已提交 */
    SUBMITTED("已提交"),
    /** 已批改 */
    GRADED("已批改"),
    /** 已退回 */
    RETURNED("已退回"),
    /** 迟交 */
    LATE("迟交");

    private final String desc;

    SubmissionStatus(String desc) {
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
    public static SubmissionStatus getByName(String name) {
        for (SubmissionStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return null;
    }
}