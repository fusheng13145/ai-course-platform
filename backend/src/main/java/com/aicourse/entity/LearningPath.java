package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("learning_paths")
public class LearningPath {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long courseId;
    private String pathName;
    private String description;
    private String stages;
    private String planDetail;
    private String dailyTasks;
    private String learningGoal;
    private Integer currentStage;
    private Integer totalStages;
    private BigDecimal completionRate;
    private String status;
    private LocalDateTime estimatedCompletionDate;
    private LocalDateTime actualCompletionDate;
    
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

    public String getPathName() {
        return pathName;
    }

    public void setPathName(String pathName) {
        this.pathName = pathName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStages() {
        return stages;
    }

    public void setStages(String stages) {
        this.stages = stages;
    }

    public String getPlanDetail() {
        return planDetail;
    }

    public void setPlanDetail(String planDetail) {
        this.planDetail = planDetail;
    }

    public String getDailyTasks() {
        return dailyTasks;
    }

    public void setDailyTasks(String dailyTasks) {
        this.dailyTasks = dailyTasks;
    }

    public String getLearningGoal() {
        return learningGoal;
    }

    public void setLearningGoal(String learningGoal) {
        this.learningGoal = learningGoal;
    }

    public Integer getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(Integer currentStage) {
        this.currentStage = currentStage;
    }

    public Integer getTotalStages() {
        return totalStages;
    }

    public void setTotalStages(Integer totalStages) {
        this.totalStages = totalStages;
    }

    public BigDecimal getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(BigDecimal completionRate) {
        this.completionRate = completionRate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getEstimatedCompletionDate() {
        return estimatedCompletionDate;
    }

    public void setEstimatedCompletionDate(LocalDateTime estimatedCompletionDate) {
        this.estimatedCompletionDate = estimatedCompletionDate;
    }

    public LocalDateTime getActualCompletionDate() {
        return actualCompletionDate;
    }

    public void setActualCompletionDate(LocalDateTime actualCompletionDate) {
        this.actualCompletionDate = actualCompletionDate;
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
