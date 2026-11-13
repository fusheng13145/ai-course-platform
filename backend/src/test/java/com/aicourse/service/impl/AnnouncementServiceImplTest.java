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
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公告服务单测（WP8，V16）。
 *
 * <p>判据（拆包方案 WP8 验收 1/2/4 的服务层部分）：扇出条数 = 受众去重人数；
 * 非受众读详情 403、不存在 404；多目标 / 空标题发布被拒；无归属用户列表为空页。
 * 发布端 ACL（验收 4）在 AnnouncementController，由其切片测试覆盖。</p>
 */
class AnnouncementServiceImplTest {

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
        // 单测无 Spring 上下文：save/getById 改由内存承接（扇出与可见性才是被测面）
        service = new AnnouncementServiceImpl(courseService, enrollmentService, userService,
                notificationService, teachingClassMapper, adminClassMapper) {
            @Override
            public boolean save(Announcement entity) {
                entity.setId(100L);
                rows.put(100L, entity);
                return true;
            }

            @Override
            public Announcement getById(Serializable id) {
                return rows.get(id);
            }
        };
    }

    private CourseEnrollment enrollment(Long studentId) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(3L);
        enrollment.setApprovalStatus("APPROVED");
        enrollment.setIsActive(true);
        return enrollment;
    }

    private void stubEmptyScope(Long userId) {
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any())).thenReturn(List.of());
        when(courseService.list(ArgumentMatchers.<Wrapper<Course>>any())).thenReturn(List.of());
        when(teachingClassMapper.selectList(ArgumentMatchers.<Wrapper<TeachingClass>>any()))
                .thenReturn(List.of());
        when(userService.getById(userId)).thenReturn(null);
        when(adminClassMapper.selectList(ArgumentMatchers.<Wrapper<AdminClass>>any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("课程公告：受众 = 选课学生 ∪ 课程教师 ∪ 发布人，去重后一次扇出")
    void publishFansOutToDeduplicatedAudience() {
        // 学生 1、2 选课；学生 1 重复出现（多选课记录）；教师 3；发布人 2
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any()))
                .thenReturn(List.of(enrollment(1L), enrollment(2L), enrollment(1L)));
        Course course = new Course();
        course.setId(3L);
        course.setTeacherId(3L);
        when(courseService.getById(3L)).thenReturn(course);

        service.publish(3L, null, null, "开学通知", "下周开课", 2L, Announcement.ROLE_TEACHER);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).batchSendNotifications(idsCaptor.capture(),
                eq("开学通知"), anyString(), eq("ANNOUNCEMENT"), anyString(), anyString(),
                anyString(), anyString());
        // 去重后 = {1, 2, 3} ∪ 发布人 2 → 恰好 3 人（验收 1：扇出条数 = 去重人数）
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("教学班公告：受众 = 班成员 ∪ 主讲")
    void publishToTeachingClass() {
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any()))
                .thenReturn(List.of(enrollment(1L)));
        TeachingClass tc = new TeachingClass();
        tc.setId(8L);
        tc.setLeadTeacherId(7L);
        when(teachingClassMapper.selectById(8L)).thenReturn(tc);

        service.publish(null, 8L, null, "测验提醒", "周五小测", 7L, Announcement.ROLE_TEACHER);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).batchSendNotifications(idsCaptor.capture(),
                anyString(), anyString(), eq("ANNOUNCEMENT"), anyString(), anyString(),
                anyString(), anyString());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(1L, 7L);
    }

    @Test
    @DisplayName("行政班公告：受众 = 班学生 ∪ 班主任")
    void publishToAdminClass() {
        User student = new User();
        student.setId(5L);
        student.setAdminClassId(6L);
        student.setIsActive(true);
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of(student));
        AdminClass adminClass = new AdminClass();
        adminClass.setId(6L);
        adminClass.setAdvisorTeacherId(9L);
        when(adminClassMapper.selectById(6L)).thenReturn(adminClass);

        service.publish(null, null, 6L, "班会通知", "周三班会", 9L, Announcement.ROLE_ADVISOR);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).batchSendNotifications(idsCaptor.capture(),
                anyString(), anyString(), eq("ANNOUNCEMENT"), anyString(), anyString(),
                anyString(), anyString());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(5L, 9L);
    }

    @Test
    @DisplayName("多目标发布被拒（400）；空标题被拒；无扇出")
    void rejectsInvalidPublish() {
        assertThatThrownBy(() -> service.publish(3L, 8L, null, "t", "c", 1L, Announcement.ROLE_TEACHER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.publish(3L, null, null, "  ", "c", 1L, Announcement.ROLE_TEACHER))
                .isInstanceOf(BusinessException.class);
        verify(notificationService, never()).batchSendNotifications(any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("详情：不存在 404；非受众 403；受众 200")
    void getVisibleEnforcesAudience() {
        assertThatThrownBy(() -> service.getVisible(404L, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiCode.NOT_FOUND));

        Announcement announcement = new Announcement();
        announcement.setId(7L);
        announcement.setCourseId(3L);
        rows.put(7L, announcement);

        // 用户 11：选课在课程 5（含教学班 2），不在课程 3 → 403
        CourseEnrollment other = new CourseEnrollment();
        other.setStudentId(11L);
        other.setCourseId(5L);
        other.setTeachingClassId(2L);
        other.setApprovalStatus("APPROVED");
        other.setIsActive(true);
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any()))
                .thenReturn(List.of(other));
        when(courseService.list(ArgumentMatchers.<Wrapper<Course>>any())).thenReturn(List.of());
        when(teachingClassMapper.selectList(ArgumentMatchers.<Wrapper<TeachingClass>>any()))
                .thenReturn(List.of());
        when(userService.getById(11L)).thenReturn(null);
        when(adminClassMapper.selectList(ArgumentMatchers.<Wrapper<AdminClass>>any()))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.getVisible(7L, 11L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiCode.FORBIDDEN));

        // 受众（课程 3 的负责教师）→ 可读
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any()))
                .thenReturn(List.of());
        Course owned = new Course();
        owned.setId(3L);
        when(courseService.list(ArgumentMatchers.<Wrapper<Course>>any())).thenReturn(List.of(owned));
        assertThat(service.getVisible(7L, 3L).getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("列表：无任何归属的用户得到空页，而不是全表")
    void pageVisibleEmptyScopeReturnsEmptyPage() {
        stubEmptyScope(42L);

        Page<Announcement> result = (Page<Announcement>) service.pageVisible(1, 10, 42L);
        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }
}
