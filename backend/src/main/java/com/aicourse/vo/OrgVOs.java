package com.aicourse.vo;

import com.aicourse.entity.College;
import com.aicourse.entity.Major;
import com.aicourse.entity.AdminClass;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 组织架构出参（学院 / 专业 / 行政班）。
 *
 * <p>字段与实体一一对应、**无删减**：本类不是脱敏模型，而是分层边界模型——
 * Controller 出参从此不再直接返回 {@code @TableName} 实体，
 * 后续给表加列（例如内部备注、软删标记）不会再被动地出现在 HTTP 响应里。
 *
 * <p>刻意不加 {@code @JsonInclude(NON_NULL)}：实体映射默认输出 null 字段，
 * 加注解会改变已有响应形状（字段消失），属破坏性变更。
 */
public final class OrgVOs {

    private OrgVOs() {
    }

    /** 学院出参。 */
    @Data
    public static class CollegeVO {
        private Long id;
        private Long schoolId;
        private String code;
        private String name;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        /** 实体 → 出参。 */
        public static CollegeVO from(College college) {
            if (college == null) {
                return null;
            }
            CollegeVO vo = new CollegeVO();
            vo.setId(college.getId());
            vo.setSchoolId(college.getSchoolId());
            vo.setCode(college.getCode());
            vo.setName(college.getName());
            vo.setStatus(college.getStatus());
            vo.setCreatedAt(college.getCreatedAt());
            vo.setUpdatedAt(college.getUpdatedAt());
            return vo;
        }

        /** 批量转换，保持入参顺序，null 元素保留为 null。 */
        public static List<CollegeVO> from(List<College> colleges) {
            return colleges == null ? List.of() : colleges.stream().map(CollegeVO::from).toList();
        }
    }

    /** 专业出参。 */
    @Data
    public static class MajorVO {
        private Long id;
        private Long collegeId;
        private String code;
        private String name;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        /** 实体 → 出参。 */
        public static MajorVO from(Major major) {
            if (major == null) {
                return null;
            }
            MajorVO vo = new MajorVO();
            vo.setId(major.getId());
            vo.setCollegeId(major.getCollegeId());
            vo.setCode(major.getCode());
            vo.setName(major.getName());
            vo.setStatus(major.getStatus());
            vo.setCreatedAt(major.getCreatedAt());
            vo.setUpdatedAt(major.getUpdatedAt());
            return vo;
        }

        /** 批量转换，保持入参顺序，null 元素保留为 null。 */
        public static List<MajorVO> from(List<Major> majors) {
            return majors == null ? List.of() : majors.stream().map(MajorVO::from).toList();
        }
    }

    /** 行政班出参。 */
    @Data
    public static class AdminClassVO {
        private Long id;
        private Long schoolId;
        private Long collegeId;
        private Long majorId;
        private String name;
        private String code;
        private String gradeYear;
        private Long advisorTeacherId;
        private Integer studentCount;
        private Integer maxStudents;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        /** 实体 → 出参。 */
        public static AdminClassVO from(AdminClass adminClass) {
            if (adminClass == null) {
                return null;
            }
            AdminClassVO vo = new AdminClassVO();
            vo.setId(adminClass.getId());
            vo.setSchoolId(adminClass.getSchoolId());
            vo.setCollegeId(adminClass.getCollegeId());
            vo.setMajorId(adminClass.getMajorId());
            vo.setName(adminClass.getName());
            vo.setCode(adminClass.getCode());
            vo.setGradeYear(adminClass.getGradeYear());
            vo.setAdvisorTeacherId(adminClass.getAdvisorTeacherId());
            vo.setStudentCount(adminClass.getStudentCount());
            vo.setMaxStudents(adminClass.getMaxStudents());
            vo.setStatus(adminClass.getStatus());
            vo.setCreatedAt(adminClass.getCreatedAt());
            vo.setUpdatedAt(adminClass.getUpdatedAt());
            return vo;
        }

        /** 批量转换，保持入参顺序，null 元素保留为 null。 */
        public static List<AdminClassVO> from(List<AdminClass> adminClasses) {
            return adminClasses == null ? List.of() : adminClasses.stream().map(AdminClassVO::from).toList();
        }
    }
}
