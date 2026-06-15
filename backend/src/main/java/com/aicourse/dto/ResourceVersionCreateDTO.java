package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 创建资源版本请求DTO
 * 用于接收为已有资源创建新版本时的请求数据
 */
@Schema(description = "创建资源版本请求")
public class ResourceVersionCreateDTO {

    @NotNull(message = "资源ID不能为空")
    @Schema(description = "目标资源ID", example = "301",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long resourceId;

    @NotBlank(message = "文件URL不能为空")
    @Size(max = 500, message = "URL长度不能超过500个字符")
    @Schema(description = "新版文件的URL地址", example = "https://example.com/files/ch1-slides-v2.pdf",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileUrl;

    @Min(value = 1, message = "文件大小必须大于0字节")
    @Schema(description = "新版文件大小（字节）", example = "5898240")
    private Long fileSize;

    @Min(value = 1, message = "版本号至少为1")
    @Schema(description = "版本号（递增整数）", example = "2")
    private Integer versionNumber;

    @Size(max = 500, message = "变更说明不能超过500个字符")
    @Schema(description = "本次版本的变更说明", example = "修复了第3节的排版问题，补充了练习题答案")
    private String changelog;

    // ====================== Getter & Setter ======================

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getChangelog() {
        return changelog;
    }

    public void setChangelog(String changelog) {
        this.changelog = changelog;
    }
}
