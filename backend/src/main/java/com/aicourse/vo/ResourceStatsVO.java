package com.aicourse.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

/**
 * 课程资源统计 VO。
 * <p>
 * 由 {@code ResourceController.getResourceStats}（GET /api/resources/stats/course/{courseId}）产出：
 * 资源总数、总体积与按文件类型的分布。字段名与历史 Map 返回保持一致，
 * 前端消费无需改动（2026-08-30 由 Map 裸返回收敛为结构化出参）。
 * </p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceStatsVO {

    /** 资源总数 */
    private Long totalCount;

    /** 资源总体积（字节） */
    private Long totalSize;

    /** 按文件类型聚合的数量分布 */
    private Map<String, Long> typeDistribution;

    public ResourceStatsVO() {
    }

    public ResourceStatsVO(Long totalCount, Long totalSize, Map<String, Long> typeDistribution) {
        this.totalCount = totalCount;
        this.totalSize = totalSize;
        this.typeDistribution = typeDistribution;
    }
}
