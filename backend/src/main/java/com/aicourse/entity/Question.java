package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 题库题目实体
 * 存储各类题型题干、选项、答案、解析、难度、分值及使用统计等信息
 */
@TableName("questions")
public class Question {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 题目类型（单选/多选/判断/简答等）
     */
    private String type;

    /**
     * 题干内容
     */
    private String content;

    /**
     * 选项集合（JSON格式存储）
     */
    private String options;

    /**
     * 正确答案（多答案按规则分隔/JSON存储）
     */
    private String correctAnswers;

    /**
     * 难度等级
     */
    private String difficulty;

    /**
     * 题目分值
     */
    private BigDecimal score;

    /**
     * 关联知识点集合（JSON/逗号分隔）
     */
    private String knowledgePoints;

    /**
     * 题目解析
     */
    private String explanation;

    /**
     * 被试卷引用次数
     */
    private Integer usageCount;

    /**
     * 答题正确率
     */
    private BigDecimal correctRate;

    /**
     * 所属课程ID
     */
    private Long courseId;

    /**
     * 创建人用户ID
     */
    private Long createdBy;

    /**
     * 是否启用 0-禁用 1-启用
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * 记录创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 记录更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String getCorrectAnswers() {
        return correctAnswers;
    }

    public void setCorrectAnswers(String correctAnswers) {
        this.correctAnswers = correctAnswers;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(String knowledgePoints) {
        this.knowledgePoints = knowledgePoints;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Integer getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Integer usageCount) {
        this.usageCount = usageCount;
    }

    public BigDecimal getCorrectRate() {
        return correctRate;
    }

    public void setCorrectRate(BigDecimal correctRate) {
        this.correctRate = correctRate;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
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