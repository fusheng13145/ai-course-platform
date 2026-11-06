package com.aicourse.common;

/**
 * 选课来源常量（V14 / B-12-P-08 WP4–WP5）。
 *
 * <p>取值与 {@code course_enrollments.source} 列注释一致：</p>
 * <ul>
 *     <li>{@link #LEGACY}：V14 迁移对存量 512 条选课的标注；</li>
 *     <li>{@link #SELF_ENROLL}：学生自助选课；</li>
 *     <li>{@link #ADMIN_CLASS_IMPORT}：教师按行政班导入或重同步生成的名单。</li>
 * </ul>
 */
public final class EnrollmentSource {

    public static final String LEGACY = "LEGACY";
    public static final String SELF_ENROLL = "SELF_ENROLL";
    public static final String ADMIN_CLASS_IMPORT = "ADMIN_CLASS_IMPORT";

    private EnrollmentSource() {
    }
}
