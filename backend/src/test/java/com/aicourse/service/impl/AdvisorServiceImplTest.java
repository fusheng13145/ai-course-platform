package com.aicourse.service.impl;

import com.aicourse.entity.AdminClass;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.AdminClassMapper;
import com.aicourse.security.AdminClassAccessControl;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.UserService;
import com.aicourse.vo.AdvisorClassProfileVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 班主任画像服务单测（WP9，6A 口径）。
 *
 * <p>判据：验收 1「查他人班 403」（非班主任抛 403）；验收 2「画像只含聚合值」——
 * 本测试断言 VO 仅含聚合字段且数值正确，不含任何学生个人成绩字段。</p>
 */
class AdvisorServiceImplTest {

    private AdminClassMapper adminClassMapper;
    private AdminClassAccessControl adminClassAccessControl;
    private AdminScopeGuard adminScopeGuard;
    private UserService userService;
    private CourseEnrollmentService enrollmentService;
    private CourseService courseService;
    private AdvisorServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AdminClass.class);
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, CourseEnrollment.class);
    }

    @BeforeEach
    void setUp() {
        adminClassMapper = mock(AdminClassMapper.class);
        adminClassAccessControl = mock(AdminClassAccessControl.class);
        adminScopeGuard = mock(AdminScopeGuard.class);
        userService = mock(UserService.class);
        enrollmentService = mock(CourseEnrollmentService.class);
        courseService = mock(CourseService.class);
        service = new AdvisorServiceImpl(adminClassMapper, adminClassAccessControl,
                adminScopeGuard, userService, enrollmentService, courseService);
    }

    private AdminClass adminClass(Long id, Long advisorId) {
        AdminClass adminClass = new AdminClass();
        adminClass.setId(id);
        adminClass.setName("计算机 2401 班");
        adminClass.setAdvisorTeacherId(advisorId);
        return adminClass;
    }

    private User student(long id) {
        User user = new User();
        user.setId(id);
        user.setAdminClassId(6L);
        user.setIsActive(true);
        user.setFullName("学生" + id);
        return user;
    }

    private CourseEnrollment enrollment(Long studentId, BigDecimal progress, BigDecimal finalScore) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(3L);
        enrollment.setProgress(progress);
        enrollment.setFinalScore(finalScore);
        enrollment.setApprovalStatus("APPROVED");
        enrollment.setIsActive(true);
        return enrollment;
    }

    @Test
    @DisplayName("listMyClasses：只返回本人担任班主任的班级")
    void listMyClassesFiltersByAdvisor() {
        when(adminClassMapper.selectList(ArgumentMatchers.<Wrapper<AdminClass>>any()))
                .thenReturn(List.of(adminClass(6L, 9L)));

        assertThat(service.listMyClasses(9L)).hasSize(1);
        assertThat(service.listMyClasses(9L).get(0).getAdvisorTeacherId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("非班主任查画像 403（验收 1：查他人班拒绝）")
    void classProfileNonAdvisorForbidden() {
        when(adminClassAccessControl.assertAdminClassAdvisor(6L, 8L))
                .thenThrow(new BusinessException(com.aicourse.common.ApiCode.FORBIDDEN, "只有本班班主任可以访问该班级画像"));

        assertThatThrownBy(() -> service.classProfile(6L, 8L, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(com.aicourse.common.ApiCode.FORBIDDEN));
    }

    @Test
    @DisplayName("聚合画像：人数/均值/预警数正确，且不含学生个人成绩字段")
    void classProfileAggregatesOnly() {
        when(adminClassAccessControl.assertAdminClassAdvisor(6L, 9L)).thenReturn(adminClass(6L, 9L));
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any()))
                .thenReturn(List.of(student(1L), student(2L), student(3L)));
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any()))
                .thenReturn(List.of(
                        enrollment(1L, new BigDecimal("80"), new BigDecimal("55")), // 预警：finalScore < 60
                        enrollment(2L, new BigDecimal("30"), new BigDecimal("92")), // 进度低但已出分 92 → 非预警
                        enrollment(3L, new BigDecimal("90"), new BigDecimal("75"))));

        AdvisorClassProfileVO profile = service.classProfile(6L, 9L, false);

        assertThat(profile.getStudentCount()).isEqualTo(3);
        assertThat(profile.getAvgProgress()).isEqualByComparingTo(new BigDecimal("66.7"));
        assertThat(profile.getAvgFinalScore()).isEqualByComparingTo(new BigDecimal("74.0"));
        // 画像预警口径 = finalScore < 60（挂科风险），与分析侧 lowProgress||lowScore 双口径不同
        assertThat(profile.getAtRiskCount()).isEqualTo(1);
        assertThat(profile.getGradedCount()).isEqualTo(3);
        // 6A 响应体断言（机械）：VO 的字段集合就是这 7 个聚合字段——
        // 出现 studentName/studentNo/score 之类的个人字段即转红
        assertThat(java.util.Arrays.stream(AdvisorClassProfileVO.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrder("adminClassId", "className", "studentCount",
                        "avgProgress", "avgFinalScore", "atRiskCount", "gradedCount");
    }

    @Test
    @DisplayName("院系管理员查外院班级 403；本院班级放行（能力矩阵：班级画像按院系只读）")
    void classProfileCollegeAdminScope() {
        AdminClass otherCollege = adminClass(7L, null);
        otherCollege.setCollegeId(99L);
        when(adminClassMapper.selectById(7L)).thenReturn(otherCollege);
        org.mockito.Mockito.doThrow(new BusinessException(com.aicourse.common.ApiCode.FORBIDDEN, "无权访问该院系数据"))
                .when(adminScopeGuard).assertCollegeAccess(99L);

        assertThatThrownBy(() -> service.classProfile(7L, 3L, true))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(com.aicourse.common.ApiCode.FORBIDDEN));

        AdminClass ownCollege = adminClass(8L, null);
        ownCollege.setCollegeId(10L);
        when(adminClassMapper.selectById(8L)).thenReturn(ownCollege);
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of());
        assertThat(service.classProfile(8L, 3L, true).getStudentCount()).isZero();
    }

    @Test
    @DisplayName("学生预警明细：进度<40 或 总评<60 各出一条；双达标合并为一行双原因")
    void classAlertsFiltersAndExplains() {
        when(adminClassAccessControl.assertAdminClassAdvisor(6L, 9L)).thenReturn(adminClass(6L, 9L));
        when(userService.list(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<User>>any()))
                .thenReturn(List.of(student(1L)));
        com.aicourse.entity.Course course = new com.aicourse.entity.Course();
        course.setId(3L);
        course.setName("计算机网络");
        when(courseService.list(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aicourse.entity.Course>>any()))
                .thenReturn(List.of(course));
        when(enrollmentService.list(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<CourseEnrollment>>any()))
                .thenReturn(List.of(
                        enrollment(1L, new BigDecimal("30"), new BigDecimal("92")),  // 低进度
                        enrollment(1L, new BigDecimal("80"), new BigDecimal("55")),  // 低分
                        enrollment(1L, new BigDecimal("90"), new BigDecimal("75")))); // 正常

        List<Map<String, Object>> alerts = service.classAlerts(6L, 9L, false);

        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).get("reasons")).isEqualTo("进度不足40%");
        assertThat(alerts.get(0).get("courseName")).isEqualTo("计算机网络");
        assertThat(alerts.get(1).get("reasons")).isEqualTo("总评不及格");
    }

    @Test
    @DisplayName("班级无在册学生：画像零值而非异常")
    void classProfileEmptyClass() {
        when(adminClassAccessControl.assertAdminClassAdvisor(6L, 9L)).thenReturn(adminClass(6L, 9L));
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of());

        AdvisorClassProfileVO profile = service.classProfile(6L, 9L, false);
        assertThat(profile.getStudentCount()).isZero();
        assertThat(profile.getAvgProgress()).isNull();
        assertThat(profile.getAtRiskCount()).isZero();
    }
}
