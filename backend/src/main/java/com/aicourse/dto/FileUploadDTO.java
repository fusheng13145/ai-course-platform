package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 文件分片上传创建请求DTO
 * 用于创建文件分片上传会话
 */
@Schema(description = "文件上传创建请求")
@Data
public class FileUploadDTO {

    @NotBlank(message = "文件名不能为空")
    @Schema(description = "文件名", example = "lecture1.pdf", requiredMode = Schema.RequiredMode.REQUIRED)
    private String filename;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件大小必须大于0")
    @Schema(description = "文件大小（字节）", example = "1048576", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long fileSize;

    @NotNull(message = "总分片数不能为空")
    @Min(value = 1, message = "总分片数必须大于0")
    @Schema(description = "总分片数", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long totalChunks;

}
