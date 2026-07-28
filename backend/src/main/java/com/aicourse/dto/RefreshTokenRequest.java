package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 刷新访问令牌请求DTO
 */
@Schema(description = "刷新令牌请求")
@Data
public class RefreshTokenRequest {

    @NotBlank(message = "刷新令牌不能为空")
    @Schema(description = "刷新令牌", example = "eyJhbGciOi...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;

}
