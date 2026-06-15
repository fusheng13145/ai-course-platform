package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程详情响应VO（View Object）
 * 用于返回给前端的课程完整详情信息，包含统计数据和教师信息
 */
@Schema(description = "课程详情响应")
public class CourseDetailVO {

    // ====================== 课程基础字段 ======================

    @Schema(description = "课程ID", example = "1")
    private Long id;

    @Schema(description = "课程名称", example = "Java程序设计基础")
    private String name;

    @Schema(description = "课程编号", example = "CS101")
    private String code;

    @Schema(description = "课程描述", example = "本课程涵盖Java语言核心概念...")
    private String description;

    @Schema(description = "课程分类", example = "计算机科学")
    private String category;

    @Schema(description = "课程学分", example = "4.0")
    private BigDecimal credits;

    @Schema(description = "课程难度等级(1-5)", example = "3")
    private Integer difficulty;

    @Schema(description = "封面图片URL", example = "https://example.com/cover.jpg")
    private String coverImage;

    @Schema(description = "选课类型", example = "FREE",
            allowableValues = {"FREE", "APPROVAL", "CODE"})
    private String enrollmentType;

    @Schema(description = "最大选课人数", example = "200")
    private Integer maxStudents;

    @Schema(description = "课程价格（元）", example = "0.00")
    private BigDecimal price;

    @Schema(description = "课程状态", example = "ACTIVE",
            allowableValues = {"ACTIVE", "INACTIVE", "DRAFT"})
    private String status;

    @Schema(description = "开课时间", example = "2024-09-01T08:00:00")
    private LocalDateTime startDate;

    @Schema(description = "结课时间", example = "2025-01-15T18:00:00")
    private LocalDateTime endDate;

    @Schema(description = "创建时间", example = "2024-08-15T10:00:00")
    private LocalDateTime createdAt;

    // ====================== 统计与关联字段 ======================

    @Schema(description = "已选课学生人数", example = "156")
    private Integer enrolledCount;

    @Schema(description = "章节数量", example = "12")
    private Integer chapterCount;

    @Schema(description = "平均成绩", example = "82.5")
    private BigDecimal avgScore;

    @Schema(description = "授课教师信息")
    private TeacherInfoVO teacherInfo;

    @Schema(description = "当前用户的学习进度")
    private ProgressVO progress;

    /**
     * 授课教师信息内部类
     */
    @Schema(description = "授课教师信息")
    public static class TeacherInfoVO {

        @Schema(description = "教师ID", example = "100")
        private Long id;

        @Schema(description = "教师姓名", example = "李教授")
        private String name;

        @Schema(description = "教师头像URL", example = "https://example.com/avatar.jpg")
        private String avatar;

        // ====================== Getter & Setter ======================

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
    }

    /**
     * 学习进度内部类
     */
    @Schema(description = "学习进度信息")
    public static class ProgressVO {

        @Schema(description = "是否已选课", example = "true")
        private Boolean enrolled;

        @Schema(description = "已完成章节数", example = "8")
        private Integer chaptersCompleted;

        @Schema(description = "完成百分比(0-100)", example = "66.7")
        private Double percentage;

        // ====================== Getter & Setter ======================

        public Boolean getEnrolled() { return enrolled; }
        public void setEnrolled(Boolean enrolled) { this.enrolled = enrolled; }
        public Integer getChaptersCompleted() { return chaptersCompleted; }
        public void setChaptersCompleted(Integer chaptersCompleted) { this.chaptersCompleted = chaptersCompleted; }
        public Double getPercentage() { return percentage; }
        public void setPercentage(Double percentage) { this.percentage = percentage; }
    }

    // ====================== Getter & Setter ======================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getCredits() { return credits; }
    public void setCredits(BigDecimal credits) { this.credits = credits; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public String getEnrollmentType() { return enrollmentType; }
    public void setEnrollmentType(String enrollmentType) { this.enrollmentType = enrollmentType; }
    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getEnrolledCount() { return enrolledCount; }
    public void setEnrolledCount(Integer enrolledCount) { this.enrolledCount = enrolledCount; }
    public Integer getChapterCount() { return chapterCount; }
    public void setChapterCount(Integer chapterCount) { this.chapterCount = chapterCount; }
    public BigDecimal getAvgScore() { return avgScore; }
    public void setAvgScore(BigDecimal avgScore) { this.avgScore = avgScore; }
    public TeacherInfoVO getTeacherInfo() { return teacherInfo; }
    public void setTeacherInfo(TeacherInfoVO teacherInfo) { this.teacherInfo = teacherInfo; }
    public ProgressVO getProgress() { return progress; }
    public void setProgress(ProgressVO progress) { this.progress = progress; }
}
