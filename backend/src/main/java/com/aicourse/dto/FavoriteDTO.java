package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 收藏状态切换请求 DTO
 */
@Schema(description = "收藏状态切换请求")
@Data
public class FavoriteDTO {

    /**
     * 目标收藏状态：true-收藏，false-取消收藏
     */
    @Schema(description = "是否收藏", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean favorite;
}
