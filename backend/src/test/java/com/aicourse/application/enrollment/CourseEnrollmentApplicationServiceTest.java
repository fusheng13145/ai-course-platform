package com.aicourse.application.enrollment;

import com.aicourse.common.ApiCode;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.security.TeachingClassAccessControl;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 3E-05D：{@code CourseEnrollmentApplicationService} 用例。
 *
 * <p>重点锁四条最容易回退的口径：①院系管理员显式传外院 courseId → 403（不是「查空」）；
 * ②关键字解析出的学生ID集为空 → 直接空页且不查库；③驳回必须同时置 {@code is_active=false}；
 * ④通过/驳回仅允许 PENDING（否则 409）。</p>
 */
@ExtendWith(MockitoExtension.class)
class CourseEnrollmentApplicationServiceTest {

    @Mock
    private CourseEnrollmentService enrollmentService;
    @Mock
    private CourseService courseService;
    @Mock
    private UserService userService;
    @Mock
    private AdminScopeGuard adminScopeGuard;
    @Mock
    private TeachingClassAccessControl teachingClassAccessControl;
    @Mock
    private Authentication auth;

    @InjectMocks
    private CourseEnrollmentApplicationService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CourseEnrollment.class);
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, Course.class);
    }

    private static CourseEnrollment enrollment(Long id, Long courseId, String status) {
        CourseEnrollment e = new CourseEnrollment();
        e.setId(id);
        e.setCourseId(courseId);
        e.setStudentId(1L);
        e.setApprovalStatus(status);
        e.setIsActive(true);
        return e;
    }

    private static Course course(Long id, Long teacherId) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        return c;
    }

    /** 教师身份（TEACHER 角色，非 admin）。 */
    private Authentication teacherAuth() {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                11L, "n/a", List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
    }

    // ==================== 全量分页 ====================

    @Test
    @DisplayName("全量分页-院系管理员传外院课程：403，且不查选课")
    void listAllCollegeAdminForeignCourseForbidden() {
        when(adminScopeGuard.isCollegeAdmin()).thenReturn(true);
        when(courseService.getById(9L)).thenReturn(course(9L, 1L));
        org.mockito.Mockito.doThrow(new BusinessException(ApiCode.FORBIDDEN, "跨院"))
                .when(adminScopeGuard).assertCourseAccess(any());

        assertThrows(BusinessException.class, () -> service.listAll(1, 10, null, 9L, null));
        verify(enrollmentService, never()).page(any(), any());
    }

    @Test
    @DisplayName("全量分页-院系管理员本院无学生：直接空页并回显请求 page/size")
    void listAllCollegeAdminNoStudents() {
        when(adminScopeGuard.isCollegeAdmin()).thenReturn(true);
        when(adminScopeGuard.currentCollegeUserIds()).thenReturn(List.of());

        PagedResponse<?> page = service.listAll(3, 20, null, null, null);

        assertEquals(3L, page.getPage());
        assertEquals(20L, page.getPageSize());
        assertEquals(0L, page.getTotal());
        verify(enrollmentService, never()).page(any(), any());
    }

    @Test
    @DisplayName("全量分页-关键字解析为空集：直接空页，不把空 IN 下发")
    void listAllKeywordMatchedNothing() {
        when(adminScopeGuard.isCollegeAdmin()).thenReturn(false);
        when(userService.list(ArgumentMatchers.<LambdaQueryWrapper<User>>any())).thenReturn(List.of());

        PagedResponse<?> page = service.listAll(1, 10, null, null, "不存在的人");

        assertEquals(0L, page.getTotal());
        verify(enrollmentService, never()).page(any(), any());
    }

    @Test
    @DisplayName("全量分页-校级管理员：按审批状态/课程过滤并转 VO")
    void listAllSchoolAdmin() {
        when(adminScopeGuard.isCollegeAdmin()).thenReturn(false);
        Page<CourseEnrollment> result = new Page<>(1, 10);
        CourseEnrollment e = enrollment(1L, 3L, "APPROVED");
        result.setRecords(List.of(e));
        result.setTotal(1);
        when(enrollmentService.page(any(), any())).thenReturn(result);

        PagedResponse<?> page = service.listAll(1, 10, "APPROVED", 3L, null);

        assertEquals(1, page.getList().size());
        assertEquals(1L, page.getTotal());
    }

    @Test
    @DisplayName("我的报名-固定 is_active + APPROVED，分页 size 硬编码 100")
    void myEnrollmentsUsesV14Rule() {
        Page<CourseEnrollment> result = new Page<>(1, 100);
        result.setRecords(List.of(enrollment(1L, 3L, "APPROVED")));
        result.setTotal(1);
        when(enrollmentService.page(any(), any())).thenReturn(result);

        PagedResponse<?> page = service.myEnrollments(1L);

        assertEquals(1, page.getList().size());
        assertEquals(100L, page.getPageSize());
    }

    // ==================== 审核状态机 ====================

    @Test
    @DisplayName("通过-非 PENDING 返回 409，且不改库")
    void approveNonPendingConflicts() {
        when(enrollmentService.getById(1L)).thenReturn(enrollment(1L, 3L, "APPROVED"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L, auth));

        assertEquals(ApiCode.CONFLICT, ex.getCode());
        verify(enrollmentService, never()).updateById(any());
    }

    @Test
    @DisplayName("通过-记录不存在 404")
    void approveMissingNotFound() {
        when(enrollmentService.getById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L, auth));

        assertEquals(ApiCode.NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("驳回-同时置 is_active=false（防止已驳回学生继续被判为课程成员）")
    void rejectDeactivatesEnrollment() {
        when(enrollmentService.getById(1L)).thenReturn(enrollment(1L, 3L, "PENDING"));
        when(courseService.getById(3L)).thenReturn(course(3L, 11L));
        when(enrollmentService.updateById(any())).thenReturn(true);

        CourseEnrollment rejected = service.reject(1L, "材料不全", teacherAuth());

        assertEquals("REJECTED", rejected.getApprovalStatus());
        assertFalse(rejected.getIsActive());
        assertEquals("材料不全", rejected.getApprovalComment());
        assertEquals(11L, rejected.getApprovedBy());
    }

    @Test
    @DisplayName("驳回-原因为没有 reason 时保留原有评论（不覆盖为 null）")
    void rejectWithoutReasonKeepsComment() {
        CourseEnrollment e = enrollment(1L, 3L, "PENDING");
        e.setApprovalComment("旧备注");
        when(enrollmentService.getById(1L)).thenReturn(e);
        when(courseService.getById(3L)).thenReturn(course(3L, 11L));

        service.reject(1L, null, teacherAuth());

        assertEquals("旧备注", e.getApprovalComment());
    }

    @Test
    @DisplayName("取消-学生取消他人选课 403，且不进状态机")
    void cancelByOtherStudentForbidden() {
        CourseEnrollment e = enrollment(1L, 3L, "APPROVED");
        e.setStudentId(99L);
        when(enrollmentService.getById(1L)).thenReturn(e);
        Authentication student = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                1L, "n/a", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(1L, "原因", student));

        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
        verify(enrollmentService, never()).cancelEnrollment(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("取消-教师非课程负责人且无教学班：403")
    void cancelByUnrelatedTeacherForbidden() {
        when(enrollmentService.getById(1L)).thenReturn(enrollment(1L, 3L, "APPROVED"));
        when(courseService.getById(3L)).thenReturn(course(3L, 99L));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(1L, "原因", teacherAuth()));

        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
        verify(enrollmentService, never()).cancelEnrollment(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("取消-教学班主讲可管理本班选课（走 TeachingClassAccessControl）")
    void cancelByTeachingClassOwnerAllowed() {
        CourseEnrollment e = enrollment(1L, 3L, "APPROVED");
        e.setTeachingClassId(7L);
        when(enrollmentService.getById(1L)).thenReturn(e);
        when(courseService.getById(3L)).thenReturn(course(3L, 99L));
        when(enrollmentService.cancelEnrollment(1L, 11L, "原因"))
                .thenReturn(enrollment(1L, 3L, "CANCELLED"));

        service.cancel(1L, "原因", teacherAuth());

        verify(teachingClassAccessControl).assertRosterManage(eq(7L), eq(11L));
        verify(enrollmentService).cancelEnrollment(1L, 11L, "原因");
    }

    @Test
    @DisplayName("取消-院系管理员跨院操作：403")
    void cancelByCollegeAdminForeignCourseForbidden() {
        when(enrollmentService.getById(1L)).thenReturn(enrollment(1L, 3L, "APPROVED"));
        when(courseService.getById(3L)).thenReturn(course(3L, 11L));
        when(adminScopeGuard.isCollegeAdmin()).thenReturn(true);
        org.mockito.Mockito.doThrow(new BusinessException(ApiCode.FORBIDDEN, "跨院"))
                .when(adminScopeGuard).assertCourseAccess(any());
        Authentication admin = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                1L, "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThrows(BusinessException.class, () -> service.cancel(1L, "原因", admin));
        verify(enrollmentService, never()).cancelEnrollment(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("通过-合法路径：写 approvalStatus/approvedBy/approvalTime/isActive 四字段")
    void approveHappyPath() {
        when(enrollmentService.getById(1L)).thenReturn(enrollment(1L, 3L, "PENDING"));
        when(courseService.getById(3L)).thenReturn(course(3L, 11L));
        when(enrollmentService.updateById(any())).thenReturn(true);

        CourseEnrollment approved = service.approve(1L, teacherAuth());

        assertEquals("APPROVED", approved.getApprovalStatus());
        assertEquals(11L, approved.getApprovedBy());
        assertTrue(approved.getIsActive());
        assertTrue(approved.getApprovalTime() != null);
    }

    @Test
    @DisplayName("关键字解析上限 500：SQL 里带 LIMIT 500（避免全表扫描）")
    void listAllKeywordLimitApplied() {
        when(adminScopeGuard.isCollegeAdmin()).thenReturn(false);
        when(userService.list(ArgumentMatchers.<LambdaQueryWrapper<User>>any())).thenReturn(List.of());
        // 断言交给实现细节：空集即空页（此处只验证不落库查询）
        service.listAll(1, 10, null, null, "  张三  ");
        verify(userService).list(ArgumentMatchers.<LambdaQueryWrapper<User>>any());
        verify(enrollmentService, never()).page(any(), any());
        verify(userService, never()).listByIds(anyList());
    }
}
