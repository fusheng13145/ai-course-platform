package com.aicourse.dto;

import com.aicourse.entity.CourseChapter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 课程章节创建／更新入参（入参收实体加固）。
 *
 * <p><b>为什么不直接绑 {@code CourseChapter} 实体</b>：实体上的 {@code id}／{@code createdAt}／
 * {@code updatedAt} 是服务端字段；更关键的是 {@code courseId}——更新接口若接受客户端传入的
 * {@code courseId}，就能把别人的章节"搬"到自己课程下（章节劫持）。因此本 DTO 只在<b>创建</b>时
 * 使用 {@code courseId}，更新时由服务端以库里既有章节的 {@code courseId} 为准。</p>
 */
@Schema(name = "CourseChapterSaveDTO", description = "课程章节创建／更新入参；id 与时间戳不接受客户端传入")
@Data
public class CourseChapterSaveDTO {

    @NotBlank(message = "章节标题不能为空")
    @Schema(description = "章节标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "章节正文（HTML 允许）")
    private String content;

    @Schema(description = "父章节ID；0 或空表示一级章节", example = "0")
    private Long parentId;

    @Schema(description = "排序序号", example = "0")
    private Integer orderIndex;

    @Schema(description = "时长（分钟）", example = "45")
    private Integer duration;

    @Schema(description = "是否发布")
    private Boolean isPublished;

    @Schema(description = "所属课程ID；仅在创建时生效，更新忽略（以既有章节归属为准）", example = "1")
    private Long courseId;

    public CourseChapter toEntity() {
        CourseChapter chapter = new CourseChapter();
        applyTo(chapter);
        return chapter;
    }

    public void applyTo(CourseChapter chapter) {
        chapter.setTitle(title);
        chapter.setContent(content);
        chapter.setParentId(parentId);
        chapter.setOrderIndex(orderIndex);
        chapter.setDuration(duration);
        chapter.setIsPublished(isPublished);
        chapter.setCourseId(courseId);
    }
}
