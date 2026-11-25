package com.aicourse.application.course;

import com.aicourse.common.ApiCode;
import com.aicourse.dto.CourseSaveDTO;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseDefinition;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.CourseApprovalService;
import com.aicourse.service.CourseDefinitionService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.vo.CourseStatsVO;
import com.aicourse.vo.CourseVOs;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课程用例编排（3E-04B）。
 *
 * <p>盯 V13 三层权限模型不被改歪：教师=本人课程、院系管理员=本院、校级管理员=不限；
 * 以及两条埋在流程里的口径（管理员代建必须显式 teacherId、列表他院请求 403）。</p>
 */
class CourseApplicationServiceTest {

    private CourseService courseService;
    private CourseEnrollmentService enrollmentService;
    private CourseApprovalService courseApprovalService;
    private CourseDefinitionService definitionService;
    private AdminScopeGuard adminScopeGuard;

    private CourseApplicationService service;
    private Authentication teacher;
    private Authentication admin;

    @BeforeEach
    void setUp() {
        courseService = mock(CourseService.class);
        enrollmentService = mock(CourseEnrollmentService.class);
        courseApprovalService = mock(CourseApprovalService.class);
        definitionService = mock(CourseDefinitionService.class);
        adminScopeGuard = mock(AdminScopeGuard.class);
        service = new CourseApplicationService(courseService, enrollmentService, courseApprovalService,
                definitionService, adminScopeGuard);
        teacher = new UsernamePasswordAuthenticationToken(7L, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
        admin = new UsernamePasswordAuthenticationToken(9L, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Course.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CourseEnrollment.class);
    }

    // ------------------------------------------------ 列表与学院范围

    @Test
    @DisplayName("列表：院系管理员强制本院；显式请求他院 403 且不查库")
    void list_collegeAdminEnforcesOwnCollege() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(100L);
        when(courseService.page(any(), any())).thenReturn(pageOf(course(1L, 7L, 100L)));

        service.list(1, 10, null, null, null, null, null, null);
        ArgumentCaptor<Wrapper<Course>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(courseService).page(any(), captor.capture());
        assertTrue(wrapperParams(captor.getValue()).contains(100L), "院系管理员必须强制本院过滤");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.list(1, 10, null, null, null, 200L, null, null));
        assertEquals(ApiCode.FORBIDDEN, e.getCode());
    }

    @Test
    @DisplayName("列表：校级管理员可按参数筛学院")
    void list_schoolAdminFiltersByParam() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(null);
        when(courseService.page(any(), any())).thenReturn(pageOf(course(1L, 7L, 100L)));

        service.list(1, 10, null, null, null, 100L, null, null);

        ArgumentCaptor<Wrapper<Course>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(courseService).page(any(), captor.capture());
        assertTrue(wrapperParams(captor.getValue()).contains(100L));
    }

    @Test
    @DisplayName("详情：走学院访问守卫（院系管理员受限）")
    void detail_usesCollegeGuard() {
        Course c = course(1L, 7L, 100L);
        when(courseService.getById(1L)).thenReturn(c);

        assertEquals(1L, service.detail(1L).getId().longValue());
        verify(adminScopeGuard).assertCourseAccess(c);
    }

    // ------------------------------------------------ 创建 / 更新

    @Test
    @DisplayName("创建（教师）：teacherId 取登录身份，忽略请求体里的 teacherId")
    void create_teacherUsesPrincipalId() {
        CourseSaveDTO dto = new CourseSaveDTO();
        dto.setDefinitionId(1L);
        dto.setTeacherId(99L);
        Course saved = course(5L, 7L, 100L);
        when(courseService.createCourse(any(), eq(7L))).thenReturn(saved);

        assertEquals(5L, service.create(dto, teacher).getId().longValue());
        verify(courseApprovalService).applyOwnerCollege(any(), eq(7L));
        verify(courseApprovalService).recordSubmission(eq(5L), eq(7L));
        InOrder order = inOrder(courseService, courseApprovalService);
        order.verify(courseService).createCourse(any(), eq(7L));
        order.verify(courseApprovalService).recordSubmission(eq(5L), eq(7L));
    }

    @Test
    @DisplayName("创建（管理员）：缺 teacherId 400，且不落库")
    void create_adminWithoutTeacherIs400() {
        CourseSaveDTO dto = new CourseSaveDTO();
        dto.setDefinitionId(1L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(dto, admin));
        assertEquals(ApiCode.BAD_REQUEST, e.getCode());
        verify(courseService, never()).createCourse(any(), any());
    }

    @Test
    @DisplayName("创建（管理员）：课程库定义属他院先 403，不落库")
    void create_adminForeignDefinitionIs403BeforeCreate() {
        CourseSaveDTO dto = new CourseSaveDTO();
        dto.setDefinitionId(1L);
        dto.setTeacherId(99L);
        CourseDefinition definition = new CourseDefinition();
        definition.setId(1L);
        definition.setCollegeId(200L);
        when(definitionService.getById(1L)).thenReturn(definition);
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "跨学院"))
                .when(adminScopeGuard).assertCollegeAccess(200L);

        assertThrows(BusinessException.class, () -> service.create(dto, admin));
        verify(courseService, never()).createCourse(any(), any());
    }

    @Test
    @DisplayName("更新：非院系管理员不做额外取库校验，直接委托服务层")
    void update_plainFlowDelegatesToService() {
        CourseSaveDTO dto = new CourseSaveDTO();
        when(adminScopeGuard.isCollegeAdmin()).thenReturn(false);
        when(courseService.updateCourse(eq(1L), any(), eq(7L), eq(false))).thenReturn(course(1L, 7L, 100L));

        assertEquals(1L, service.update(1L, dto, teacher).getId().longValue());
        verify(courseService, never()).getById(any());
    }

    @Test
    @DisplayName("更新：院系管理员先校验课程存在与学院范围")
    void update_collegeAdminChecksScope() {
        CourseSaveDTO dto = new CourseSaveDTO();
        Course existing = course(1L, 7L, 100L);
        when(adminScopeGuard.isCollegeAdmin()).thenReturn(true);
        when(courseService.getById(1L)).thenReturn(existing);
        when(courseService.updateCourse(eq(1L), any(), eq(9L), eq(true))).thenReturn(existing);

        service.update(1L, dto, admin);

        InOrder order = inOrder(courseService, adminScopeGuard);
        order.verify(courseService).getById(1L);
        order.verify(adminScopeGuard).assertCourseAccess(existing);
        order.verify(courseService).updateCourse(eq(1L), any(), eq(9L), eq(true));
    }

    // ------------------------------------------------ 归属守卫

    @Test
    @DisplayName("归属：教师操作他人课程 403，且不触达写操作")
    void ownerGuard_rejectsOtherTeachersCourse() {
        when(courseService.getById(1L)).thenReturn(course(1L, 8L, 100L));

        BusinessException del = assertThrows(BusinessException.class, () -> service.delete(1L, teacher));
        assertEquals(ApiCode.FORBIDDEN, del.getCode());
        BusinessException tog = assertThrows(BusinessException.class, () -> service.toggle(1L, teacher));
        assertEquals(ApiCode.FORBIDDEN, tog.getCode());
        BusinessException sub = assertThrows(BusinessException.class, () -> service.resubmit(1L, teacher));
        assertEquals(ApiCode.FORBIDDEN, sub.getCode());

        verify(courseService, never()).deleteCourseById(any());
        verify(courseService, never()).toggleCourseStatus(any());
        verify(courseApprovalService, never()).resubmit(any(), any());
    }

    @Test
    @DisplayName("归属：课程不存在 404")
    void ownerGuard_missingCourseIs404() {
        when(courseService.getById(1L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.delete(1L, teacher));
        assertEquals(ApiCode.NOT_FOUND, e.getCode());
    }

    @Test
    @DisplayName("归属：管理员路径转学院校验（非院系管理员直接放行）")
    void ownerGuard_adminDefersToCollegeScope() {
        when(adminScopeGuard.isCollegeAdmin()).thenReturn(false);

        service.delete(1L, admin);

        verify(courseService, never()).getById(any());
        verify(courseService).deleteCourseById(1L);
    }

    @Test
    @DisplayName("审批：管理员路径先判学院范围；校级管理员不额外取库")
    void approve_checksCollegeScopeOnlyForCollegeAdmin() {
        when(adminScopeGuard.isCollegeAdmin()).thenReturn(false);
        when(courseApprovalService.approve(1L, 9L)).thenReturn(course(1L, 7L, 100L));

        service.approve(1L, admin);

        verify(courseService, never()).getById(any());
        verify(courseApprovalService).approve(1L, 9L);
    }

    @Test
    @DisplayName("驳回：reason 原样透传（null 也透传，由服务层决定文案）")
    void reject_passesReasonThrough() {
        when(adminScopeGuard.isCollegeAdmin()).thenReturn(false);
        when(courseApprovalService.reject(1L, 9L, null)).thenReturn(course(1L, 7L, 100L));

        service.reject(1L, null, admin);

        verify(courseApprovalService).reject(1L, 9L, null);
    }

    // ------------------------------------------------ 统计与选课

    @Test
    @DisplayName("统计：课程不存在返回 404（改造前落默认 400，本批改正）")
    void stats_missingCourseIs404() {
        // 第一次取库用于归属校验，第二次取库用于读课程状态 —— 第二次返回 null 触发 404
        when(courseService.getById(1L)).thenReturn(course(1L, 7L, 100L), (Course) null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.stats(1L, teacher));
        assertEquals(ApiCode.NOT_FOUND, e.getCode());
        verify(enrollmentService, never()).count(any());
    }

    @Test
    @DisplayName("统计：已通过学员与待审核各查一次，返回六字段")
    void stats_countsActiveAndPending() {
        when(courseService.getById(1L)).thenReturn(course(1L, 7L, 100L));
        when(enrollmentService.count(any())).thenReturn(3L, 2L);

        CourseStatsVO stats = service.stats(1L, teacher);

        assertEquals(3L, stats.getTotalStudents());
        assertEquals(2L, stats.getPendingStudents());
        assertEquals("ACTIVE", stats.getStatus());
        verify(enrollmentService, times(2)).count(any());
    }

    @Test
    @DisplayName("已报名课程：条件是本人 + 有效 + APPROVED")
    void enrolledCourses_filtersOwnActiveApproved() {
        when(enrollmentService.page(any(), any())).thenReturn(pageOfEnrollment());

        service.enrolledCourses(teacher, 1, 10);

        ArgumentCaptor<Wrapper<CourseEnrollment>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(enrollmentService).page(any(), captor.capture());
        Collection<Object> params = wrapperParams(captor.getValue());
        assertTrue(params.contains(7L), "必须按本人过滤，实际=" + params);
        assertTrue(params.contains(true), "必须只取有效记录，实际=" + params);
        assertTrue(params.contains("APPROVED"), "必须只取已通过记录，实际=" + params);
    }

    @Test
    @DisplayName("我的课程：按登录身份过滤")
    void myCourses_filtersByTeacherId() {
        when(courseService.page(any(), any())).thenReturn(pageOf(course(1L, 7L, 100L)));

        service.myCourses(teacher, 1, 10);

        ArgumentCaptor<Wrapper<Course>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(courseService).page(any(), captor.capture());
        assertTrue(wrapperParams(captor.getValue()).contains(7L));
    }

    @Test
    @DisplayName("学生名单：教师仅限本人课程")
    void students_requiresOwnership() {
        when(courseService.getById(1L)).thenReturn(course(1L, 8L, 100L));

        assertThrows(BusinessException.class, () -> service.students(1L, 1, 10, teacher));
        verify(enrollmentService, never()).page(any(), any());
    }

    // ------------------------------------------------ helpers

    private static Course course(Long id, Long teacherId, Long collegeId) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        c.setCollegeId(collegeId);
        c.setStatus("ACTIVE");
        c.setApprovalStatus("APPROVED");
        return c;
    }

    private static Page<Course> pageOf(Course record) {
        Page<Course> page = new Page<>(1, 10);
        page.setRecords(List.of(record));
        page.setTotal(1);
        return page;
    }

    private static Page<CourseEnrollment> pageOfEnrollment() {
        Page<CourseEnrollment> page = new Page<>(1, 10);
        page.setRecords(List.of(new CourseEnrollment()));
        page.setTotal(1);
        return page;
    }

    private static Collection<Object> wrapperParams(Wrapper<?> wrapper) {
        AbstractWrapper<?, ?, ?> aw = (AbstractWrapper<?, ?, ?>) wrapper;
        aw.getSqlSegment();
        return aw.getParamNameValuePairs().values();
    }
}
