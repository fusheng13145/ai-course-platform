package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 上传资源请求DTO
 * 用于接收上传课程资源时的请求数据，包含文件信息和权限配置
 */
@Schema(description = "上传资源请求")
public class ResourceUploadDTO {

    @Schema(description = "所属章节ID（可选，关联到具体章节）", example = "201")
    private Long chapterId;

    @NotNull(message = "所属课程ID不能为空")
    @Schema(description = "所属课程ID", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @NotBlank(message = "资源名称不能为空")
    @Size(max = 200, message = "资源名称不能超过200个字符")
    @Schema(description = "资源/文件名称", example = "第一章课件.pdf",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "文件类型不能为空")
    @Pattern(regexp = "^(PDF|DOC|DOCX|XLS|XLSX|PPT|PPTX|MP4|MP3|ZIP|RAR|IMG|MD|CODE|OTHER)$",
            message = "文件类型不合法")
    @Schema(description = "文件类型", example = "PDF",
            allowableValues = {"PDF", "DOC", "DOCX", "XLS", "XLSX", "PPT", "PPTX",
                    "MP4", "MP3", "ZIP", "RAR", "IMG", "MD", "CODE", "OTHER"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileType;

    @Min(value = 1, message = "文件大小必须大于0字节")
    @Max(value = 107374182400L, message = "文件大小不能超过100GB") // 100GB
    @Schema(description = "文件大小（字节）", example = "5242880")
    private Long fileSize;

    @NotBlank(message = "资源URL地址不能为空")
    @Size(max = 500, message = "URL长度不能超过500个字符")
    @Schema(description = "资源访问URL地址", example = "https://example.com/files/ch1-slides.pdf",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

    @Schema(description = "是否对全员可见（true=选课学生均可访问）", example = "true")
    private Boolean isVisibleToAll = true;

    @Schema(description = "是否允许下载", example = "true")
    private Boolean downloadAllowed = true;

    @Size(max = 500, message = "版本说明不能超过500个字符")
    @Schema(description = "版本描述/变更说明", example = "2024秋季学期版本，更新了第3节内容")
    private String versionDescription;

    // ====================== Getter & Setter ======================

    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Boolean getIsVisibleToAll() { return isVisibleToAll; }
    public void setIsVisibleToAll(Boolean isVisibleToAll) { this.isVisibleToAll = isVisibleToAll; }
    public Boolean getDownloadAllowed() { return downloadAllowed; }
    public void setDownloadAllowed(Boolean downloadAllowed) { this.downloadAllowed = downloadAllowed; }
    public String getVersionDescription() { return versionDescription; }
    public void setVersionDescription(String versionDescription) { this.versionDescription = versionDescription; }
}
