package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("course_enrollments")
public class CourseEnrollment {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("student_id")
    private Long studentId;
    @TableField("course_id")
    private Long courseId;
    @TableField("approval_status")
    private String approvalStatus;
    
    @TableField("is_active")
    private Boolean isActive;
    
    @TableField("final_score")
    private BigDecimal finalScore;
    @TableField("attendance_score")
    private BigDecimal attendanceScore;
    @TableField("assignment_score")
    private BigDecimal assignmentScore;
    @TableField("exam_score")
    private BigDecimal examScore;
    private BigDecimal progress;
    @TableField("total_study_time")
    private Integer totalStudyTime;
    @TableField("approval_time")
    private LocalDateTime approvalTime;
    @TableField("approved_by")
    private Long approvedBy;
    @TableField("approval_comment")
    private String approvalComment;
    @TableField("enrolled_at")
    private LocalDateTime enrolledAt;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public BigDecimal getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(BigDecimal finalScore) {
        this.finalScore = finalScore;
    }

    public BigDecimal getAttendanceScore() {
        return attendanceScore;
    }

    public void setAttendanceScore(BigDecimal attendanceScore) {
        this.attendanceScore = attendanceScore;
    }

    public BigDecimal getAssignmentScore() {
        return assignmentScore;
    }

    public void setAssignmentScore(BigDecimal assignmentScore) {
        this.assignmentScore = assignmentScore;
    }

    public BigDecimal getExamScore() {
        return examScore;
    }

    public void setExamScore(BigDecimal examScore) {
        this.examScore = examScore;
    }

    public BigDecimal getProgress() {
        return progress;
    }

    public void setProgress(BigDecimal progress) {
        this.progress = progress;
    }

    public Integer getTotalStudyTime() {
        return totalStudyTime;
    }

    public void setTotalStudyTime(Integer totalStudyTime) {
        this.totalStudyTime = totalStudyTime;
    }

    public LocalDateTime getApprovalTime() {
        return approvalTime;
    }

    public void setApprovalTime(LocalDateTime approvalTime) {
        this.approvalTime = approvalTime;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getApprovalComment() {
        return approvalComment;
    }

    public void setApprovalComment(String approvalComment) {
        this.approvalComment = approvalComment;
    }

    public LocalDateTime getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(LocalDateTime enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
