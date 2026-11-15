package com.aicourse.controller;
import com.aicourse.controller.ExamPaperController;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.Question;
import com.aicourse.security.ActivityVisibilityGuard;
import com.aicourse.service.ActivityTargetService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.ExamPaperStatsPaperVO;
import com.aicourse.vo.ExamPaperStatsVO;
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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ExamPaperController 接口测试（升级版：真实业务断言）。
 * 对每个 endpoint 打桩 service 并断言 PagedResponse / 实体 / Map 字段透传，verify 确认路由委托。
 */
@WebMvcTest(ExamPaperController.class)
class ExamPaperControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.ExamPaperService examPaperService;
    @MockitoBean
    private com.aicourse.service.ExamQuestionService examQuestionService;
    @MockitoBean
    private com.aicourse.service.QuestionService questionService;
    @MockitoBean
    private com.aicourse.service.ExamSessionService examSessionService;
    @MockitoBean
    private com.aicourse.security.ExamPaperAccessControl examPaperAccessControl;
    @MockitoBean
    private com.aicourse.service.CourseService courseService;
    @MockitoBean
    private ActivityVisibilityGuard activityVisibilityGuard;
    @MockitoBean
    private ActivityTargetService activityTargetService;

    @BeforeEach
    void registerEntityMetadata() {
        // LambdaQueryWrapper 的列名与参数在 getSqlSegment() 时才落地，先注册实体元数据
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExamPaper.class);
    }

    /** 触发 wrapper 生成 SQL 片段并取出参数值（等价于真实 SQL 的过滤条件）。 */
    private static Collection<Object> wrapperParams(Wrapper<?> wrapper) {
        assertTrue(wrapper != null, "查询条件 wrapper 不得为 null");
        AbstractWrapper<?, ?, ?> aw = (AbstractWrapper<?, ?, ?>) wrapper;
        aw.getSqlSegment();
        return aw.getParamNameValuePairs().values();
    }

    private Page<ExamPaper> examPaperPageWith(Long id) {
        ExamPaper p = new ExamPaper();
        p.setId(id);
        Page<ExamPaper> page = new Page<>();
        page.setRecords(List.of(p));
        page.setTotal(1L);
        return page;
    }

    @Test
    @DisplayName("GET /exam-papers - 分页查询试卷返回200，列表与总数透传")
    void listByParams_authorized_passes_security() throws Exception {
        when(examPaperService.page(any(), any())).thenReturn(examPaperPageWith(1L));
        mockMvc.perform(get("/exam-papers?page=1&size=1&courseId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].id").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
        verify(examPaperService).page(any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("GET /exam-papers?status=draft&keyword=期中 - 教师端状态与关键词进入服务端查询条件")
    void listByParams_teacher_appliesStatusAndKeyword() throws Exception {
        when(examPaperService.page(any(), any())).thenReturn(examPaperPageWith(1L));
        mockMvc.perform(get("/exam-papers?page=1&size=1&status=draft&keyword=期中")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk());

        ArgumentCaptor<Wrapper<ExamPaper>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(examPaperService).page(any(), captor.capture());
        Collection<Object> params = wrapperParams(captor.getValue());
        assertTrue(params.contains("DRAFT"), "状态需转大写精确匹配，实际=" + params);
        assertTrue(params.stream().anyMatch(v -> v != null && v.toString().contains("期中")),
                "关键词需进入 LIKE 条件，实际=" + params);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("GET /exam-papers?status=DRAFT - 学生传入草稿状态被忽略，仍只查已发布")
    void listByParams_student_statusForcedPublished() throws Exception {
        when(examPaperService.page(any(), any())).thenReturn(examPaperPageWith(1L));
        mockMvc.perform(get("/exam-papers?status=DRAFT").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk());

        ArgumentCaptor<Wrapper<ExamPaper>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(examPaperService).page(any(), captor.capture());
        Collection<Object> params = wrapperParams(captor.getValue());
        assertTrue(params.contains("PUBLISHED"), "学生端必须强制已发布，实际=" + params);
        assertFalse(params.contains("DRAFT"), "学生不得按草稿状态查询，实际=" + params);
    }

    @Test
    @DisplayName("GET /exam-papers?page=1&size=1&courseId=1 - 未认证返回401")
    void listByParams_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-papers?page=1&size=1&courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /exam-papers/course/1 - 按课程查询试卷返回200，列表透传")
    void listByCourse_authorized_passes_security() throws Exception {
        when(examPaperService.page(any(), any())).thenReturn(examPaperPageWith(1L));
        mockMvc.perform(get("/exam-papers/course/1?page=1&size=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].id").value(1));
        verify(examPaperService).page(any(), any());
    }

    @Test
    @DisplayName("GET /exam-papers/course/1?page=1&size=1 - 未认证返回401")
    void listByCourse_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-papers/course/1?page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /exam-papers/1 - 查询试卷详情返回200，ID透传")
    void getById_authorized_passes_security() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.getById(eq(1L))).thenReturn(paper);
        mockMvc.perform(get("/exam-papers/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(examPaperService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /exam-papers/1 - 未认证返回401")
    void getById_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-papers/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers - 教师创建试卷返回200，creatorId透传")
    void create_authorized_passes_security() throws Exception {
        mockMvc.perform(post("/exam-papers").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"测试试卷\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.creatorId").value(1));
        verify(examPaperService).save(any());
    }

    @Test
    @DisplayName("POST /exam-papers - 未认证返回401")
    void create_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-papers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers - STUDENT角色返回403")
    void create_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/exam-papers").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /exam-papers/1 - 教师更新试卷返回200，走服务层 updateExamPaper（白名单/总分校验收口）")
    void update_authorized_passes_security() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.getById(eq(1L))).thenReturn(paper);
        when(examPaperService.updateExamPaper(eq(1L), any())).thenReturn(paper);
        mockMvc.perform(put("/exam-papers/1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"测试试卷\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(examPaperService).updateExamPaper(eq(1L), any());
        verify(examPaperService, never()).updateById(any());
    }

    @Test
    @DisplayName("PUT /exam-papers/1 - 未认证返回401")
    void update_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/exam-papers/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /exam-papers/1 - STUDENT角色返回403")
    void update_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/exam-papers/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /exam-papers/1 - 教师删除试卷返回200")
    void delete_authorized_passes_security() throws Exception {
        mockMvc.perform(delete("/exam-papers/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(examPaperService).removeById(eq(1L));
    }

    // B-13 ⑳：外键级联会连坐销毁题目行/考试会话/监考事件，故有考试记录时必须拒绝删除。
    // 2026-11-01 项目主裁定：返回 409（不做软删、不加 deleted_at、不占迁移号）。
    @Test
    @DisplayName("DELETE /exam-papers/1 - 已有考试记录时返回409且不执行删除")
    void delete_withExamSessions_returns409() throws Exception {
        when(examSessionService.count(any())).thenReturn(3L);
        mockMvc.perform(delete("/exam-papers/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
        verify(examPaperService, never()).removeById(eq(1L));
    }

    @Test
    @DisplayName("DELETE /exam-papers/1 - 无考试记录时仍可删除（守卫不误伤）")
    void delete_withoutExamSessions_stillDeletes() throws Exception {
        when(examSessionService.count(any())).thenReturn(0L);
        mockMvc.perform(delete("/exam-papers/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(examPaperService).removeById(eq(1L));
    }

    @Test
    @DisplayName("DELETE /exam-papers/1 - 未认证返回401")
    void delete_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/exam-papers/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /exam-papers/1 - STUDENT角色返回403")
    void delete_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/exam-papers/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /exam-papers/1/questions - 查询试卷题目返回200，数组透传")
    void getQuestions_authorized_passes_security() throws Exception {
        ExamQuestion eq = new ExamQuestion();
        eq.setId(5L);
        eq.setQuestionId(9L);
        ExamPaper existing = new ExamPaper();
        existing.setId(1L);
        existing.setCourseId(1L);
        when(examPaperService.getById(eq(1L))).thenReturn(existing);
        when(examQuestionService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(eq));
        when(questionService.getById(eq(9L))).thenReturn(new Question());
        mockMvc.perform(get("/exam-papers/1/questions").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(5));
        verify(examQuestionService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    @DisplayName("GET /exam-papers/1/questions - 未认证返回401")
    void getQuestions_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-papers/1/questions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /exam-papers/1/detail - 查询试卷完整详情返回200，字段透传")
    void getDetail_authorized_passes_security() throws Exception {
        com.aicourse.entity.ExamPaper p = new com.aicourse.entity.ExamPaper();
        p.setId(1L);
        p.setCourseId(1L);
        p.setTitle("期中试卷");
        when(examPaperService.getById(eq(1L))).thenReturn(p);
        when(examPaperService.getExamPaperDetail(eq(1L)))
                .thenReturn(new com.aicourse.vo.ExamPaperDetailVO(p, java.util.Collections.emptyList(), 0));
        mockMvc.perform(get("/exam-papers/1/detail").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.paper.title").value("期中试卷"));
        verify(examPaperService).getExamPaperDetail(eq(1L));
    }

    @Test
    @DisplayName("GET /exam-papers/1/detail - 未认证返回401")
    void getDetail_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-papers/1/detail"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers/1/publish - 管理员直接发布返回403（WP6：治理只读）")
    void publish_admin_forbidden_403() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.getById(eq(1L))).thenReturn(paper);
        doThrow(new com.aicourse.exception.BusinessException(com.aicourse.common.ApiCode.FORBIDDEN,
                "管理员仅治理只读，不得发布教学活动或修改目标教学班"))
                .when(activityVisibilityGuard).assertNotAdmin(any());
        mockMvc.perform(post("/exam-papers/1/publish").with(withUser(1L, "ADMIN")))
                .andExpect(status().isForbidden());
        verify(examPaperService, never()).publishExamPaper(anyLong());
        verify(examPaperService, never()).submitExamPaperForReview(anyLong());
    }

    @Test
    @DisplayName("POST /exam-papers/1/publish - 教师提交审核，不直接发布")
    void publish_teacher_submits_review() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.getById(eq(1L))).thenReturn(paper);
        mockMvc.perform(post("/exam-papers/1/publish").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已提交审核"));
        verify(examPaperService).submitExamPaperForReview(eq(1L));
        verify(examPaperService, never()).publishExamPaper(anyLong());
    }

    @Test
    @DisplayName("PATCH /exam-papers/1/approve - 管理员审核通过返回200")
    void approve_admin_passes_security() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.getById(eq(1L))).thenReturn(paper);
        mockMvc.perform(patch("/exam-papers/1/approve").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("审核通过"));
        verify(examPaperService).approveExamPaper(eq(1L));
    }

    @Test
    @DisplayName("PATCH /exam-papers/1/approve - 教师返回403")
    void approve_teacher_forbidden_403() throws Exception {
        mockMvc.perform(patch("/exam-papers/1/approve").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(examPaperService, never()).approveExamPaper(anyLong());
    }

    @Test
    @DisplayName("POST /exam-papers/1/reject - 管理员驳回并透传原因")
    void reject_admin_passes_security() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.getById(eq(1L))).thenReturn(paper);
        mockMvc.perform(post("/exam-papers/1/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"题干超纲\"}").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已驳回"));
        verify(examPaperService).rejectExamPaper(eq(1L), eq("题干超纲"));
    }

    @Test
    @DisplayName("POST /exam-papers/1/reject - 教师返回403")
    void reject_teacher_forbidden_403() throws Exception {
        mockMvc.perform(post("/exam-papers/1/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(examPaperService, never()).rejectExamPaper(anyLong(), any());
    }

    @Test
    @DisplayName("POST /exam-papers/1/publish - 未认证返回401")
    void publish_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-papers/1/publish"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers/1/publish - STUDENT角色返回403")
    void publish_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/exam-papers/1/publish").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /exam-papers/1/questions - 教师批量替换试卷题目返回200，清单透传")
    void replaceQuestions_authorized_passes_security() throws Exception {
        ExamQuestion saved = new ExamQuestion();
        saved.setId(77L);
        saved.setExamPaperId(1L);
        saved.setQuestionId(9L);
        saved.setOrderIndex(0);
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.getById(eq(1L))).thenReturn(paper);
        when(examPaperService.replaceExamQuestions(eq(1L), any()))
                .thenReturn(List.of(saved));
        mockMvc.perform(post("/exam-papers/1/questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questions\":[{\"questionId\":9,\"points\":5,\"orderIndex\":0}]}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(77))
                .andExpect(jsonPath("$.data[0].questionId").value(9));
        verify(examPaperService).replaceExamQuestions(eq(1L), any());
        verify(examPaperAccessControl).assertCanManage(any(), any());
    }

    @Test
    @DisplayName("POST /exam-papers/1/questions - 未认证返回401")
    void replaceQuestions_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-papers/1/questions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"questions\":[{\"questionId\":9}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers/1/questions - STUDENT角色返回403")
    void replaceQuestions_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/exam-papers/1/questions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"questions\":[{\"questionId\":9}]}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /exam-papers/auto-compose - 教师自动组卷返回200，ID透传")
    void autoCompose_authorized_passes_security() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.autoComposeExamPaper(any(), eq(1L))).thenReturn(paper);
        mockMvc.perform(post("/exam-papers/auto-compose").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(examPaperService).autoComposeExamPaper(any(), eq(1L));
    }

    @Test
    @DisplayName("POST /exam-papers/auto-compose - 未认证返回401")
    void autoCompose_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-papers/auto-compose").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers/auto-compose - STUDENT角色返回403")
    void autoCompose_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/exam-papers/auto-compose").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /exam-papers/1/copy - 教师复制试卷返回200，ID透传")
    void copy_authorized_passes_security() throws Exception {
        ExamPaper source = new ExamPaper();
        source.setId(1L);
        source.setCourseId(10L);
        when(examPaperService.getById(eq(1L))).thenReturn(source);
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        when(examPaperService.copyExamPaper(eq(1L), any())).thenReturn(paper);
        mockMvc.perform(post("/exam-papers/1/copy").contentType(MediaType.APPLICATION_JSON).content("{\"newTitle\":\"副本\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        // 修复 A-02 #6：复制前必须先对源试卷做对象级 ACL 校验
        verify(examPaperAccessControl).assertCanManage(any(), any());
        verify(examPaperService).copyExamPaper(eq(1L), any());
    }

    // A-02 #6 回归：复制源试卷须对源课程有管理权，否则任意教师可复制他师试卷
    // 并经 /detail 读取全部题目与标准答案。
    @Test
    @DisplayName("POST /exam-papers/1/copy - 非源课程授课教师复制返回403且不执行复制")
    void copy_withoutManagePermission_forbidden_403() throws Exception {
        ExamPaper source = new ExamPaper();
        source.setId(1L);
        source.setCourseId(10L);
        when(examPaperService.getById(eq(1L))).thenReturn(source);
        doThrow(new com.aicourse.exception.BusinessException(com.aicourse.common.ApiCode.FORBIDDEN,
                "无权操作其他教师课程的试卷"))
                .when(examPaperAccessControl).assertCanManage(any(), any());
        mockMvc.perform(post("/exam-papers/1/copy").contentType(MediaType.APPLICATION_JSON).content("{\"newTitle\":\"副本\"}")
                        .with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(examPaperService, never()).copyExamPaper(anyLong(), any());
    }

    @Test
    @DisplayName("POST /exam-papers/1/copy - 未认证返回401")
    void copy_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-papers/1/copy").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-papers/1/copy - STUDENT角色返回403")
    void copy_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/exam-papers/1/copy").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /exam-papers/1/statistics - 查询试卷统计返回200，字段透传")
    void statistics_authorized_passes_security() throws Exception {
        when(examPaperService.getPaperStatistics(eq(1L))).thenReturn(
                new ExamPaperStatsVO(new ExamPaperStatsPaperVO(1L, "期中", "PUBLISHED"), 4,
                        new java.math.BigDecimal("56.00"), Map.of("SINGLE", 1), Map.of("1-5分", 1)));
        mockMvc.perform(get("/exam-papers/1/statistics").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.questionCount").value(4))
                .andExpect(jsonPath("$.data.paperInfo.title").value("期中"));
        verify(examPaperService).getPaperStatistics(eq(1L));
    }

    @Test
    @DisplayName("GET /exam-papers/1/statistics - 未认证返回401")
    void statistics_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-papers/1/statistics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /exam-papers/1/targets - 管理员改目标返回403（WP6：治理只读）")
    void updateTargets_admin_forbidden_403() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        paper.setCourseId(1L);
        when(examPaperService.getById(eq(1L))).thenReturn(paper);
        doThrow(new com.aicourse.exception.BusinessException(com.aicourse.common.ApiCode.FORBIDDEN,
                "管理员仅治理只读，不得发布教学活动或修改目标教学班"))
                .when(activityVisibilityGuard).assertNotAdmin(any());
        mockMvc.perform(put("/exam-papers/1/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"CLASSES\",\"teachingClassIds\":[11]}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isForbidden());
        verify(activityTargetService, never()).replaceExamPaperTargets(anyLong(), any(), any());
    }

    @Test
    @DisplayName("PUT /exam-papers/1/targets - 教师设置目标教学班返回200")
    void updateTargets_teacher_returns_200() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        paper.setCourseId(1L);
        paper.setVisibility("COURSE");
        when(examPaperService.getById(eq(1L))).thenReturn(paper);
        when(activityTargetService.normalizeAndValidate(eq(1L), eq(1L), eq("CLASSES"), any(), eq(true)))
                .thenReturn("CLASSES");
        when(activityTargetService.examPaperTargetClassIds(1L)).thenReturn(List.of(11L));
        mockMvc.perform(put("/exam-papers/1/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"CLASSES\",\"teachingClassIds\":[11]}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("CLASSES"))
                .andExpect(jsonPath("$.data.teachingClassIds[0]").value(11));
        verify(activityTargetService).replaceExamPaperTargets(1L, "CLASSES", List.of(11L));
    }

    @Test
    @DisplayName("GET /exam-papers?teachingClassId=11 - 教学班筛选委托可见性守卫")
    void listByParams_teachingClassId_delegates_guard() throws Exception {
        Page<ExamPaper> p = new Page<>(1, 1);
        p.setRecords(List.of());
        p.setTotal(0);
        when(examPaperService.page(any(), any())).thenReturn(p);
        mockMvc.perform(get("/exam-papers?page=1&size=1&teachingClassId=11")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk());
        verify(activityVisibilityGuard).applyExamPaperScope(any(), any(), eq(11L));
    }
}
