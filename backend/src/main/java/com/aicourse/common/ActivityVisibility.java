package com.aicourse.common;

import com.aicourse.exception.BusinessException;

/**
 * 教学活动的可见范围（B-12 / P-08 · WP6）。
 *
 * <p>口径（拆包方案 5A）：</p>
 * <ul>
 *     <li>{@link #COURSE}：课程级，默认值；该课程全部教学班（含后续新建教学班）可见；</li>
 *     <li>{@link #CLASSES}：教学班级；仅 {@code task_teaching_classes} / {@code exam_teaching_classes}
 *     中登记的目标教学班可见，且必须至少登记 1 个目标教学班。</li>
 * </ul>
 */
public final class ActivityVisibility {

    public static final String COURSE = "COURSE";

    public static final String CLASSES = "CLASSES";

    private ActivityVisibility() {
    }

    /**
     * 归一化可见范围：null/空白按默认 COURSE；其余去空格转大写，非法值返回 null。
     */
    public static String normalizeOrNull(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return COURSE;
        }
        String normalized = visibility.trim().toUpperCase();
        return COURSE.equals(normalized) || CLASSES.equals(normalized) ? normalized : null;
    }

    /**
     * 归一化可见范围，非法值抛 400。
     */
    public static String normalize(String visibility) {
        String normalized = normalizeOrNull(visibility);
        if (normalized == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST,
                    "可见范围只能为 COURSE 或 CLASSES：" + visibility);
        }
        return normalized;
    }
}
