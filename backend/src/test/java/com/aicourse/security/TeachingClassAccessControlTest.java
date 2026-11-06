package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Course;
import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.CourseService;
import com.aicourse.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 教学班访问控制单元测试（V14 / WP4）。
 * 钉住：课程负责人之外不能建班/改班；主讲只能本院；名单仅本班主讲可读，其他班主讲 403。
 */
@ExtendWith(MockitoExtension.class)
class TeachingClassAccessControlTest {

    @Mock
    private CourseService courseService;
    @Mock
    private UserService userService;
    @Mock
    private TeachingClassMapper teachingClassMapper;

    private TeachingClassAccessControl accessControl;

    @BeforeEach
    void setUp() {
        accessControl = new TeachingClassAccessControl(courseService, userService, teachingClassMapper);
    }

    @Test
    @DisplayName("非课程负责人创建教学班返回 403")
    void nonOwnerCannotManageCourse() {
        Course course = course(10L, 100L, 1L);
        when(courseService.getById(10L)).thenReturn(course);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accessControl.assertCourseOwner(10L, 101L));
        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("主讲指派跨院教师返回 403")
    void crossCollegeLeadIsForbidden() {
        Course course = course(10L, 100L, 1L);
        User teacher = user(200L, "TEACHER", 2L);
        when(userService.getById(200L)).thenReturn(teacher);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accessControl.assertLeadTeacherInCourseCollege(course, 200L));
        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("同课程其他教学班主讲读取本班名单返回 403")
    void otherClassLeadCannotReadMembers() {
        TeachingClass teachingClass = teachingClass(30L, 10L, 100L);
        when(teachingClassMapper.selectById(30L)).thenReturn(teachingClass);
        User otherTeacher = user(200L, "TEACHER", 1L);
        when(userService.getById(200L)).thenReturn(otherTeacher);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accessControl.assertTeachingClassLead(30L, 200L));
        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("管理员治理只读放行教学班名单")
    void adminCanReadMembers() {
        TeachingClass teachingClass = teachingClass(30L, 10L, 100L);
        when(teachingClassMapper.selectById(30L)).thenReturn(teachingClass);
        User admin = user(1L, "ADMIN", 1L);
        when(userService.getById(1L)).thenReturn(admin);

        assertEquals(30L, accessControl.assertTeachingClassLead(30L, 1L).getId());
    }

    private static Course course(Long id, Long teacherId, Long collegeId) {
        Course course = new Course();
        course.setId(id);
        course.setTeacherId(teacherId);
        course.setCollegeId(collegeId);
        return course;
    }

    private static User user(Long id, String role, Long collegeId) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setCollegeId(collegeId);
        return user;
    }

    private static TeachingClass teachingClass(Long id, Long courseId, Long leadTeacherId) {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(id);
        teachingClass.setCourseId(courseId);
        teachingClass.setLeadTeacherId(leadTeacherId);
        return teachingClass;
    }
}
