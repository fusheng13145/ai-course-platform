package com.aicourse.service;

import com.aicourse.entity.CourseChapter;
import com.baomidou.mybatisplus.extension.service.IService;

public interface CourseChapterService extends IService<CourseChapter> {

    /**
     * 新增章节（含课程管理权限校验，无权抛「无权管理该课程」）。
     */
    CourseChapter createChapter(CourseChapter chapter, Long operatorId, boolean isAdmin);

    /**
     * 修改章节（含章节存在性与课程管理权限校验），返回更新后章节。
     */
    CourseChapter updateChapter(Long id, CourseChapter chapter, Long operatorId, boolean isAdmin);

    /**
     * 删除章节（含章节存在性与课程管理权限校验）。
     */
    void deleteChapter(Long id, Long operatorId, boolean isAdmin);
}
