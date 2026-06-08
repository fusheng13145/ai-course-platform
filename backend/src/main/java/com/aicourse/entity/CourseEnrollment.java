package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程选课记录实体类
 * 对应数据表：course_enrollments
 */
@TableName("course_enrollments")
public class CourseEnrollment {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 选课审核状态
     */
    private String approvalStatus;

    /**
     * 是否有效
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * 总成绩
     */
    private BigDecimal finalScore;

    /**
     * 考勤成绩
     */
    private BigDecimal attendanceScore;

    /**
     * 作业成绩
     */
    private BigDecimal assignmentScore;

    /**
     * 考试成绩
     */
    private BigDecimal examScore;

    /**
     * 学习进度
     */
    private BigDecimal progress;

    /**
     * 累计学习时长（单位：分钟）
     */
    private Integer totalStudyTime;

    /**
     * 审核时间
     */
    private LocalDateTime approvalTime;

    /**
     * 审核人ID
     */
    private Long approvedBy;

    /**
     * 审核意见
     */
    private String approvalComment;

    /**
     * 选课时间
     */
    private LocalDateTime enrolledAt;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间（自动填充）
     */
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

    public void setIsActive(Boolean active) {
        isActive = active;
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