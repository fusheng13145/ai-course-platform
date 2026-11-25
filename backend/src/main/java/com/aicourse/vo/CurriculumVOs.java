package com.aicourse.vo;

import com.aicourse.entity.CourseDefinition;
import com.aicourse.entity.GradeItem;
import com.aicourse.entity.GradePolicy;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程定义与成绩方案出参。
 *
 * <p>字段与实体一一对应、零删减。`components` 保持原始 JSON 字符串下发
 * （前端已有解析逻辑），不做服务端展开。
 */
public final class CurriculumVOs {

    private CurriculumVOs() {
    }

    /** 课程定义出参。 */
    @Data
    public static class CourseDefinitionVO {
        private Long id;
        private String code;
        private String name;
        private String description;
        private BigDecimal credits;
        private Integer hours;
        private String nature;
        private Long collegeId;
        private String category;
        private String difficulty;
        private String prerequisite;
        private String coverImage;
        private String syllabusUrl;
        private String tags;
        private String status;
        private Long createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static CourseDefinitionVO from(CourseDefinition definition) {
            if (definition == null) {
                return null;
            }
            CourseDefinitionVO vo = new CourseDefinitionVO();
            vo.setId(definition.getId());
            vo.setCode(definition.getCode());
            vo.setName(definition.getName());
            vo.setDescription(definition.getDescription());
            vo.setCredits(definition.getCredits());
            vo.setHours(definition.getHours());
            vo.setNature(definition.getNature());
            vo.setCollegeId(definition.getCollegeId());
            vo.setCategory(definition.getCategory());
            vo.setDifficulty(definition.getDifficulty());
            vo.setPrerequisite(definition.getPrerequisite());
            vo.setCoverImage(definition.getCoverImage());
            vo.setSyllabusUrl(definition.getSyllabusUrl());
            vo.setTags(definition.getTags());
            vo.setStatus(definition.getStatus());
            vo.setCreatedBy(definition.getCreatedBy());
            vo.setCreatedAt(definition.getCreatedAt());
            vo.setUpdatedAt(definition.getUpdatedAt());
            return vo;
        }

        public static List<CourseDefinitionVO> from(List<CourseDefinition> definitions) {
            return definitions == null ? List.of() : definitions.stream().map(CourseDefinitionVO::from).toList();
        }
    }

    /** 成绩方案出参。 */
    @Data
    public static class GradePolicyVO {
        private Long id;
        private Long courseId;
        private String name;

        /**
         * 维度权重：库内是 JSON 字符串，**HTTP 出参为对象数组**。
         *
         * <p>沿用实体上的 `GradeComponentsJson` 序列化器，保证 `$.data.components`
         * 仍是数组而不是被转义过的字符串——这是前端成绩方案编辑页的既有契约，
         * 换 VO 时若丢掉这组注解会把数组退化成字符串，直接打断权重编辑。
         */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.aicourse.common.GradeComponentsJson.Serializer.class,
                nullsUsing = com.aicourse.common.GradeComponentsJson.NullSerializer.class)
        private String components;

        private Long creatorId;
        private Boolean isDefault;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static GradePolicyVO from(GradePolicy policy) {
            if (policy == null) {
                return null;
            }
            GradePolicyVO vo = new GradePolicyVO();
            vo.setId(policy.getId());
            vo.setCourseId(policy.getCourseId());
            vo.setName(policy.getName());
            vo.setComponents(policy.getComponents());
            vo.setCreatorId(policy.getCreatorId());
            vo.setIsDefault(policy.getIsDefault());
            vo.setCreatedAt(policy.getCreatedAt());
            vo.setUpdatedAt(policy.getUpdatedAt());
            return vo;
        }
    }

    /** 成绩项出参。 */
    @Data
    public static class GradeItemVO {
        private Long id;
        private Long policyId;
        private Long studentId;
        private String componentKey;
        private BigDecimal score;
        private BigDecimal rawScore;
        private Long sourceId;
        private String remark;
        private Long graderId;
        private Long teachingClassId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static GradeItemVO from(GradeItem item) {
            if (item == null) {
                return null;
            }
            GradeItemVO vo = new GradeItemVO();
            vo.setId(item.getId());
            vo.setPolicyId(item.getPolicyId());
            vo.setStudentId(item.getStudentId());
            vo.setComponentKey(item.getComponentKey());
            vo.setScore(item.getScore());
            vo.setRawScore(item.getRawScore());
            vo.setSourceId(item.getSourceId());
            vo.setRemark(item.getRemark());
            vo.setGraderId(item.getGraderId());
            vo.setTeachingClassId(item.getTeachingClassId());
            vo.setCreatedAt(item.getCreatedAt());
            vo.setUpdatedAt(item.getUpdatedAt());
            return vo;
        }
    }
}
