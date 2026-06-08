package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 查重检测记录实体
 * 存储作业/文稿的查重任务信息、相似度结果、明细及异常日志
 */
@TableName("plagiarism_checks")
public class PlagiarismCheck {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联任务ID
     */
    private Long taskId;

    /**
     * 关联提交记录ID
     */
    private Long submissionId;

    /**
     * 提交学生ID
     */
    private Long studentId;

    /**
     * 查重类型
     */
    private String checkType;

    /**
     * 检测状态（待检测/检测中/已完成/检测失败）
     */
    private String status;

    /**
     * 整体相似度分值
     */
    private BigDecimal similarityScore;

    /**
     * 高相似片段数量
     */
    private Integer highSimilarityCount;

    /**
     * 检测结果摘要
     */
    private String resultSummary;

    /**
     * 检测详细数据（JSON格式存储）
     */
    private String checkDetail;

    /**
     * 检测完成时间
     */
    private LocalDateTime checkedAt;

    /**
     * 检测失败异常信息
     */
    private String errorMessage;

    /**
     * 记录创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getCheckType() {
        return checkType;
    }

    public void setCheckType(String checkType) {
        this.checkType = checkType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(BigDecimal similarityScore) {
        this.similarityScore = similarityScore;
    }

    public Integer getHighSimilarityCount() {
        return highSimilarityCount;
    }

    public void setHighSimilarityCount(Integer highSimilarityCount) {
        this.highSimilarityCount = highSimilarityCount;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public String getCheckDetail() {
        return checkDetail;
    }

    public void setCheckDetail(String checkDetail) {
        this.checkDetail = checkDetail;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}