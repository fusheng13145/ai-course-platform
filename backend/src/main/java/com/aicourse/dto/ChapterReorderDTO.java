package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 章节批量排序请求DTO
 * 用于教师/管理员批量更新课程章节的显示顺序
 */
@Schema(description = "章节批量排序请求")
@Data
public class ChapterReorderDTO {

    @NotEmpty(message = "排序列表不能为空")
    @Valid
    @Schema(description = "排序项列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<OrderItem> orderList;

    /**
     * 排序项
     */
    @Data
    @Schema(description = "章节排序项")
    public static class OrderItem {

        @NotNull(message = "章节ID不能为空")
        @Schema(description = "章节ID", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Long id;

        @NotNull(message = "排序序号不能为空")
        @Schema(description = "排序序号（从小到大排列）", example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer orderIndex;

    }

}
