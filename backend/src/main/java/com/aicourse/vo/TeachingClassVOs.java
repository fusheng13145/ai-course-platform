package com.aicourse.vo;

import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.TeachingClassAdminClass;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 教学班出参：教学班本体 / 行政班来源。
 *
 * <p>两者字段均与实体一一对应、零删减。名单类端点不在这里做字段裁剪：
 * - `members` 复用 {@link CourseVOs.EnrollmentVO}（选课记录，无个人敏感列）；
 * - `rosterCandidates` 复用 {@link OrgVOs.AdminClassVO}（行政班元数据）。
 *
 * <p>真正的可见性约束是端点级 ACL（仅课程负责人 / 本班主讲 / 管理员），
 * 已在 `TeachingClassAccessControl` 与 `AdminScopeGuard` 里 fail-closed 实现，
 * 不靠序列化层裁剪。
 */
public final class TeachingClassVOs {

    private TeachingClassVOs() {
    }

    /** 教学班出参。 */
    @Data
    public static class ClassVO {
        private Long id;
        private Long courseId;
        private String name;
        private String code;
        private Long leadTeacherId;
        private Integer capacity;
        private String status;
        private Long createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        /** 实体 → 出参。 */
        public static ClassVO from(TeachingClass teachingClass) {
            if (teachingClass == null) {
                return null;
            }
            ClassVO vo = new ClassVO();
            vo.setId(teachingClass.getId());
            vo.setCourseId(teachingClass.getCourseId());
            vo.setName(teachingClass.getName());
            vo.setCode(teachingClass.getCode());
            vo.setLeadTeacherId(teachingClass.getLeadTeacherId());
            vo.setCapacity(teachingClass.getCapacity());
            vo.setStatus(teachingClass.getStatus());
            vo.setCreatedBy(teachingClass.getCreatedBy());
            vo.setCreatedAt(teachingClass.getCreatedAt());
            vo.setUpdatedAt(teachingClass.getUpdatedAt());
            return vo;
        }

        /** 批量转换。 */
        public static List<ClassVO> from(List<TeachingClass> classes) {
            return classes == null ? List.of() : classes.stream().map(ClassVO::from).toList();
        }
    }

    /** 教学班行政班来源出参。 */
    @Data
    public static class SourceVO {
        private Long id;
        private Long teachingClassId;
        private Long adminClassId;
        private String status;
        private Long importedBy;
        private LocalDateTime importedAt;
        private LocalDateTime createdAt;

        /** 实体 → 出参。 */
        public static SourceVO from(TeachingClassAdminClass source) {
            if (source == null) {
                return null;
            }
            SourceVO vo = new SourceVO();
            vo.setId(source.getId());
            vo.setTeachingClassId(source.getTeachingClassId());
            vo.setAdminClassId(source.getAdminClassId());
            vo.setStatus(source.getStatus());
            vo.setImportedBy(source.getImportedBy());
            vo.setImportedAt(source.getImportedAt());
            vo.setCreatedAt(source.getCreatedAt());
            return vo;
        }

        /** 批量转换。 */
        public static List<SourceVO> from(List<TeachingClassAdminClass> sources) {
            return sources == null ? List.of() : sources.stream().map(SourceVO::from).toList();
        }
    }
}
