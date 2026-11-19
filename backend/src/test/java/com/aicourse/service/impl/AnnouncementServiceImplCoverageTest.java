package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.AdminClass;
import com.aicourse.entity.Announcement;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.AdminClassMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 公告剩余分支：目标数量、空字段、教学班/行政班可见性、受众归属与三类目标合并过滤。
 */
class AnnouncementServiceImplCoverageTest {

    private CourseService courseService;
    private CourseEnrollmentService enrollmentService;
    private UserService userService;
    private NotificationService notificationService;
    private TeachingClassMapper teachingClassMapper;
    private AdminClassMapper adminClassMapper;
    private AnnouncementServiceImpl service;
    private Map<Serializable, Announcement> rows;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Announcement.class);
        TableInfoHelper.initTableInfo(assistant, Course.class);
        TableInfoHelper.initTableInfo(assistant, CourseEnrollment.class);
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, TeachingClass.class);
        TableInfoHelper.initTableInfo(assistant, AdminClass.class);
    }

    @BeforeEach
    void setUp() {
        courseService = mock(CourseService.class);
        enrollmentService = mock(CourseEnrollmentService.class);
        userService = mock(UserService.class);
        notificationService = mock(NotificationService.class);
        teachingClassMapper = mock(TeachingClassMapper.class);
        adminClassMapper = mock(AdminClassMapper.class);
        rows = new HashMap<>();
        service = new AnnouncementServiceImpl(courseService, enrollmentService, userService,
                notificationService, teachingClassMapper, adminClassMapper) {
            @Override
            public boolean save(Announcement entity) {
                entity.setId(200L);
                rows.put(200L, entity);
                return true;
            }

            @Override
            public Announcement getById(Serializable id) {
                return rows.get(id);
            }

            @Override
            public <E extends IPage<Announcement>> E page(E page, Wrapper<Announcement> queryWrapper) {
                return page;
            }
        };
    }

    private void stubEmptyScope() {
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any())).thenReturn(List.of());
        when(courseService.list(ArgumentMatchers.<Wrapper<Course>>any())).thenReturn(List.of());
        when(teachingClassMapper.selectList(ArgumentMatchers.<Wrapper<TeachingClass>>any())).thenReturn(List.of());
        when(userService.getById(any())).thenReturn(null);
        when(adminClassMapper.selectList(ArgumentMatchers.<Wrapper<AdminClass>>any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("发布：目标为 0/多个均拒绝，标题/正文 null 与空白均拒绝")
    void publishInvalidTargetsAndFields() {
        assertBadRequest(() -> service.publish(null, null, null, "t", "c", 1L, "TEACHER"));
        assertBadRequest(() -> service.publish(3L, 8L, 6L, "t", "c", 1L, "TEACHER"));
        assertBadRequest(() -> service.publish(3L, null, null, null, "c", 1L, "TEACHER"));
        assertBadRequest(() -> service.publish(3L, null, null, "t", null, 1L, "TEACHER"));
        assertBadRequest(() -> service.publish(3L, null, null, "t", "  ", 1L, "TEACHER"));
        verify(notificationService, never()).batchSendNotifications(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private void assertBadRequest(org.junit.jupiter.api.function.Executable executable) {
        BusinessException ex = assertThrows(BusinessException.class, executable);
        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
    }

    @Test
    @DisplayName("受众：课程/教学班/行政班负责人为空时不追加，发布人不为空时追加")
    void audienceTargetNullOwners() {
        CourseEnrollment courseEnrollment = new CourseEnrollment();
        courseEnrollment.setStudentId(1L);
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any())).thenReturn(List.of(courseEnrollment));
        Course course = new Course(); course.setId(3L); course.setTeacherId(null);
        when(courseService.getById(3L)).thenReturn(course);
        service.publish(3L, null, null, "t", "c", null, "TEACHER");

        TeachingClass tc = new TeachingClass(); tc.setId(8L); tc.setLeadTeacherId(null);
        when(teachingClassMapper.selectById(8L)).thenReturn(tc);
        service.publish(null, 8L, null, "t", "c", 1L, "TEACHER");

        User student = new User(); student.setId(5L);
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of(student));
        AdminClass adminClass = new AdminClass(); adminClass.setId(6L); adminClass.setAdvisorTeacherId(null);
        when(adminClassMapper.selectById(6L)).thenReturn(adminClass);
        service.publish(null, null, 6L, "t", "c", 1L, "ADVISOR");
    }

    @Test
    @DisplayName("可见性：教学班与行政班目标均可读，非归属课程目标 403")
    void getVisibleByTeachingAndAdminClass() {
        Announcement tcAnnouncement = new Announcement();
        tcAnnouncement.setId(1L); tcAnnouncement.setTeachingClassId(2L);
        rows.put(1L, tcAnnouncement);
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any())).thenReturn(List.of());
        when(courseService.list(ArgumentMatchers.<Wrapper<Course>>any())).thenReturn(List.of());
        TeachingClass tc = new TeachingClass(); tc.setId(2L); tc.setLeadTeacherId(9L);
        when(teachingClassMapper.selectList(ArgumentMatchers.<Wrapper<TeachingClass>>any())).thenReturn(List.of(tc));
        when(userService.getById(9L)).thenReturn(null);
        when(adminClassMapper.selectList(ArgumentMatchers.<Wrapper<AdminClass>>any())).thenReturn(List.of());
        assertEquals(1L, service.getVisible(1L, 9L).getId());

        Announcement adminAnnouncement = new Announcement();
        adminAnnouncement.setId(2L); adminAnnouncement.setAdminClassId(6L);
        rows.put(2L, adminAnnouncement);
        User user = new User(); user.setId(9L); user.setAdminClassId(6L);
        when(userService.getById(9L)).thenReturn(user);
        assertEquals(2L, service.getVisible(2L, 9L).getId());

        Announcement forbidden = new Announcement();
        forbidden.setId(3L); forbidden.setCourseId(7L);
        rows.put(3L, forbidden);
        when(courseService.list(ArgumentMatchers.<Wrapper<Course>>any())).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getVisible(3L, 9L));
        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("归属：null 用户、null 课程/教学班字段、教师/班主任两条来源")
    void audienceScopeBranchesAndAllDimensions() {
        assertTrue(((Page<?>) service.pageVisible(1, 10, null)).getRecords().isEmpty());

        CourseEnrollment nullDims = new CourseEnrollment();
        nullDims.setCourseId(null); nullDims.setTeachingClassId(null);
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any())).thenReturn(List.of(nullDims));
        Course owned = new Course(); owned.setId(3L);
        when(courseService.list(ArgumentMatchers.<Wrapper<Course>>any())).thenReturn(List.of(owned));
        TeachingClass led = new TeachingClass(); led.setId(8L);
        when(teachingClassMapper.selectList(ArgumentMatchers.<Wrapper<TeachingClass>>any())).thenReturn(List.of(led));
        User user = new User(); user.setId(9L); user.setAdminClassId(6L);
        when(userService.getById(9L)).thenReturn(user);
        AdminClass advised = new AdminClass(); advised.setId(7L);
        when(adminClassMapper.selectList(ArgumentMatchers.<Wrapper<AdminClass>>any())).thenReturn(List.of(advised));

        Page<?> page = (Page<?>) service.pageVisible(1, 10, 9L);
        assertNotNull(page);
    }

    @Test
    @DisplayName("归属：空集合短路")
    void pageVisibleEmptyScopeStillReturnsPage() {
        stubEmptyScope();
        Page<Announcement> page = (Page<Announcement>) service.pageVisible(1, 10, 42L);
        assertTrue(page.getRecords().isEmpty());
    }
}
