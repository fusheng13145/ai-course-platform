package com.aicourse.enums;

/**
 * 审批状态枚举
 */
public enum ApprovalStatus {

    /** 待审批 */
    PENDING("待审批"),
    /** 已通过 */
    APPROVED("已通过"),
    /** 已驳回 */
    REJECTED("已驳回");

    private final String desc;

    ApprovalStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据枚举名称匹配
     * @param name 枚举名
     * @return 对应枚举
     */
    public static ApprovalStatus getByName(String name) {
        for (ApprovalStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return null;
    }
}