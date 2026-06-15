package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建课程请求DTO
 * 用于接收教师/管理员创建新课程时的完整请求数据
 */
@Schema(description = "创建课程请求")
public class CourseCreateDTO {

    @NotBlank(message = "课程名称不能为空")
    @Size(max = 100, message = "课程名称不能超过100个字符")
    @Schema(description = "课程名称", example = "Java程序设计基础",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 30, message = "课程编号不能超过30个字符")
    @Schema(description = "课程编号", example = "CS101")
    private String code;

    @Size(max = 2000, message = "课程描述不能超过2000个字符")
    @Schema(description = "课程描述", example = "本课程涵盖Java语言核心概念、面向对象编程思想...")
    private String description;

    @Schema(description = "课程分类", example = "计算机科学",
            allowableValues = {"计算机科学", "数学", "物理", "化学", "文学", "艺术", "其他"})
    private String category;

    @DecimalMin(value = "0.1", message = "学分最小为0.1")
    @DecimalMax(value = "10.0", message = "学分最大为10.0")
    @Schema(description = "课程学分", example = "4.0")
    private BigDecimal credits;

    @Min(value = 1, message = "难度等级最低为1")
    @Max(value = 5, message = "难度等级最高为5")
    @Schema(description = "课程难度等级(1-5)", example = "3")
    private Integer difficulty;

    @Schema(description = "封面图片URL地址", example = "https://example.com/course-cover.jpg")
    private String coverImage;

    @Min(value = 1, message = "最大选课人数至少为1")
    @Max(value = 10000, message = "最大选课人数不超过10000")
    @Schema(description = "最大选课人数", example = "200")
    private Integer maxStudents;

    @Pattern(regexp = "^(FREE|APPROVAL|CODE)$", message = "选课类型只能是FREE/APPROVAL/CODE")
    @Schema(description = "选课类型", example = "FREE",
            allowableValues = {"FREE", "APPROVAL", "CODE"})
    private String enrollmentType = "FREE";

    @Schema(description = "选课码（enrollmentType=CODE时必填）", example = "JAVA2024FALL")
    private String enrollmentCode;

    @DecimalMin(value = "0", message = "价格不能为负数")
    @Schema(description = "课程价格（元），0表示免费", example = "0.00")
    private BigDecimal price = BigDecimal.ZERO;

    @Schema(description = "课程标签列表", example = "[\"Java\", \"后端\", \"入门\"]")
    private List<String> tags;

    @Schema(description = "开课时间", example = "2024-09-01T08:00:00")
    private LocalDateTime startDate;

    @Schema(description = "结课时间", example = "2025-01-15T18:00:00")
    private LocalDateTime endDate;

    @Schema(description = "前置课程ID列表", example = "[1, 2]")
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
