package com.aicourse.vo;

import com.aicourse.entity.LearningPathNode;
import com.aicourse.entity.LearningRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习进度出参：路径节点与学习行为记录。
 *
 * <p>字段与实体一一对应、零删减。
 */
public final class LearningProgressVOs {

    private LearningProgressVOs() {
    }

    /** 学习路径节点出参。 */
    @Data
    public static class NodeVO {
        private Long id;
        private Long learningPathId;
        private Long knowledgePointId;
        private Long taskId;
        private Long chapterId;
        private String title;
        private String description;
        private String nodeType;
        private String status;
        private Integer orderIndex;
        private Integer estimatedTime;
        private Integer actualTime;
        private BigDecimal score;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static NodeVO from(LearningPathNode node) {
            if (node == null) {
                return null;
            }
            NodeVO vo = new NodeVO();
            vo.setId(node.getId());
            vo.setLearningPathId(node.getLearningPathId());
            vo.setKnowledgePointId(node.getKnowledgePointId());
            vo.setTaskId(node.getTaskId());
            vo.setChapterId(node.getChapterId());
            vo.setTitle(node.getTitle());
            vo.setDescription(node.getDescription());
            vo.setNodeType(node.getNodeType());
            vo.setStatus(node.getStatus());
            vo.setOrderIndex(node.getOrderIndex());
            vo.setEstimatedTime(node.getEstimatedTime());
            vo.setActualTime(node.getActualTime());
            vo.setScore(node.getScore());
            vo.setCompletedAt(node.getCompletedAt());
            vo.setCreatedAt(node.getCreatedAt());
            vo.setUpdatedAt(node.getUpdatedAt());
            return vo;
        }

        public static List<NodeVO> from(List<LearningPathNode> nodes) {
            return nodes == null ? List.of() : nodes.stream().map(NodeVO::from).toList();
        }
    }

    /** 学习行为记录出参。 */
    @Data
    public static class RecordVO {
        private Long id;
        private Long userId;
        private Long resourceId;
        private Long chapterId;
        private Long courseId;
        private String actionType;
        private Integer duration;
        private BigDecimal progress;
        private String metadata;
        private LocalDateTime createdAt;

        public static RecordVO from(LearningRecord record) {
            if (record == null) {
                return null;
            }
            RecordVO vo = new RecordVO();
            vo.setId(record.getId());
            vo.setUserId(record.getUserId());
            vo.setResourceId(record.getResourceId());
            vo.setChapterId(record.getChapterId());
            vo.setCourseId(record.getCourseId());
            vo.setActionType(record.getActionType());
            vo.setDuration(record.getDuration());
            vo.setProgress(record.getProgress());
            vo.setMetadata(record.getMetadata());
            vo.setCreatedAt(record.getCreatedAt());
            return vo;
        }

        public static List<RecordVO> from(List<LearningRecord> records) {
            return records == null ? List.of() : records.stream().map(RecordVO::from).toList();
        }
    }
}
