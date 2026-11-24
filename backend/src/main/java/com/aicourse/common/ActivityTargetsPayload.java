package com.aicourse.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动可见范围响应体 {@code {visibility, teachingClassIds}} 的唯一构造点。
 *
 * <p>阶段 2 DUP-05：考试卷（{@code ExamPaperTargetController}）与作业任务（{@code TaskController}）
 * 的 {@code /targets} 接口各自维护一份逐字相同的 payload 组装方法。两处分别演进时，
 * 前端拿到的字段名/类型会悄悄分叉（同名字段两种结构是契约类事故的常见来源）。
 * 现收敛到本类，字段名只在这里定义一次。</p>
 *
 * <p>返回 {@code Map} 而非新建 VO 是刻意的：这两个端点的既有契约就是裸对象，
 * 换类型会动前端契约，与「收敛实现」目标无关，不在本批次范围。</p>
 */
public final class ActivityTargetsPayload {

    private ActivityTargetsPayload() {
    }

    /**
     * @param visibility       归一后的可见范围（{@code ActivityVisibility.normalize} 的结果）
     * @param teachingClassIds 目标教学班 ID 列表，可为空列表
     * @return 字段顺序稳定的响应体（visibility → teachingClassIds）
     */
    public static Map<String, Object> of(String visibility, List<Long> teachingClassIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("visibility", visibility);
        payload.put("teachingClassIds", teachingClassIds);
        return payload;
    }
}
