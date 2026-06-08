package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 错题记录实体类
 * 对应数据表：wrong_questions
 */
@TableName("wrong_questions")
public class WrongQuestion {

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
     * 题目ID
     */
    private Long questionId;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 错误答案
     */
    private String wrongAnswer;

    /**
     * 错误原因/备注
     */
    private String reason;

    /**
     * 是否已掌握
     */
    private Boolean mastered;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 练习次数
     */
    private Integer practiceCount;

    /**
     * 最近练习时间
     */
    private LocalDateTime lastPracticedAt;

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

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getWrongAnswer() {
        return wrongAnswer;
    }

    public void setWrongAnswer(String wrongAnswer) {
        this.wrongAnswer = wrongAnswer;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean getMastered() {
        return mastered;
    }

    public void setMastered(Boolean mastered) {
        this.mastered = mastered;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Integer getPracticeCount() {
        return practiceCount;
    }

    public void setPracticeCount(Integer practiceCount) {
        this.practiceCount = practiceCount;
    }

    public LocalDateTime getLastPracticedAt() {
        return lastPracticedAt;
    }

    public void setLastPracticedAt(LocalDateTime lastPracticedAt) {
        this.lastPracticedAt = lastPracticedAt;
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