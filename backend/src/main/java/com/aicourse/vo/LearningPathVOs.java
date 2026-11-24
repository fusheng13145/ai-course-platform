package com.aicourse.vo;

import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习路线与节点出参（阶段 3 · Q-05：不再直返实体）。
 *
 * <p>两组字段均与实体当前序列化字段一一对应、零删减；该表不含敏感信息，
 * 裁剪只会让前端渲染断链，故本类只做分层边界，不做脱敏。
 */
public final class LearningPathVOs {

    private LearningPathVOs() {
    }

    /** 学习路线出参。 */
    @Data
    public static class PathVO {
        private Long id;
        private Long studentId;
        private Long courseId;
        private String pathName;
        private String description;
        private String stages;
        private String planDetail;
        private String dailyTasks;
        private String learningGoal;
        private Integer currentStage;
        private Integer totalStages;
        private BigDecimal completionRate;
        private String status;
        private LocalDateTime estimatedCompletionDate;
        private LocalDateTime actualCompletionDate;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        /** 实体 → 出参。 */
        public static PathVO from(LearningPath path) {
            if (path == null) {
                return null;
            }
            PathVO vo = new PathVO();
            vo.setId(path.getId());
            vo.setStudentId(path.getStudentId());
            vo.setCourseId(path.getCourseId());
            vo.setPathName(path.getPathName());
            vo.setDescription(path.getDescription());
            vo.setStages(path.getStages());
            vo.setPlanDetail(path.getPlanDetail());
            vo.setDailyTasks(path.getDailyTasks());
            vo.setLearningGoal(path.getLearningGoal());
            vo.setCurrentStage(path.getCurrentStage());
            vo.setTotalStages(path.getTotalStages());
            vo.setCompletionRate(path.getCompletionRate());
            vo.setStatus(path.getStatus());
            vo.setEstimatedCompletionDate(path.getEstimatedCompletionDate());
            vo.setActualCompletionDate(path.getActualCompletionDate());
            vo.setCreatedAt(path.getCreatedAt());
            vo.setUpdatedAt(path.getUpdatedAt());
            return vo;
        }

        /** 批量转换。 */
        public static List<PathVO> from(List<LearningPath> paths) {
            return paths == null ? List.of() : paths.stream().map(PathVO::from).toList();
        }
    }

    /** 路线节点出参。 */
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

        /** 实体 → 出参。 */
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

        /** 批量转换。 */
        public static List<NodeVO> from(List<LearningPathNode> nodes) {
            return nodes == null ? List.of() : nodes.stream().map(NodeVO::from).toList();
        }
    }
}
