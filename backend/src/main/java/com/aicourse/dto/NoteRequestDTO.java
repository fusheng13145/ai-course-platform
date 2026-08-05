package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 笔记创建/更新请求 DTO
 */
@Schema(description = "笔记创建/更新请求")
@Data
public class NoteRequestDTO {

    /**
     * 笔记ID（更新时必填，创建时留空）
     */
    @Schema(description = "笔记ID，更新时必填，创建时留空")
    private Long id;

    /**
     * 关联课程ID
     */
    @Schema(description = "关联课程ID")
    private Long courseId;

    /**
     * 关联章节ID
     */
    @Schema(description = "关联章节ID")
    private Long chapterId;

    /**
     * 关联知识点ID
     */
    @Schema(description = "关联知识点ID")
    private Long knowledgePointId;

    /**
     * 笔记标题
     */
    @Schema(description = "笔记标题")
    private String title;

    /**
     * 笔记正文（Markdown）
     */
    @NotBlank(message = "笔记内容不能为空")
    @Schema(description = "笔记正文", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    /**
     * 是否收藏
     */
    @Schema(description = "是否收藏")
    private Boolean favorite;
}
