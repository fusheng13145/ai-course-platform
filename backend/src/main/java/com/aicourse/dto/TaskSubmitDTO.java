package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 任务提交DTO
 * 用于接收学生提交作业时的请求数据，支持文本内容和文件附件
 */
@Schema(description = "任务提交请求")
public class TaskSubmitDTO {

    @Size(max = 50000, message = "文本内容长度不能超过50000个字符")
    @Schema(description = "学生提交的文本内容（支持Markdown）", example = "## 解题思路\n\n本题采用动态规划方法...")
    private String content;

    @Schema(description = "提交的文件URL地址", example = "https://example.com/uploads/submission/hw1.pdf")
    private String fileUrl;

    @Schema(description = "提交时间（可选，不传则由服务端自动生成）", example = "2024-12-30T20:15:00")
    private LocalDateTime submittedAt;

    // ====================== Getter & Setter ======================

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
}
