package com.aicourse.enums;

/**
 * 知识点/课程关联关系类型枚举
 */
public enum RelationType {

    /** 前置关联 */
    PREREQUISITE("前置关联"),
    /** 后续关联 */
    SUBSEQUENT("后续关联"),
    /** 相关联 */
    RELATED("相关联"),
    /** 父级关联 */
    PARENT("父级关联"),
    /** 依赖关联 */
    DEPENDENCY("依赖关联");

    private final String desc;

    RelationType(String desc) {
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
    public static RelationType getByName(String name) {
        for (RelationType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }
}