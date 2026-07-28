package com.aicourse.controller;
import com.aicourse.controller.AnalyticsController;
import com.aicourse.service.AnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * AnalyticsController 接口测试（升级版）。
 * 原生成器弱断言（status != 401 && status != 403）已全部替换为真实业务断言：
 * 对依赖的 AnalyticsService 方法打桩返回合理业务对象，断言 200/code=200，并校验关键字段透传、verify 路由委托。
 * AnalyticsController 各方法均无 @PreAuthorize，仅需认证，故不新增越权 403 测试。
 */
@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.AnalyticsService analyticsService;

    @Test
    @DisplayName("GET /analytics/dashboard?teacherId=1 - 返回200且教师仪表盘数据透传")
    void dashboard_authorized_returns_200() throws Exception {
        when(analyticsService.getTeacherDashboard(1L)).thenReturn(Map.of("courseCount", 5, "studentCount", 120));
        mockMvc.perform(get("/analytics/dashboard?teacherId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.courseCount").value(5));
        verify(analyticsService).getTeacherDashboard(eq(1L));
    }


    @Test
    @DisplayName("GET /analytics/dashboard?teacherId=1 - 未认证返回401")
    void dashboard_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/analytics/dashboard?teacherId=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /analytics/overview - 返回200且平台总览数据透传")
    void platformOverview_authorized_returns_200() throws Exception {
        when(analyticsService.getPlatformOverview()).thenReturn(Map.of("activeUsers", 50, "completionRate", 0.78));
        mockMvc.perform(get("/analytics/overview").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.activeUsers").value(50));
        verify(analyticsService).getPlatformOverview();
    }


    @Test
    @DisplayName("GET /analytics/overview - 未认证返回401")
    void platformOverview_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/analytics/overview"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /analytics/students/1 - 返回200且学生仪表盘数据透传")
    void studentDashboard_authorized_returns_200() throws Exception {
        when(analyticsService.getUserDashboard(1L)).thenReturn(Map.of("courseCount", 3, "avgProgress", 0.65));
        mockMvc.perform(get("/analytics/students/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.courseCount").value(3));
        verify(analyticsService).getUserDashboard(eq(1L));
    }


    @Test
    @DisplayName("GET /analytics/students/1 - 未认证返回401")
    void studentDashboard_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/analytics/students/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /analytics/trends?userId=1 - 返回200且趋势列表透传")
    void learningTrends_authorized_returns_200() throws Exception {
        when(analyticsService.getLearningTrends(1L)).thenReturn(List.of(Map.of("date", "2024-01-01", "count", 5)));
        mockMvc.perform(get("/analytics/trends?userId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].count").value(5));
        verify(analyticsService).getLearningTrends(eq(1L));
    }


    @Test
    @DisplayName("GET /analytics/trends?userId=1 - 未认证返回401")
    void learningTrends_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/analytics/trends?userId=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /analytics/heatmap?userId=1 - 返回200且热力图列表透传")
    void learningHeatmap_authorized_returns_200() throws Exception {
        when(analyticsService.getLearningHeatmap(eq(1L), any(), any())).thenReturn(List.of(Map.of("date", "2024-01-01", "minutes", 30)));
        mockMvc.perform(get("/analytics/heatmap?userId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].minutes").value(30));
        verify(analyticsService).getLearningHeatmap(eq(1L), any(), any());
    }


    @Test
    @DisplayName("GET /analytics/heatmap?userId=1 - 未认证返回401")
    void learningHeatmap_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/analytics/heatmap?userId=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /analytics/heatmap/platform?days=1 - 返回200且平台热力图列表透传")
    void platformHeatmap_authorized_returns_200() throws Exception {
        when(analyticsService.getPlatformHeatmap(1)).thenReturn(List.of(Map.of("dayOfWeek", 1, "hour", 2, "count", 3)));
        mockMvc.perform(get("/analytics/heatmap/platform?days=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].count").value(3));
        verify(analyticsService).getPlatformHeatmap(eq(1));
    }


    @Test
    @DisplayName("GET /analytics/heatmap/platform?days=1 - 未认证返回401")
    void platformHeatmap_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/analytics/heatmap/platform?days=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /analytics/courses/1 - 返回200且课程分析数据透传")
    void courseAnalysis_authorized_returns_200() throws Exception {
        when(analyticsService.getCourseAnalysis(1L)).thenReturn(Map.of("courseName", "Math", "studentCount", 20));
        mockMvc.perform(get("/analytics/courses/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.studentCount").value(20));
        verify(analyticsService).getCourseAnalysis(eq(1L));
    }


    @Test
    @DisplayName("GET /analytics/courses/1 - 未认证返回401")
    void courseAnalysis_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/analytics/courses/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /analytics/courses/1/grades - 返回200且成绩分布数据透传")
    void gradeDistribution_authorized_returns_200() throws Exception {
        when(analyticsService.getGradeDistribution(1L)).thenReturn(Map.of("avgScore", 80, "passCount", 18));
        mockMvc.perform(get("/analytics/courses/1/grades").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.avgScore").value(80));
        verify(analyticsService).getGradeDistribution(eq(1L));
    }


    @Test
    @DisplayName("GET /analytics/courses/1/grades - 未认证返回401")
    void gradeDistribution_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/analytics/courses/1/grades"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /analytics/courses/1/grades/histogram - 返回200且直方图数据透传")
    void gradeDistributionHistogram_authorized_returns_200() throws Exception {
        when(analyticsService.getGradeDistributionHistogram(1L)).thenReturn(Map.of("maxScore", 100, "minScore", 40));
        mockMvc.perform(get("/analytics/courses/1/grades/histogram").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.maxScore").value(100));
        verify(analyticsService).getGradeDistributionHistogram(eq(1L));
    }


    @Test
    @DisplayName("GET /analytics/courses/1/grades/histogram - 未认证返回401")
    void gradeDistributionHistogram_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/analytics/courses/1/grades/histogram"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /analytics/courses/ranking - 返回200且课程排行列表透传")
    void courseRanking_authorized_returns_200() throws Exception {
        when(analyticsService.getCourseRanking()).thenReturn(List.of(Map.of("courseId", 1, "enrollCount", 30)));
        mockMvc.perform(get("/analytics/courses/ranking").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].courseId").value(1));
        verify(analyticsService).getCourseRanking();
    }


    @Test
    @DisplayName("GET /analytics/courses/ranking - 未认证返回401")
    void courseRanking_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/analytics/courses/ranking"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /analytics/users/growth?days=1 - 返回200且用户增长列表透传")
    void userGrowthTrend_authorized_returns_200() throws Exception {
        when(analyticsService.getUserGrowthTrend(1)).thenReturn(List.of(Map.of("date", "2024-01-01", "newUsers", 5)));
        mockMvc.perform(get("/analytics/users/growth?days=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].newUsers").value(5));
        verify(analyticsService).getUserGrowthTrend(eq(1));
    }


    @Test
    @DisplayName("GET /analytics/users/growth?days=1 - 未认证返回401")
    void userGrowthTrend_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/analytics/users/growth?days=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /analytics/exams/1/statistics - 返回200且考试统计数据透传")
    void examStatistics_authorized_returns_200() throws Exception {
        when(analyticsService.getExamStatistics(1L)).thenReturn(Map.of("avgScore", 75, "passRate", 0.9));
        mockMvc.perform(get("/analytics/exams/1/statistics").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.avgScore").value(75));
        verify(analyticsService).getExamStatistics(eq(1L));
    }


    @Test
    @DisplayName("GET /analytics/exams/1/statistics - 未认证返回401")
    void examStatistics_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/analytics/exams/1/statistics"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /analytics/at-risk/students - 返回200且掉队预警聚合数据透传")
    void atRiskStudents_authorized_returns_200() throws Exception {
        when(analyticsService.getAtRiskStudents(eq("test"), eq(1L), eq("test"), eq("test"), eq(1L)))
                .thenReturn(Map.of("totalStudents", 100, "atRiskCount", 10));
        mockMvc.perform(get("/analytics/at-risk/students?groupBy=test&courseId=1&department=test&major=test&classId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.atRiskCount").value(10));
        verify(analyticsService).getAtRiskStudents(eq("test"), eq(1L), eq("test"), eq("test"), eq(1L));
    }


    @Test
    @DisplayName("GET /analytics/at-risk/students - 未认证返回401")
    void atRiskStudents_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/analytics/at-risk/students?groupBy=test&courseId=1&department=test&major=test&classId=1"))
                .andExpect(status().isUnauthorized());
    }

}
