package com.aicourse.dto;

import com.aicourse.entity.KnowledgePoint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 知识点创建／更新入参（入参收实体加固）。
 *
 * <p><b>为什么不直接绑 {@code KnowledgePoint} 实体</b>：{@code id}／{@code createdAt}／
 * {@code updatedAt} 是服务端字段；{@code courseId} 决定知识点归属课程，更新时若由客户端传入即可把
 * 知识点"搬"到别的课程（绕过课程管理权限判据）。因此更新时 {@code courseId} 一律以既有知识点为准。</p>
 */
@Schema(name = "KnowledgePointSaveDTO", description = "知识点创建／更新入参；id 与时间戳不接受客户端传入")
@Data
public class KnowledgePointSaveDTO {

    @NotBlank(message = "知识点名称不能为空")
    @Schema(description = "知识点名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "知识点描述")
    private String description;

    @Schema(description = "所属课程ID；仅在创建时生效，更新忽略（以既有知识点归属为准）", example = "1")
    private Long courseId;

    @Schema(description = "父级知识点ID；0 或空表示顶级", example = "0")
    private Long parentId;

    @Schema(description = "难度等级", example = "medium")
    private String difficultyLevel;

    @Schema(description = "重要程度（数值越大优先级越高）", example = "5")
    private Integer importance;

    @Schema(description = "预计学习时长（分钟）", example = "30")
    private Integer estimatedTime;

    @Schema(description = "知识点类型", example = "CONCEPT")
    private String pointType;

    @Schema(description = "同层级排序序号", example = "0")
    private Integer orderIndex;

    public KnowledgePoint toEntity() {
        KnowledgePoint point = new KnowledgePoint();
        applyTo(point);
        return point;
    }

    public void applyTo(KnowledgePoint point) {
        point.setName(name);
        point.setDescription(description);
        point.setCourseId(courseId);
        point.setParentId(parentId);
        point.setDifficultyLevel(difficultyLevel);
        point.setImportance(importance);
        point.setEstimatedTime(estimatedTime);
        point.setPointType(pointType);
        point.setOrderIndex(orderIndex);
    }
}
