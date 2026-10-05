package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 知识学习路径规划请求 DTO。
 * <p>
 * 对应 PRD 知识图谱模块 {@code POST /api/knowledge/path/plan}（起点到目标的最优路径）。
 * 业务委托 {@code KnowledgeGraphService.planLearningPath} 执行 BFS 多跳路径规划，
 * 由图谱引擎（团队另一成员，Python 8001）或消费侧本地 BFS 兜底完成。
 */
@Schema(description = "知识学习路径规划请求")
@Data
public class KnowledgePathPlanDTO {

    @NotNull(message = "起点知识点ID不能为空")
    @Schema(description = "起点知识点ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceId;

    @NotNull(message = "目标知识点ID不能为空")
    @Schema(description = "目标知识点ID", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetId;

    @Schema(description = "最大搜索跳数（1=直接邻居，2=邻居的邻居）；留空/非正数表示不限，返回两点间真实最短路径",
            example = "3")
    private Integer maxHops;
}
