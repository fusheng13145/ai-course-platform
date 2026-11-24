package com.aicourse.vo;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.entity.Note;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识面出参：知识点 / 知识点关系 / 笔记。
 *
 * <p>三组字段均与实体一一对应、零删减。三张表都是纯内容模型，无个人敏感列；
 * `Note.userId` 是作者主键，笔记端点本身按 owner 校验（`getOwned` / `updateOwned`），
 * 行级可见性由服务层兜住。
 */
public final class KnowledgeVOs {

    private KnowledgeVOs() {
    }

    /** 知识点出参。 */
    @Data
    public static class PointVO {
        private Long id;
        private String name;
        private String description;
        private Long courseId;
        private Long parentId;
        private String difficultyLevel;
        private Integer importance;
        private Integer estimatedTime;
        private String pointType;
        private Integer orderIndex;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static PointVO from(KnowledgePoint point) {
            if (point == null) {
                return null;
            }
            PointVO vo = new PointVO();
            vo.setId(point.getId());
            vo.setName(point.getName());
            vo.setDescription(point.getDescription());
            vo.setCourseId(point.getCourseId());
            vo.setParentId(point.getParentId());
            vo.setDifficultyLevel(point.getDifficultyLevel());
            vo.setImportance(point.getImportance());
            vo.setEstimatedTime(point.getEstimatedTime());
            vo.setPointType(point.getPointType());
            vo.setOrderIndex(point.getOrderIndex());
            vo.setCreatedAt(point.getCreatedAt());
            vo.setUpdatedAt(point.getUpdatedAt());
            return vo;
        }

        public static List<PointVO> from(List<KnowledgePoint> points) {
            return points == null ? List.of() : points.stream().map(PointVO::from).toList();
        }
    }

    /** 知识点关系出参。 */
    @Data
    public static class RelationVO {
        private Long id;
        private Long sourceId;
        private Long targetId;
        private String relationType;
        /** V35 新增：上游原始谓词，隔离时用于回溯。 */
        private String rawRelationType;
        /** V35 新增：是否被隔离（错位边）。 */
        private Boolean isQuarantined;
        private BigDecimal strength;
        private String description;
        private LocalDateTime createdAt;

        public static RelationVO from(KnowledgePointRelation relation) {
            if (relation == null) {
                return null;
            }
            RelationVO vo = new RelationVO();
            vo.setId(relation.getId());
            vo.setSourceId(relation.getSourceId());
            vo.setTargetId(relation.getTargetId());
            vo.setRelationType(relation.getRelationType());
            vo.setRawRelationType(relation.getRawRelationType());
            vo.setIsQuarantined(relation.getIsQuarantined());
            vo.setStrength(relation.getStrength());
            vo.setDescription(relation.getDescription());
            vo.setCreatedAt(relation.getCreatedAt());
            return vo;
        }

        public static List<RelationVO> from(List<KnowledgePointRelation> relations) {
            return relations == null ? List.of() : relations.stream().map(RelationVO::from).toList();
        }
    }

    /** 笔记出参。 */
    @Data
    public static class NoteVO {
        private Long id;
        private Long userId;
        private Long courseId;
        private Long chapterId;
        private Long knowledgePointId;
        private String title;
        private String content;
        private Integer isFavorite;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static NoteVO from(Note note) {
            if (note == null) {
                return null;
            }
            NoteVO vo = new NoteVO();
            vo.setId(note.getId());
            vo.setUserId(note.getUserId());
            vo.setCourseId(note.getCourseId());
            vo.setChapterId(note.getChapterId());
            vo.setKnowledgePointId(note.getKnowledgePointId());
            vo.setTitle(note.getTitle());
            vo.setContent(note.getContent());
            vo.setIsFavorite(note.getIsFavorite());
            vo.setCreatedAt(note.getCreatedAt());
            vo.setUpdatedAt(note.getUpdatedAt());
            return vo;
        }

        public static List<NoteVO> from(List<Note> notes) {
            return notes == null ? List.of() : notes.stream().map(NoteVO::from).toList();
        }
    }
}
