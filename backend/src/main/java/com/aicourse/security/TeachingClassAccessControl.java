package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.CourseService;
import com.aicourse.service.UserService;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 教学班访问控制（V14 / B-12-P-08 WP4）。
 *
 * <p>口径：</p>
 * <ul>
 *     <li>建班 / 改班 / 指定主讲：仅课程负责人（{@code courses.teacher_id}），管理员不代办；</li>
 *     <li>主讲指派：目标必须是 TEACHER，且其 {@code users.college_id} 与课程所属学院一致（2A）；</li>
 *     <li>名单/成绩：仅该教学班主讲可读；管理员按治理只读放行；同课程其他教学班主讲 403。</li>
 * </ul>
 */
@Component
public class TeachingClassAccessControl {

    private final CourseService courseService;
    private final UserService userService;
    private final TeachingClassMapper teachingClassMapper;

    public TeachingClassAccessControl(CourseService courseService,
                                      UserService userService,
                                      TeachingClassMapper teachingClassMapper) {
        this.courseService = courseService;
        this.userService = userService;
        this.teachingClassMapper = teachingClassMapper;
    }

    /**
     * 取课程，不存在时抛 404。
     */
    public Course requireCourse(Long courseId) {
        Course course = courseId == null ? null : courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在：" + courseId);
        }
        return course;
    }

    /**
     * 要求操作人是课程负责人。
     */
    public Course assertCourseOwner(Long courseId, Long operatorId) {
        Course course = requireCourse(courseId);
        if (!Objects.equals(course.getTeacherId(), operatorId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "只有课程负责人可管理该课程的教学班");
        }
        return course;
    }

    /**
     * 取教学班，不存在时抛 404。
     */
    public TeachingClass requireTeachingClass(Long teachingClassId) {
        TeachingClass teachingClass = teachingClassId == null ? null : teachingClassMapper.selectById(teachingClassId);
        if (teachingClass == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "教学班不存在：" + teachingClassId);
        }
        return teachingClass;
    }

    /**
     * 要求操作人是该教学班主讲；管理员只读放行。
     */
    public TeachingClass assertTeachingClassLead(Long teachingClassId, Long operatorId) {
        TeachingClass teachingClass = requireTeachingClass(teachingClassId);
        User operator = operatorId == null ? null : userService.getById(operatorId);
        if (operator != null && RoleConstants.ADMIN.equals(operator.getRole())) {
            return teachingClass;
        }
        if (!Objects.equals(teachingClass.getLeadTeacherId(), operatorId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "只有该教学班主讲可查看名单与成绩");
        }
        return teachingClass;
    }

    /**
     * 要求操作人可管理该教学班名单（V14 / WP5）：本班主讲或课程负责人；
     * 管理员治理只读，不代导入/重同步。
     */
    public TeachingClass assertRosterManage(Long teachingClassId, Long operatorId) {
        TeachingClass teachingClass = requireTeachingClass(teachingClassId);
        if (Objects.equals(teachingClass.getLeadTeacherId(), operatorId)) {
            return teachingClass;
        }
        Course course = requireCourse(teachingClass.getCourseId());
        if (!Objects.equals(course.getTeacherId(), operatorId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "只有课程负责人或该教学班主讲可管理名单");
        }
        return teachingClass;
    }

    /**
     * 要求操作人可读取该教学班名单来源（V14 / WP5）：管理员治理只读、本班主讲、课程负责人。
     */
    public TeachingClass assertRosterRead(Long teachingClassId, Long operatorId) {
        TeachingClass teachingClass = requireTeachingClass(teachingClassId);
        User operator = operatorId == null ? null : userService.getById(operatorId);
        if (operator != null && RoleConstants.ADMIN.equals(operator.getRole())) {
            return teachingClass;
        }
        if (Objects.equals(teachingClass.getLeadTeacherId(), operatorId)) {
            return teachingClass;
        }
        Course course = requireCourse(teachingClass.getCourseId());
        if (!Objects.equals(course.getTeacherId(), operatorId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "只有课程负责人或该教学班主讲可查看名单来源");
        }
        return teachingClass;
    }

    /**
     * 要求目标教师与课程属于同一学院（课程学院为空时回退负责人学院）。
     */
    public User assertLeadTeacherInCourseCollege(Course course, Long teacherId) {
        User teacher = teacherId == null ? null : userService.getById(teacherId);
        if (teacher == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "主讲教师不存在：" + teacherId);
        }
        if (!RoleConstants.TEACHER.equals(teacher.getRole())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "主讲必须是教师账号");
        }
        Long courseCollegeId = course.getCollegeId();
        if (courseCollegeId == null && course.getTeacherId() != null) {
            User owner = userService.getById(course.getTeacherId());
            courseCollegeId = owner == null ? null : owner.getCollegeId();
        }
        if (courseCollegeId == null || !Objects.equals(courseCollegeId, teacher.getCollegeId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "只能指派本院教师为该课程主讲");
        }
        return teacher;
    }
}
