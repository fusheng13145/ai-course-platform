package com.aicourse.vo;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.CourseApprovalRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程域出参：课程 / 选课记录 / 审批流水。
 *
 * <p>三组字段均与实体当前序列化字段一一对应、零删减（三张表不含个人敏感信息，
 * 裁剪会让前端渲染断链）。本类只做分层边界：以后给表加内部列，不会自动漏进 HTTP 响应。
 */
public final class CourseVOs {

    private CourseVOs() {
    }

    /** 课程出参。 */
    @Data
    public static class CourseVO {
        private Long id;
        private String name;
        private String code;
        private String description;
        private BigDecimal credits;
        private Long teacherId;
        private Long collegeId;
        private String semester;
        private Integer year;
        private String status;
        private String approvalStatus;
        private String enrollmentType;
        private Integer maxStudents;
        private Boolean isActive;
        private String category;
        private String difficulty;
        private BigDecimal price;
        private String tags;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String coverImage;
        private LocalDateTime approvalTime;
        private Long approvedBy;
        private String approvalComment;
        private String syllabusUrl;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Long semesterId;
        private Boolean questionPracticeEnabled;
        private Long definitionId;
        private Integer plannedSections;
        private LocalDateTime completedAt;

        /** 实体 → 出参。 */
        public static CourseVO from(Course course) {
            if (course == null) {
                return null;
            }
            CourseVO vo = new CourseVO();
            vo.setId(course.getId());
            vo.setName(course.getName());
            vo.setCode(course.getCode());
            vo.setDescription(course.getDescription());
            vo.setCredits(course.getCredits());
            vo.setTeacherId(course.getTeacherId());
            vo.setCollegeId(course.getCollegeId());
            vo.setSemester(course.getSemester());
            vo.setYear(course.getYear());
            vo.setStatus(course.getStatus());
            vo.setApprovalStatus(course.getApprovalStatus());
            vo.setEnrollmentType(course.getEnrollmentType());
            vo.setMaxStudents(course.getMaxStudents());
            vo.setIsActive(course.getIsActive());
            vo.setCategory(course.getCategory());
            vo.setDifficulty(course.getDifficulty());
            vo.setPrice(course.getPrice());
            vo.setTags(course.getTags());
            vo.setStartDate(course.getStartDate());
            vo.setEndDate(course.getEndDate());
            vo.setCoverImage(course.getCoverImage());
            vo.setApprovalTime(course.getApprovalTime());
            vo.setApprovedBy(course.getApprovedBy());
            vo.setApprovalComment(course.getApprovalComment());
            vo.setSyllabusUrl(course.getSyllabusUrl());
            vo.setCreatedAt(course.getCreatedAt());
            vo.setUpdatedAt(course.getUpdatedAt());
            vo.setSemesterId(course.getSemesterId());
            vo.setQuestionPracticeEnabled(course.getQuestionPracticeEnabled());
            vo.setDefinitionId(course.getDefinitionId());
            vo.setPlannedSections(course.getPlannedSections());
            vo.setCompletedAt(course.getCompletedAt());
            return vo;
        }

        /** 批量转换。 */
        public static List<CourseVO> from(List<Course> courses) {
            return courses == null ? List.of() : courses.stream().map(CourseVO::from).toList();
        }
    }

    /** 选课记录出参。 */
    @Data
    public static class EnrollmentVO {
        private Long id;
        private Long studentId;
        private Long courseId;
        private Long teachingClassId;
        private String source;
        private Long sourceAdminClassId;
        private String approvalStatus;
        private Boolean isActive;
        private BigDecimal finalScore;
        private BigDecimal attendanceScore;
        private BigDecimal assignmentScore;
        private BigDecimal examScore;
        private BigDecimal progress;
        private Integer totalStudyTime;
        private LocalDateTime approvalTime;
        private Long approvedBy;
        private String approvalComment;
        private LocalDateTime enrolledAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        /** 实体 → 出参。 */
        public static EnrollmentVO from(CourseEnrollment enrollment) {
            if (enrollment == null) {
                return null;
            }
            EnrollmentVO vo = new EnrollmentVO();
            vo.setId(enrollment.getId());
            vo.setStudentId(enrollment.getStudentId());
            vo.setCourseId(enrollment.getCourseId());
            vo.setTeachingClassId(enrollment.getTeachingClassId());
            vo.setSource(enrollment.getSource());
            vo.setSourceAdminClassId(enrollment.getSourceAdminClassId());
            vo.setApprovalStatus(enrollment.getApprovalStatus());
            vo.setIsActive(enrollment.getIsActive());
            vo.setFinalScore(enrollment.getFinalScore());
            vo.setAttendanceScore(enrollment.getAttendanceScore());
            vo.setAssignmentScore(enrollment.getAssignmentScore());
            vo.setExamScore(enrollment.getExamScore());
            vo.setProgress(enrollment.getProgress());
            vo.setTotalStudyTime(enrollment.getTotalStudyTime());
            vo.setApprovalTime(enrollment.getApprovalTime());
            vo.setApprovedBy(enrollment.getApprovedBy());
            vo.setApprovalComment(enrollment.getApprovalComment());
            vo.setEnrolledAt(enrollment.getEnrolledAt());
            vo.setCreatedAt(enrollment.getCreatedAt());
            vo.setUpdatedAt(enrollment.getUpdatedAt());
            return vo;
        }

        /** 批量转换。 */
        public static List<EnrollmentVO> from(List<CourseEnrollment> enrollments) {
            return enrollments == null ? List.of() : enrollments.stream().map(EnrollmentVO::from).toList();
        }
    }

    /** 审批流水出参。 */
    @Data
    public static class ApprovalRecordVO {
        private Long id;
        private Long courseId;
        private Long operatorId;
        private String action;
        private String fromStatus;
        private String toStatus;
        private String comment;
        private LocalDateTime createdAt;

        /** 实体 → 出参。 */
        public static ApprovalRecordVO from(CourseApprovalRecord record) {
            if (record == null) {
                return null;
            }
            ApprovalRecordVO vo = new ApprovalRecordVO();
            vo.setId(record.getId());
            vo.setCourseId(record.getCourseId());
            vo.setOperatorId(record.getOperatorId());
            vo.setAction(record.getAction());
            vo.setFromStatus(record.getFromStatus());
            vo.setToStatus(record.getToStatus());
            vo.setComment(record.getComment());
            vo.setCreatedAt(record.getCreatedAt());
            return vo;
        }

        /** 批量转换。 */
        public static List<ApprovalRecordVO> from(List<CourseApprovalRecord> records) {
            return records == null ? List.of() : records.stream().map(ApprovalRecordVO::from).toList();
        }
    }
}
