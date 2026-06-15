package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 题目搜索查询DTO
 * 用于接收前端搜索题库时的筛选和排序参数
 */
@Schema(description = "题目搜索查询参数")
public class QuestionSearchDTO extends PageQueryDTO {

    @Schema(description = "所属课程ID", example = "1")
    private Long courseId;

    @Schema(description = "搜索关键词（匹配题干/解析）", example = "多态")
    private String keyword;

    @Pattern(regexp = "^(SINGLE_CHOICE|MULTIPLE_CHOICE|TRUE_FALSE|SHORT_ANSWER|FILL_BLANK)$",
            message = "题目类型不合法")
    @Schema(description = "题目类型筛选", example = "SINGLE_CHOICE",
            allowableValues = {"SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER", "FILL_BLANK"})
    private String type;

    @Min(value = 1, message = "最低难度不能小于1")
    @Max(value = 5, message = "最低难度不能大于5")
    @Schema(description = "最低难度等级(1-5)", example = "1")
    private Integer difficultyMin;

    @Min(value = 1, message = "最高难度不能小于1")
    @Max(value = 5, message = "最高难度不能大于5")
    @Schema(description = "最高难度等级(1-5)", example = "3")
    private Integer difficultyMax;

    @Schema(description = "知识点ID筛选", example = "101")
    private Long knowledgePointId;

    @Schema(description = "标签筛选（逗号分隔或列表）", example = "基础语法,常考")
    private String tags;

    @Pattern(regexp = "^(CREATED|DIFFICULTY|USAGE|CORRECT_RATE)$",
            message = "排序方式不合法")
    @Schema(description = "排序方式", example = "DIFFICULTY",
            allowableValues = {"CREATED", "DIFFICULTY", "USAGE", "CORRECT_RATE"})
    private String sortBy = "CREATED";

    // ====================== Getter & Setter ======================

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getDifficultyMin() {
        return difficultyMin;
    }

    public void setDifficultyMin(Integer difficultyMin) {
        this.difficultyMin = difficultyMin;
    }

    public Integer getDifficultyMax() {
        return difficultyMax;
    }

    public void setDifficultyMax(Integer difficultyMax) {
        this.difficultyMax = difficultyMax;
    }

    public Long getKnowledgePointId() {
        return knowledgePointId;
    }

    public void setKnowledgePointId(Long knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    @Override
    public String getSortBy() {
        return sortBy;
    }

    @Override
    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }
}
