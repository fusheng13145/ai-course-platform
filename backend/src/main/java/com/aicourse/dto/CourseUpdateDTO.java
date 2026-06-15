package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 更新课程请求DTO
 * 用于接收更新课程信息时的请求数据，所有字段均为可选
 */
@Schema(description = "更新课程请求")
public class CourseUpdateDTO {

    @Size(max = 100, message = "课程名称不能超过100个字符")
    @Schema(description = "课程名称", example = "Java程序设计基础（进阶版）")
    private String name;

    @Size(max = 30, message = "课程编号不能超过30个字符")
    @Schema(description = "课程编号", example = "CS101-V2")
    private String code;

    @Size(max = 2000, message = "课程描述不能超过2000个字符")
    @Schema(description = "课程描述", example = "本课程进阶版涵盖更多高级特性...")
    private String description;

    @Schema(description = "课程分类", example = "计算机科学")
    private String category;

    @DecimalMin(value = "0.1", message = "学分最小为0.1")
    @DecimalMax(value = "10.0", message = "学分最大为10.0")
    @Schema(description = "课程学分", example = "4.0")
    private BigDecimal credits;

    @Min(value = 1, message = "难度等级最低为1")
    @Max(value = 5, message = "难度等级最高为5")
    @Schema(description = "课程难度等级(1-5)", example = "3")
    private Integer difficulty;

    @Schema(description = "封面图片URL地址", example = "https://example.com/course-cover-v2.jpg")
    private String coverImage;

    @Min(value = 1, message = "最大选课人数至少为1")
    @Max(value = 10000, message = "最大选课人数不超过10000")
    @Schema(description = "最大选课人数", example = "300")
    private Integer maxStudents;

    @Pattern(regexp = "^(FREE|APPROVAL|CODE)$", message = "选课类型只能是FREE/APPROVAL/CODE")
    @Schema(description = "选课类型", example = "APPROVAL",
            allowableValues = {"FREE", "APPROVAL", "CODE"})
    private String enrollmentType;

    @Schema(description = "选课码", example = "JAVA2024FALL")
    private String enrollmentCode;

    @DecimalMin(value = "0", message = "价格不能为负数")
    @Schema(description = "课程价格（元）", example = "99.00")
    private BigDecimal price;

    @Schema(description = "课程标签列表", example = "[\"Java\", \"Spring\", \"进阶\"]")
    private List<String> tags;

    @Schema(description = "开课时间", example = "2024-09-01T08:00:00")
    private LocalDateTime startDate;

    @Schema(description = "结课时间", example = "2025-01-15T18:00:00")
    private LocalDateTime endDate;

    @Schema(description = "前置课程ID列表", example = "[1, 2, 3]")
    private List<Long> prerequisites;

    // ====================== Getter & Setter ======================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getCredits() {
        return credits;
    }

    public void setCredits(BigDecimal credits) {
        this.credits = credits;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public String getCoverImage() {
        return coverImage;
    }

    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }

    public Integer getMaxStudents() {
        return maxStudents;
    }

    public void setMaxStudents(Integer maxStudents) {
        this.maxStudents = maxStudents;
    }

    public String getEnrollmentType() {
        return enrollmentType;
    }

    public void setEnrollmentType(String enrollmentType) {
        this.enrollmentType = enrollmentType;
    }

    public String getEnrollmentCode() {
        return enrollmentCode;
    }

    public void setEnrollmentCode(String enrollmentCode) {
        this.enrollmentCode = enrollmentCode;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public List<Long> getPrerequisites() {
        return prerequisites;
    }

    public void setPrerequisites(List<Long> prerequisites) {
        this.prerequisites = prerequisites;
    }
}
