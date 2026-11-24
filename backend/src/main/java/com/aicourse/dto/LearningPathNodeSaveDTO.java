package com.aicourse.dto;

import com.aicourse.entity.LearningPathNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 学习路径节点创建／更新入参（入参收实体加固）。
 *
 * <p><b>为什么不直接绑 {@code LearningPathNode} 实体</b>：实体直绑时客户端可以自带
 * {@code learningPathId}（把节点挂到别人的路径下＝跨人越权）、{@code status}／{@code score}／
 * {@code actualTime}／{@code completedAt}（绕过 start/complete/skip 直接伪造完成结论），
 * 以及 {@code id}／{@code createdAt}。DTO 只暴露节点的编排字段；归属由 {@code /{id}/nodes} 的路径 ID
 * 决定，完成状态由 {@code LearningPathNodeController} 的专用端点决定。</p>
 *
 * <p>更新是局部语义：字段为 {@code null} 表示「不改」。</p>
 */
@Schema(name = "LearningPathNodeSaveDTO", description = "学习路径节点创建／更新入参；learningPathId/完成状态/主键不接受客户端传入")
@Data
public class LearningPathNodeSaveDTO {

    @Schema(description = "知识点ID", example = "12")
    private Long knowledgePointId;

    @Schema(description = "任务ID", example = "34")
    private Long taskId;

    @Schema(description = "章节ID", example = "56")
    private Long chapterId;

    @Schema(description = "节点标题", example = "三次握手流程")
    private String title;

    @Schema(description = "节点说明")
    private String description;

    @Schema(description = "节点类型", example = "KNOWLEDGE")
    private String nodeType;

    @Schema(description = "排序序号", example = "1")
    private Integer orderIndex;

    @Schema(description = "预计用时（分钟）", example = "30")
    private Integer estimatedTime;

    public LearningPathNode toEntity() {
        LearningPathNode node = new LearningPathNode();
        applyTo(node);
        return node;
    }

    public void applyTo(LearningPathNode node) {
        node.setKnowledgePointId(knowledgePointId);
        node.setTaskId(taskId);
        node.setChapterId(chapterId);
        node.setTitle(title);
        node.setDescription(description);
        node.setNodeType(nodeType);
        node.setOrderIndex(orderIndex);
        node.setEstimatedTime(estimatedTime);
    }
}
