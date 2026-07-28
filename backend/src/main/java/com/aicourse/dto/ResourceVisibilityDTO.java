package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 资源可见性切换请求DTO
 * 用于切换课程资源的全域可见性
 */
@Schema(description = "资源可见性切换请求")
@Data
public class ResourceVisibilityDTO {

    @NotNull(message = "可见性参数不能为空")
    @Schema(description = "是否对所有人可见", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean visibleToAll;

}
