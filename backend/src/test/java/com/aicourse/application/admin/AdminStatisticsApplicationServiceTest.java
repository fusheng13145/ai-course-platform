package com.aicourse.application.admin;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.User;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.SystemService;
import com.aicourse.service.UserService;
import com.aicourse.vo.CourseStatisticsVO;
import com.aicourse.vo.StatisticsOverviewVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 3E-05A：{@code AdminStatisticsApplicationService} 用例。
 *
 * <p>重点锁两件事：①「校级 / 院系」双口径的分支选择（{@code currentCollegeFilter} 一次取值后
 * 所有依赖都必须走同一口径）；② 院系无课程时的短路（选课数、活跃课程数为 0 且不查库）。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminStatisticsApplicationServiceTest {

    @Mock
    private SystemService systemService;
    @Mock
    private UserService userService;
    @Mock
    private CourseService courseService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private CourseEnrollmentService enrollmentService;
    @Mock
    private AdminScopeGuard adminScopeGuard;

    @InjectMocks
    private AdminStatisticsApplicationService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Course.class);
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, CourseEnrollment.class);
    }

    private static Course course(Long id, String category, Boolean active) {
        Course c = new Course();
        c.setId(id);
        c.setCategory(category);
        c.setIsActive(active);
        return c;
    }

    // ===================== overview =====================

    @Test
    @DisplayName("概览-校级口径：全校计数 + 三角色分布 + 全站活跃课程")
    void overviewSchoolWide() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(null);
        when(userService.count()).thenReturn(100L);
        when(courseService.count()).thenReturn(20L);
        when(enrollmentService.count()).thenReturn(500L);
        when(courseService.count(ArgumentMatchers.<LambdaQueryWrapper<Course>>any())).thenReturn(12L);
        when(userService.count(ArgumentMatchers.<LambdaQueryWrapper<User>>any()))
                .thenReturn(80L, 15L, 5L);

        StatisticsOverviewVO vo = service.overview();

        assertEquals(100L, vo.getTotalUsers());
        assertEquals(20L, vo.getTotalCourses());
        assertEquals(500L, vo.getTotalEnrollments());
        assertEquals(12L, vo.getActiveCourses());
        assertEquals(80L, vo.getRoleDistribution().getStudent());
        assertEquals(15L, vo.getRoleDistribution().getTeacher());
        assertEquals(5L, vo.getRoleDistribution().getAdmin());
    }

    @Test
    @DisplayName("概览-院系口径：课程数取本院集合大小，选课按 IN 课程ID")
    void overviewCollegeScoped() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(7L);
        when(courseService.list(ArgumentMatchers.<LambdaQueryWrapper<Course>>any()))
                .thenReturn(List.of(course(1L, "必修", true), course(2L, "选修", false)));
        when(userService.count(ArgumentMatchers.<LambdaQueryWrapper<User>>any()))
                .thenReturn(30L, 25L, 4L, 1L);
        when(enrollmentService.count(ArgumentMatchers.<LambdaQueryWrapper<CourseEnrollment>>any()))
                .thenReturn(90L);
        when(courseService.count(ArgumentMatchers.<LambdaQueryWrapper<Course>>any())).thenReturn(1L);

        StatisticsOverviewVO vo = service.overview();

        assertEquals(30L, vo.getTotalUsers());
        assertEquals(2L, vo.getTotalCourses());
        assertEquals(90L, vo.getTotalEnrollments());
        assertEquals(1L, vo.getActiveCourses());
        assertEquals(25L, vo.getRoleDistribution().getStudent());
        assertEquals(4L, vo.getRoleDistribution().getTeacher());
        assertEquals(1L, vo.getRoleDistribution().getAdmin());
        // 院系口径不得出现全校 count()
        verify(userService, never()).count();
        verify(courseService, never()).count();
    }

    @Test
    @DisplayName("概览-院系口径且本院无课程：选课数与活跃数直接 0，不查库")
    void overviewCollegeWithoutCourses() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(9L);
        when(courseService.list(ArgumentMatchers.<LambdaQueryWrapper<Course>>any())).thenReturn(List.of());
        when(userService.count(ArgumentMatchers.<LambdaQueryWrapper<User>>any())).thenReturn(0L, 0L, 0L, 0L);

        StatisticsOverviewVO vo = service.overview();

        assertEquals(0L, vo.getTotalCourses());
        assertEquals(0L, vo.getTotalEnrollments());
        assertEquals(0L, vo.getActiveCourses());
        verify(enrollmentService, never()).count(any());
        verify(courseService, never()).count(ArgumentMatchers.<LambdaQueryWrapper<Course>>any());
    }

    // ===================== 趋势与课程统计 =====================

    @Test
    @DisplayName("用户增长-按口径下传 days 与学院")
    void userGrowthDelegates() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(null);
        when(analyticsService.getUserGrowthTrend(7)).thenReturn(List.of());
        assertTrue(service.userGrowth(7).isEmpty());

        when(adminScopeGuard.currentCollegeFilter()).thenReturn(3L);
        when(analyticsService.getUserGrowthTrend(7, 3L)).thenReturn(List.of());
        assertTrue(service.userGrowth(7).isEmpty());

        verify(analyticsService, times(1)).getUserGrowthTrend(7);
        verify(analyticsService, times(1)).getUserGrowthTrend(7, 3L);
    }

    @Test
    @DisplayName("课程统计-校级口径：总量取 count，分类分布按 category 分组并忽略空值，热门限 5 条")
    void courseStatisticsSchoolWide() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(null);
        when(courseService.count()).thenReturn(6L);
        when(courseService.count(ArgumentMatchers.<LambdaQueryWrapper<Course>>any())).thenReturn(4L);
        when(courseService.list()).thenReturn(List.of(
                course(1L, "必修", true), course(2L, "必修", true),
                course(3L, "选修", false), course(4L, null, true)));
        when(analyticsService.getCourseRanking()).thenReturn(List.of(
                Map.of("id", 1), Map.of("id", 2), Map.of("id", 3),
                Map.of("id", 4), Map.of("id", 5), Map.of("id", 6)));

        CourseStatisticsVO vo = service.courseStatistics();

        assertEquals(6L, vo.getTotalCourses());
        assertEquals(4L, vo.getActiveCourses());
        assertEquals(2L, vo.getCategoryDistribution().get("必修"));
        assertEquals(1L, vo.getCategoryDistribution().get("选修"));
        assertEquals(2, vo.getCategoryDistribution().size());
        assertEquals(5, vo.getTopCourses().size());
        verify(analyticsService, never()).getCourseRanking(ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("课程统计-院系口径：活跃数按实体 isActive 统计，总量取本院集合大小")
    void courseStatisticsCollegeScoped() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(5L);
        when(courseService.list(ArgumentMatchers.<LambdaQueryWrapper<Course>>any()))
                .thenReturn(List.of(course(1L, "必修", true), course(2L, "必修", false), course(3L, null, null)));
        when(analyticsService.getCourseRanking(5L)).thenReturn(List.of(Map.of("id", 1)));

        CourseStatisticsVO vo = service.courseStatistics();

        assertEquals(3L, vo.getTotalCourses());
        assertEquals(1L, vo.getActiveCourses());
        assertEquals(2L, vo.getCategoryDistribution().get("必修"));
        assertEquals(1, vo.getTopCourses().size());
        verify(courseService, never()).count();
        verify(analyticsService, never()).getCourseRanking();
    }

    // ===================== AI 用量四维度 =====================

    @Test
    @DisplayName("AI 用量四维度-校级口径均走无参重载")
    void aiUsageSchoolWide() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(null);
        when(systemService.getAIUsageStats()).thenReturn(Map.of("total", 1));
        when(systemService.getAIUsageByCourse(30)).thenReturn(Map.of("rows", 2));
        when(systemService.getAIUsageByDay(30)).thenReturn(Map.of("rows", 3));
        when(systemService.getAIUsageByScene(30)).thenReturn(Map.of("rows", 4));

        assertEquals(1, service.aiUsage().get("total"));
        assertEquals(2, service.aiUsageByCourse(30).get("rows"));
        assertEquals(3, service.aiUsageByDay(30).get("rows"));
        assertEquals(4, service.aiUsageByScene(30).get("rows"));

        verify(systemService, never()).getAIUsageStats(ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("AI 用量四维度-院系口径均带学院，且不回落全校")
    void aiUsageCollegeScoped() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(2L);
        when(systemService.getAIUsageStats(2L)).thenReturn(Map.of("total", 1));
        when(systemService.getAIUsageByCourse(30, 2L)).thenReturn(Map.of("rows", 2));
        when(systemService.getAIUsageByDay(30, 2L)).thenReturn(Map.of("rows", 3));
        when(systemService.getAIUsageByScene(30, 2L)).thenReturn(Map.of("rows", 4));

        service.aiUsage();
        service.aiUsageByCourse(30);
        service.aiUsageByDay(30);
        service.aiUsageByScene(30);

        verify(systemService).getAIUsageStats(2L);
        verify(systemService).getAIUsageByCourse(30, 2L);
        verify(systemService).getAIUsageByDay(30, 2L);
        verify(systemService).getAIUsageByScene(30, 2L);
        verify(systemService, never()).getAIUsageStats();
        verify(systemService, never()).getAIUsageByCourse(anyInt());
        verify(systemService, never()).getAIUsageByDay(anyInt());
        verify(systemService, never()).getAIUsageByScene(anyInt());
    }

    // ===================== 导出 =====================

    @Test
    @DisplayName("导出-报表键名与嵌套结构保持原样")
    void exportReportShape() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(null);
        when(analyticsService.getDashboard()).thenReturn(Map.of("k", "v"));
        when(systemService.getAIUsageStats()).thenReturn(Map.of("total", 1));
        when(courseService.list()).thenReturn(List.of(course(1L, "必修", true)));
        when(userService.count()).thenReturn(10L);
        when(userService.count(ArgumentMatchers.<LambdaQueryWrapper<User>>any()))
                .thenReturn(8L, 2L);

        Map<String, Object> report = service.exportReport();

        assertEquals(Map.of("k", "v"), report.get("dashboard"));
        assertEquals(Map.of("total", 1), report.get("aiUsage"));
        assertEquals("comprehensive", report.get("reportType"));
        assertEquals("completed", report.get("status"));
        assertTrue(report.containsKey("exportTime"));

        @SuppressWarnings("unchecked")
        Map<String, Object> courseStats = (Map<String, Object>) report.get("courseStatistics");
        // 导出报表里 totalCourses 沿用原实现（int），键名与类型都不改
        assertEquals(1, courseStats.get("totalCourses"));
        assertEquals(1L, courseStats.get("activeCourses"));

        @SuppressWarnings("unchecked")
        Map<String, Object> userStats = (Map<String, Object>) report.get("userStatistics");
        assertEquals(10L, userStats.get("totalUsers"));
        assertEquals(8L, userStats.get("students"));
        assertEquals(2L, userStats.get("teachers"));

        verify(userService).count();
        verify(userService, times(2)).count(ArgumentMatchers.<LambdaQueryWrapper<User>>any());
    }

    @Test
    @DisplayName("导出-院系口径：报表内所有统计都收敛到本院")
    void exportReportCollegeScoped() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(4L);
        when(analyticsService.getDashboard(4L)).thenReturn(Map.of("k", "college"));
        when(systemService.getAIUsageStats(4L)).thenReturn(Map.of("total", 5));
        when(courseService.list(ArgumentMatchers.<LambdaQueryWrapper<Course>>any()))
                .thenReturn(List.of(course(1L, "选修", true), course(2L, "选修", false)));
        when(userService.count(ArgumentMatchers.<LambdaQueryWrapper<User>>any()))
                .thenReturn(20L, 18L, 2L);

        Map<String, Object> report = service.exportReport();

        assertEquals(Map.of("k", "college"), report.get("dashboard"));
        assertEquals(Map.of("total", 5), report.get("aiUsage"));

        @SuppressWarnings("unchecked")
        Map<String, Object> courseStats = (Map<String, Object>) report.get("courseStatistics");
        assertEquals(2, courseStats.get("totalCourses"));
        assertEquals(1L, courseStats.get("activeCourses"));

        verify(analyticsService, never()).getDashboard();
        verify(systemService, never()).getAIUsageStats();
    }

    @Test
    @DisplayName("口径只取一次：同一请求内不重复查询学院归属")
    void collegeFilterResolvedOnce() {
        lenient().when(adminScopeGuard.currentCollegeFilter()).thenReturn(null);
        lenient().when(courseService.list()).thenReturn(List.of());

        service.courseStatistics();

        verify(adminScopeGuard, times(1)).currentCollegeFilter();
        verify(courseService, times(1)).list();
    }
}
