package com.aicourse.vo;

import com.aicourse.entity.CourseChapter;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程章节出参。
 *
 * <p>字段与 {@link CourseChapter} 一一对应、零删减。章节正文 `content` 是课程内容，
 * 对已选课学生本就公开，无需裁剪。
 */
@Data
public class CourseChapterVO {

    private Long id;
    private Long courseId;
    private Long parentId;
    private String title;
    private String content;
    private Integer orderIndex;
    private Integer duration;
    private Boolean isPublished;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 实体 → 出参。 */
    public static CourseChapterVO from(CourseChapter chapter) {
        if (chapter == null) {
            return null;
        }
        CourseChapterVO vo = new CourseChapterVO();
        vo.setId(chapter.getId());
        vo.setCourseId(chapter.getCourseId());
        vo.setParentId(chapter.getParentId());
        vo.setTitle(chapter.getTitle());
        vo.setContent(chapter.getContent());
        vo.setOrderIndex(chapter.getOrderIndex());
        vo.setDuration(chapter.getDuration());
        vo.setIsPublished(chapter.getIsPublished());
        vo.setCreatedAt(chapter.getCreatedAt());
        vo.setUpdatedAt(chapter.getUpdatedAt());
        return vo;
    }
}
