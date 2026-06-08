package com.aicourse.enums;

/**
 * 消息通知类型枚举
 */
public enum NotificationCategory {

    /** 系统通知 */
    SYSTEM("系统通知"),
    /** 学习通知 */
    LEARNING("学习通知"),
    /** 社交互动 */
    SOCIAL("社交互动"),
    /** 成就通知 */
    ACHIEVEMENT("成就通知");

    private final String desc;

    NotificationCategory(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据枚举名获取对应实例
     * @param name 枚举名称
     * @return 匹配枚举，无结果返回 null
     */
    public static NotificationCategory getByName(String name) {
        for (NotificationCategory category : values()) {
            if (category.name().equals(name)) {
                return category;
            }
        }
        return null;
    }
}