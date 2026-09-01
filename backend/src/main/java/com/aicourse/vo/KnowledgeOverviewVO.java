package com.aicourse.vo;

import lombok.Data;

/**
 * 知识图谱概览统计 VO。
 * <p>
 * 由 {@code KnowledgeController.overview}（GET /api/knowledge/overview）产出：
 * 知识点总数、关系总数与课程过滤维度。字段名与历史 Map 返回保持一致，
 * 前端消费无需改动（2026-08-30 由 Map 裸返回收敛为结构化出参）。
 * </p>
 */
@Data
public class KnowledgeOverviewVO {

    /** 知识点总数（courseId 为空时为全平台口径） */
    private Long totalPoints;

    /** 关系总数（courseId 为空时为全平台口径） */
    private Long totalRelations;

    /** 课程过滤维度（可空：全平台口径） */
    private Long courseId;

    public KnowledgeOverviewVO() {
    }

    public KnowledgeOverviewVO(Long totalPoints, Long totalRelations, Long courseId) {
        this.totalPoints = totalPoints;
        this.totalRelations = totalRelations;
        this.courseId = courseId;
    }
}
