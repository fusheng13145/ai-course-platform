package com.aicourse.service;

import java.util.List;

/**
 * 教学活动目标教学班服务（V15 / B-12-P-08 WP6）。
 *
 * <p>职责：归一化 {@code visibility}、校验目标教学班属于活动所属课程、维护
 * {@code task_teaching_classes} / {@code exam_teaching_classes} 目标行。</p>
 */
public interface ActivityTargetService {

    /**
     * 校验可见范围与目标教学班并返回归一化后的 visibility（非法值/空目标抛 400；不写库）。
     *
     * @param activityId   活动ID；新建时传 null
     * @param courseId     活动所属课程
     * @param visibility   原始可见范围，null 按 COURSE
     * @param classIds     目标教学班；null 表示沿用既有目标行（仅更新场景）
     * @param examPaper    true 表示试卷目标（沿用既有目标行时读 exam_teaching_classes）
     * @return 归一化后的 visibility
     */
    String normalizeAndValidate(Long activityId, Long courseId, String visibility, List<Long> classIds,
                                boolean examPaper);

    /**
     * 重写任务目标行；visibility=COURSE 时清空目标行。
     */
    void replaceTaskTargets(Long taskId, String visibility, List<Long> classIds);

    /**
     * 重写试卷目标行；visibility=COURSE 时清空目标行。
     */
    void replaceExamPaperTargets(Long examPaperId, String visibility, List<Long> classIds);

    /**
     * 任务目标教学班ID（升序）。
     */
    List<Long> taskTargetClassIds(Long taskId);

    /**
     * 试卷目标教学班ID（升序）。
     */
    List<Long> examPaperTargetClassIds(Long examPaperId);
}
