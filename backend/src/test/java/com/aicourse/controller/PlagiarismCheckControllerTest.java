package com.aicourse.controller;
import com.aicourse.controller.PlagiarismCheckController;
import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.PlagiarismMatch;
import com.aicourse.service.PlagiarismCheckService;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * PlagiarismCheckController 接口测试。
 * 升级自生成器产出的弱断言（status != 401 && status != 403），改为真实业务断言：
 * 对 PlagiarismCheckService 的相关方法打桩并用 jsonPath 校验响应体关键字段透传，verify 确认路由委托与参数透传。
 */
@WebMvcTest(PlagiarismCheckController.class)
class PlagiarismCheckControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.PlagiarismCheckService plagiarismCheckService;

    // 修复 B2：PlagiarismCheckController 新增对象级授权组件依赖，@WebMvcTest 需显式 mock
    @MockitoBean
    private com.aicourse.security.SubmissionAccessControl submissionAccessControl;

    // 修复 B2 残留：getStudentHistory 追加「教师—学生」归属校验，控制器新增选课服务依赖，需 mock
    @MockitoBean
    private com.aicourse.service.CourseEnrollmentService courseEnrollmentService;

    @Test
    @DisplayName("POST /plagiarism-checks - TEACHER 创建查重任务返回 code=200 且字段透传")
    void create_authorized_passes_security() throws Exception {
        PlagiarismCheck check = new PlagiarismCheck();
        check.setId(10L);
        check.setSubmissionId(5L);
        check.setCheckType("TEXT");
        check.setStatus("PENDING");
        when(plagiarismCheckService.createCheck(anyLong(), anyString())).thenReturn(check);

        mockMvc.perform(post("/plagiarism-checks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submissionId\":5,\"checkType\":\"TEXT\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.checkType").value("TEXT"));

        verify(plagiarismCheckService).createCheck(eq(5L), eq("TEXT"));
    }


    @Test
    @DisplayName("POST /plagiarism-checks - 未认证返回401")
    void create_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/plagiarism-checks").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /plagiarism-checks - STUDENT角色返回403")
    void create_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/plagiarism-checks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submissionId\":5,\"checkType\":\"TEXT\"}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /plagiarism-checks/1/execute - TEACHER 执行查重返回 code=200 且委托 executeCheck")
    void execute_authorized_passes_security() throws Exception {
        doNothing().when(plagiarismCheckService).executeCheck(anyLong());

        mockMvc.perform(post("/plagiarism-checks/1/execute").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(plagiarismCheckService).executeCheck(eq(1L));
    }


    @Test
    @DisplayName("POST /plagiarism-checks/1/execute - 未认证返回401")
    void execute_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/plagiarism-checks/1/execute"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /plagiarism-checks/1/execute - STUDENT角色返回403")
    void execute_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/plagiarism-checks/1/execute").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /plagiarism-checks/1 - 已认证用户获取查重结果返回 code=200 且字段透传")
    void getCheckResult_authorized_passes_security() throws Exception {
        com.aicourse.entity.PlagiarismCheck check = new com.aicourse.entity.PlagiarismCheck();
        check.setId(1L);
        // 修复 B2：控制器先按 id 取查重任务以定位 submissionId 做 ACL
        check.setSubmissionId(5L);
        check.setStatus("DONE");
        when(plagiarismCheckService.getById(1L)).thenReturn(check);
        when(plagiarismCheckService.getCheckResult(anyLong()))
                .thenReturn(new com.aicourse.vo.PlagiarismCheckResultVO(
                        check, new java.math.BigDecimal("0.85"), 0, null, "DONE",
                        java.util.List.of(), null, null));

        mockMvc.perform(get("/plagiarism-checks/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 原 Map 为平铺 id；VO 化后 id 归属 checkInfo 子对象
                .andExpect(jsonPath("$.data.checkInfo.id").value(1))
                .andExpect(jsonPath("$.data.status").value("DONE"));

        verify(plagiarismCheckService).getCheckResult(eq(1L));
    }


    @Test
    @DisplayName("GET /plagiarism-checks/1 - 未认证返回401")
    void getCheckResult_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/plagiarism-checks/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /plagiarism-checks/1/matches - 已认证用户获取高相似匹配返回 code=200 且为数组")
    void getHighSimilarityMatches_authorized_passes_security() throws Exception {
        com.aicourse.entity.PlagiarismCheck check = new com.aicourse.entity.PlagiarismCheck();
        check.setId(1L);
        check.setSubmissionId(5L);
        when(plagiarismCheckService.getById(1L)).thenReturn(check);
        PlagiarismMatch match = new PlagiarismMatch();
        match.setId(7L);
        match.setPlagiarismCheckId(1L);
        match.setSimilarity(new BigDecimal("0.8"));
        when(plagiarismCheckService.getHighSimilarityMatches(anyLong(), any())).thenReturn(java.util.List.of(match));

        mockMvc.perform(get("/plagiarism-checks/1/matches?threshold=0.3").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(7));

        verify(plagiarismCheckService).getHighSimilarityMatches(eq(1L), eq(new BigDecimal("0.3")));
    }


    @Test
    @DisplayName("GET /plagiarism-checks/1/matches - 未认证返回401")
    void getHighSimilarityMatches_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/plagiarism-checks/1/matches?threshold=0.3"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /plagiarism-checks/submission/1 - 已认证用户获取查重历史返回 code=200 且为数组")
    void getSubmissionHistory_authorized_passes_security() throws Exception {
        PlagiarismCheck check = new PlagiarismCheck();
        check.setId(3L);
        check.setSubmissionId(1L);
        when(plagiarismCheckService.getSubmissionChecks(anyLong())).thenReturn(java.util.List.of(check));

        mockMvc.perform(get("/plagiarism-checks/submission/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(3));

        verify(plagiarismCheckService).getSubmissionChecks(eq(1L));
    }


    @Test
    @DisplayName("GET /plagiarism-checks/submission/1 - 未认证返回401")
    void getSubmissionHistory_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/plagiarism-checks/submission/1"))
                .andExpect(status().isUnauthorized());
    }

    // ===================== B2 越权回归 =====================

    @Test
    @DisplayName("GET /plagiarism-checks/1 - 学生读他人提交的查重结果返回403（B2 越权回归）")
    void getCheckResult_otherSubmission_forbidden_403() throws Exception {
        com.aicourse.entity.PlagiarismCheck check = new com.aicourse.entity.PlagiarismCheck();
        check.setId(1L);
        check.setSubmissionId(5L);
        when(plagiarismCheckService.getById(1L)).thenReturn(check);
        doThrow(new com.aicourse.exception.BusinessException(403, "无权访问该提交记录"))
                .when(submissionAccessControl).assertReadable(eq(5L), any());

        mockMvc.perform(get("/plagiarism-checks/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());

        verify(submissionAccessControl).assertReadable(eq(5L), any());
    }

    @Test
    @DisplayName("GET /plagiarism-checks/1 - 查重任务不存在返回404")
    void getCheckResult_notFound_returns_404() throws Exception {
        when(plagiarismCheckService.getById(1L)).thenReturn(null);

        mockMvc.perform(get("/plagiarism-checks/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isNotFound());
    }

    // ===================== B2 残留：GET /student/{studentId} 越权回归 =====================

    /**
     * 打桩 {@code plagiarismCheckService.lambdaQuery()} 链式查询：
     * MyBatis-Plus 的 lambda 链需要实体缓存与真实 BaseMapper，无法在纯 mock 环境真实执行，
     * 故以 mock 的 {@link LambdaQueryChainWrapper} 逐节点返回自身，并在末端 list() 返回给定行。
     */
    @SuppressWarnings("unchecked")
    private void stubStudentHistoryReturns(PlagiarismCheck... rows) {
        LambdaQueryChainWrapper<PlagiarismCheck> chain = mock(LambdaQueryChainWrapper.class);
        when(plagiarismCheckService.lambdaQuery()).thenReturn(chain);
        when(chain.eq(any(), any())).thenReturn(chain);
        // orderByDesc 在 MyBatis-Plus 中有 orderByDesc(R) 与 orderByDesc(List<R>) 两个重载，
        // 裸 any() 会触发重载歧义，故以 SFunction 类型化匹配器锁定单列排序重载。
        when(chain.orderByDesc(any(SFunction.class))).thenReturn(chain);
        when(chain.list()).thenReturn(java.util.Arrays.asList(rows));
    }

    @Test
    @DisplayName("GET /plagiarism-checks/student/99 - 教师读取非本人学生查重历史返回403（B2 残留越权回归）")
    void getStudentHistory_otherTeacherStudent_forbidden_403() throws Exception {
        // 教师 21 与该学生 99 无「任教课程—有效选课」关系
        when(courseEnrollmentService.hasApprovedEnrollmentWithTeacher(99L, 21L)).thenReturn(false);

        mockMvc.perform(get("/plagiarism-checks/student/99").with(withUser(21L, "TEACHER")))
                .andExpect(status().isForbidden());

        verify(courseEnrollmentService).hasApprovedEnrollmentWithTeacher(99L, 21L);
    }

    @Test
    @DisplayName("GET /plagiarism-checks/student/10 - 教师读取本人课程学生查重历史返回200")
    void getStudentHistory_ownCourseStudent_ok() throws Exception {
        when(courseEnrollmentService.hasApprovedEnrollmentWithTeacher(10L, 21L)).thenReturn(true);
        PlagiarismCheck row = new PlagiarismCheck();
        row.setId(3L);
        row.setStudentId(10L);
        stubStudentHistoryReturns(row);

        mockMvc.perform(get("/plagiarism-checks/student/10").with(withUser(21L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(3));

        verify(courseEnrollmentService).hasApprovedEnrollmentWithTeacher(10L, 21L);
    }

    @Test
    @DisplayName("GET /plagiarism-checks/student/10 - ADMIN 读取任意学生查重历史返回200（不触发教师归属判定）")
    void getStudentHistory_admin_ok() throws Exception {
        PlagiarismCheck row = new PlagiarismCheck();
        row.setId(4L);
        row.setStudentId(10L);
        stubStudentHistoryReturns(row);

        mockMvc.perform(get("/plagiarism-checks/student/10").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(4));

        verify(courseEnrollmentService, never()).hasApprovedEnrollmentWithTeacher(anyLong(), anyLong());
    }

    @Test
    @DisplayName("GET /plagiarism-checks/student/10 - STUDENT 角色返回403（该端点不向学生开放）")
    void getStudentHistory_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/plagiarism-checks/student/10").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /plagiarism-checks/student/10 - 未认证返回401")
    void getStudentHistory_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/plagiarism-checks/student/10"))
                .andExpect(status().isUnauthorized());
    }

}
