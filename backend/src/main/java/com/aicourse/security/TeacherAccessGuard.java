package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.Question;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.TaskService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 教师端归属校验唯一实现。
 *
 * <p>Controller 拆分后，课程/作业/题目/章节/学生归属判定必须共用本组件，
 * 不得在多个控制器里各写一套；拒绝语义仍为：不存在 → 404，越权 → 403。</p>
 */
@Component
public class TeacherAccessGuard {

    private final CourseService courseService;
    private final TaskService taskService;
    private final QuestionService questionService;
    private final CourseChapterService chapterService;
    private final CourseEnrollmentService enrollmentService;

    public TeacherAccessGuard(CourseService courseService,
                              TaskService taskService,
                              QuestionService questionService,
                              CourseChapterService chapterService,
                              CourseEnrollmentService enrollmentService) {
        this.courseService = courseService;
        this.taskService = taskService;
        this.questionService = questionService;
        this.chapterService = chapterService;
        this.enrollmentService = enrollmentService;
    }

    /** 教师须为课程授课教师，管理员放行。 */
    public void assertCourseOwner(Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (isAdmin(auth)) {
            return;
        }
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        if (!userId.equals(course.getTeacherId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权操作该课程");
        }
    }

    /** 作业必须存在且其课程归当前教师。 */
    public void assertTaskOwner(Long taskId, Authentication auth) {
        Task task = taskService.getById(taskId);
        if (task == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "作业不存在");
        }
        assertCourseOwner(task.getCourseId(), auth);
    }

    /** 题目有课程归属时校验课程；历史无课程的题目按原行为放行。 */
    public void assertQuestionOwner(Long questionId, Authentication auth) {
        Question question = questionService.getById(questionId);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        if (question.getCourseId() != null) {
            assertCourseOwner(question.getCourseId(), auth);
        }
    }

    /** chapterId 必须真实属于 courseId（防「自己的课程 ID + 他人的章节 ID」）。 */
    public void assertChapterBelongsToCourse(Long courseId, Long chapterId) {
        CourseChapter chapter = chapterService.getById(chapterId);
        if (chapter == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "章节不存在");
        }
        if (!courseId.equals(chapter.getCourseId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权操作该章节");
        }
    }

    /** 当前教师须为该学生的在读授课教师，管理员放行。 */
    public void assertTeacherOwnsStudent(Long studentId, Authentication auth) {
        if (isAdmin(auth)) {
            return;
        }
        Long teacherId = (Long) auth.getPrincipal();
        if (!enrollmentService.hasApprovedEnrollmentWithTeacher(studentId, teacherId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权查看该学生学情");
        }
    }

    /** 是否管理员角色：课程归属类校验对管理员放行。 */
    public boolean isAdmin(Authentication auth) {
        return AccessSupport.isAdmin(auth);
    }
}
