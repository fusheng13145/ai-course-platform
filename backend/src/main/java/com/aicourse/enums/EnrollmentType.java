package com.aicourse.enums;

/**
 * 选课报名类型枚举
 */
public enum EnrollmentType {

    /** 自由报名 */
    FREE("自由报名"),
    /** 需审批报名 */
    APPROVAL_REQUIRED("需审批报名");

    private final String desc;

    EnrollmentType(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据枚举名获取对应枚举实例
     * @param name 枚举名称
     * @return 匹配枚举，无结果返回 null
     */
    public static EnrollmentType getByName(String name) {
        for (EnrollmentType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }
}