package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseChapterMapper;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 课程章节服务实现：相关业务逻辑入口，提供增删改查与编排能力。
 */
@Service
public class CourseChapterServiceImpl extends ServiceImpl<CourseChapterMapper, CourseChapter> implements CourseChapterService {

    private static final Logger log = LoggerFactory.getLogger(CourseChapterServiceImpl.class);

    private final CourseService courseService;

    public CourseChapterServiceImpl(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * 课程管理权限校验（语义与原 CourseChapterController.canManageCourse 一致）：
     * 管理员放行；否则课程必须存在且 operatorId 为授课教师，否则抛「无权管理该课程」。
     */
    private void assertCanManageCourse(Long courseId, Long operatorId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        Course course = courseService.getById(courseId);
        if (course == null || !operatorId.equals(course.getTeacherId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权管理该课程");
        }
    }


    /** 创建课程章节 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseChapter createChapter(CourseChapter chapter, Long operatorId, boolean isAdmin) {
        log.info("创建课程章节");

        assertCanManageCourse(chapter.getCourseId(), operatorId, isAdmin);
        this.save(chapter);
        return chapter;
    }


    /** 更新课程章节 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseChapter updateChapter(Long id, CourseChapter chapter, Long operatorId, boolean isAdmin) {
        log.info("更新课程章节");

        CourseChapter existingChapter = this.getById(id);
        if (existingChapter == null) {
            throw new BusinessException("章节不存在");
        }
        assertCanManageCourse(existingChapter.getCourseId(), operatorId, isAdmin);
        chapter.setId(id);
        this.updateById(chapter);
        return this.getById(id);
    }


    /** 删除课程章节 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChapter(Long id, Long operatorId, boolean isAdmin) {
        log.info("删除课程章节");

        CourseChapter existingChapter = this.getById(id);
        if (existingChapter == null) {
            throw new BusinessException("章节不存在");
        }
        assertCanManageCourse(existingChapter.getCourseId(), operatorId, isAdmin);
        this.removeById(id);
    }
}
