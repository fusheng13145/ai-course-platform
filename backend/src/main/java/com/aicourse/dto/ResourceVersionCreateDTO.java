package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 创建资源版本请求DTO
 * 用于接收为已有资源创建新版本时的请求数据
 */
@Schema(description = "创建资源版本请求")
@Data
public class ResourceVersionCreateDTO {

    @NotNull(message = "资源ID不能为空")
    @Schema(description = "目标资源ID", example = "301",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long resourceId;

    @Schema(description = "文件名", example = "slides-v2.pdf")
    private String fileName;

    @Schema(description = "文件类型", example = "PDF")
    private String fileType;

    @Schema(description = "资源URL地址", example = "https://example.com/files/slides-v2.pdf")
    private String url;

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

    @Schema(description = "版本描述", example = "第二版课件")
    private String description;

    @Schema(description = "上传者用户ID", example = "1001")
    private Long uploaderId;

}
