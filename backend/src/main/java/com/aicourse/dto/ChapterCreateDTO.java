package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 创建章节请求DTO
 * 用于接收教师创建课程章节时的请求数据
 */
@Schema(description = "创建章节请求")
public class ChapterCreateDTO {

    @NotNull(message = "所属课程ID不能为空")
    @Schema(description = "所属课程ID", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @NotBlank(message = "章节标题不能为空")
    @Size(max = 100, message = "章节标题不能超过100个字符")
    @Schema(description = "章节标题", example = "第一章 Java概述",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Size(max = 5000, message = "章节描述不能超过5000个字符")
    @Schema(description = "章节描述/简介", example = "本章将介绍Java语言的发展历史、特点及开发环境搭建...")
    private String description;

    @Min(value = 0, message = "排序序号不能为负数")
    @Schema(description = "排序序号（从小到大排列）", example = "1")
    private Integer sortOrder = 0;

    @Pattern(regexp = "^(VIDEO|PDF|MARKDOWN|QUIZ)$", message = "内容类型只能是VIDEO/PDF/MARKDOWN/QUIZ")
    @Schema(description = "内容类型", example = "VIDEO",
            allowableValues = {"VIDEO", "PDF", "MARKDOWN", "QUIZ"})
    private String contentType = "MARKDOWN";

    @Schema(description = "内容资源URL（视频地址/文件链接等）", example = "https://example.com/video/chapter1.mp4")
    private String contentUrl;

    @Min(value = 1, message = "时长至少为1分钟")
    @Schema(description = "预计时长（分钟）", example = "45")
    private Integer duration;

    @Schema(description = "是否已发布", example = "false")
    private Boolean isPublished = false;

    @Schema(description = "关联资源列表", example = "[{\"name\":\"课件\",\"url\":\"https://...\"}]")
    private List<ResourceRef> resources;

    /**
     * 资源引用内部类
     */
    @Schema(description = "资源引用")
    public static class ResourceRef {

        @Schema(description = "资源名称", example = "第一章课件.pdf")
        private String name;

        @Schema(description = "资源URL地址", example = "https://example.com/files/ch1-slides.pdf")
        private String url;

        @Schema(description = "资源类型", example = "PDF",
                allowableValues = {"PDF", "DOC", "VIDEO", "AUDIO", "IMAGE", "LINK", "OTHER"})
        private String type;

        // ====================== Getter & Setter ======================

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    // ====================== Getter & Setter ======================

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentUrl() {
        return contentUrl;
    }

    public void setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Boolean getIsPublished() {
        return isPublished;
    }

    public void setIsPublished(Boolean isPublished) {
        this.isPublished = isPublished;
    }

    public List<ResourceRef> getResources() {
        return resources;
    }

    public void setResources(List<ResourceRef> resources) {
        this.resources = resources;
    }
}
