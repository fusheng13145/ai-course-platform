package com.aicourse.controller;
import com.aicourse.controller.TaskController;
import com.aicourse.common.ApiCode;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.ActivityVisibilityGuard;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.ActivityTargetService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.entity.Task;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
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
 * TaskController 接口测试（升级版）。
 * 原生成器弱断言（status != 401 && status != 403）已全部替换为真实业务断言：
 * 对依赖的 TaskService 方法打桩返回合理业务对象，断言 200/code=200，并校验关键字段透传、verify 路由委托。
 * 对带有 @PreAuthorize("hasAnyRole('TEACHER','ADMIN')") 的端点新增/保留 STUDENT 越权 403 测试。
 */
@WebMvcTest(TaskController.class)
class TaskControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.TaskService taskService;
    @MockitoBean
    private com.aicourse.service.SubmissionService submissionService;
    @MockitoBean
    private CourseAccessControl courseAccessControl;
    @MockitoBean
    private ActivityVisibilityGuard activityVisibilityGuard;
    @MockitoBean
    private ActivityTargetService activityTargetService;

    @BeforeEach
    void defaultAccessControl() {
        lenient().when(courseAccessControl.readableCourseIds(any())).thenReturn(null);
        lenient().when(courseAccessControl.isStudent(any())).thenReturn(false);
    }

    private Task sampleTask(Long id, String title) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setCourseId(1L);
        return t;
    }

    @Test
    @DisplayName("GET /tasks - 返回200且分页任务列表透传")
    void listByParams_authorized_returns_200() throws Exception {
        Page<Task> p = new Page<>(1, 1);
        p.setRecords(List.of(sampleTask(1L, "作业1")));
        p.setTotal(1);
        when(taskService.page(any(), any())).thenReturn(p);
        mockMvc.perform(get("/tasks?page=1&size=1&courseId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value("作业1"));
        verify(taskService).page(any(), any());
    }


    @Test
    @DisplayName("GET /tasks?page=1&size=1&courseId=1 - 未认证返回401")
    void listByParams_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/tasks?page=1&size=1&courseId=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /tasks/course/1 - 返回200且课程任务列表透传")
    void listByCourse_authorized_returns_200() throws Exception {
        Page<Task> p = new Page<>(1, 1);
        p.setRecords(List.of(sampleTask(2L, "课程作业")));
        p.setTotal(1);
        when(taskService.page(any(), any())).thenReturn(p);
        mockMvc.perform(get("/tasks/course/1?page=1&size=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.list[0].courseId").value(1));
        verify(taskService).page(any(), any());
    }


    @Test
    @DisplayName("GET /tasks/course/1?page=1&size=1 - 未认证返回401")
    void listByCourse_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/tasks/course/1?page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /tasks/1 - 返回200且任务详情透传")
    void getById_authorized_returns_200() throws Exception {
        when(taskService.getById(1L)).thenReturn(sampleTask(1L, "作业1"));
        mockMvc.perform(get("/tasks/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("作业1"));
        verify(taskService).getById(eq(1L));
    }


    @Test
    @DisplayName("GET /tasks/1 - 未认证返回401")
    void getById_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/tasks/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /tasks - 返回200且创建任务透传")
    void create_authorized_returns_200() throws Exception {
        when(taskService.save(any())).thenReturn(true);
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新作业\",\"courseId\":1}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.title").value("新作业"));
        verify(taskService).save(any(Task.class));
    }


    @Test
    @DisplayName("POST /tasks - 未认证返回401")
    void create_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /tasks - STUDENT角色返回403")
    void create_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("PUT /tasks/1 - 返回200且更新后任务透传")
    void update_authorized_returns_200() throws Exception {
        when(taskService.getById(any())).thenReturn(sampleTask(1L, "更新后"));
        when(taskService.updateById(any())).thenReturn(true);
        mockMvc.perform(put("/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"更新后\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(taskService).updateById(any(Task.class));
    }


    @Test
    @DisplayName("PUT /tasks/1 - 未认证返回401")
    void update_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/tasks/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PUT /tasks/1 - STUDENT角色返回403")
    void update_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/tasks/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("DELETE /tasks/1 - 返回200且删除委托被调用")
    void delete_authorized_returns_200() throws Exception {
        when(taskService.getById(1L)).thenReturn(sampleTask(1L, "作业1"));
        when(taskService.removeById(1L)).thenReturn(true);
        mockMvc.perform(delete("/tasks/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(taskService).removeById(eq(1L));
    }


    @Test
    @DisplayName("DELETE /tasks/1 - 未认证返回401")
    void delete_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/tasks/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("DELETE /tasks/1 - STUDENT角色返回403")
    void delete_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/tasks/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /tasks/1/publish - 返回200且发布后任务透传")
    void publish_authorized_returns_200() throws Exception {
        Task published = sampleTask(1L, "作业1");
        published.setStatus("PUBLISHED");
        when(taskService.getById(1L)).thenReturn(published);
        when(taskService.publishTask(1L)).thenReturn(published);
        mockMvc.perform(post("/tasks/1/publish").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        verify(taskService).publishTask(eq(1L));
    }


    @Test
    @DisplayName("POST /tasks/1/publish - 未认证返回401")
    void publish_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/tasks/1/publish"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /tasks/1/publish - STUDENT角色返回403")
    void publish_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/tasks/1/publish").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /tasks/1/extend-deadline - 返回200且延长后任务透传")
    void extendDeadline_authorized_returns_200() throws Exception {
        when(taskService.getById(1L)).thenReturn(sampleTask(1L, "作业1"));
        when(taskService.extendDeadline(eq(1L), any(), any())).thenReturn(sampleTask(1L, "作业1"));
        mockMvc.perform(post("/tasks/1/extend-deadline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newDueDate\":\"2024-12-31T23:59:59\",\"reason\":\"延期\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(taskService).extendDeadline(eq(1L), any(), any());
    }

    // ---- B-13 ⑨：extend-deadline 的入参时间口径必须与全局宽松反序列化一致 ----
    // 修复前：这里手写 LocalDateTime.parse，只认 ISO-8601；同一控制器的 dueDate 却走
    // LenientLocalDateTimeDeserializer（四种格式都收），两套口径并存。
    // 2026-11-01 项目主裁定：方向为「放宽」，ISO-8601 为契约推荐格式。
    @org.junit.jupiter.params.ParameterizedTest(name = "[{index}] newDueDate=\"{0}\" 应被接受")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "2026-10-31T23:59:59",
            "2026-10-31 23:59:59",
            "2026-10-31 23:59",
            "2026-10-31"
    })
    @DisplayName("POST /tasks/1/extend-deadline - 四种时间格式均应 200（与 dueDate 同口径）")
    void extendDeadline_acceptsLenientDateFormats(String newDueDate) throws Exception {
        when(taskService.getById(1L)).thenReturn(sampleTask(1L, "作业1"));
        when(taskService.extendDeadline(eq(1L), any(), any())).thenReturn(sampleTask(1L, "作业1"));
        mockMvc.perform(post("/tasks/1/extend-deadline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newDueDate\":\"" + newDueDate + "\",\"reason\":\"延期\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(taskService).extendDeadline(eq(1L), any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "[{index}] newDueDate=\"{0}\" 应 400")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"2026/10/31", "not-a-date"})
    @DisplayName("POST /tasks/1/extend-deadline - 非法时间格式返回 400（不是 500）")
    void extendDeadline_invalidFormat_returns_400(String newDueDate) throws Exception {
        when(taskService.getById(1L)).thenReturn(sampleTask(1L, "作业1"));
        mockMvc.perform(post("/tasks/1/extend-deadline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newDueDate\":\"" + newDueDate + "\",\"reason\":\"延期\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(taskService, never()).extendDeadline(any(), any(), any());
    }


    @Test
    @DisplayName("POST /tasks/1/extend-deadline - 未认证返回401")
    void extendDeadline_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/tasks/1/extend-deadline").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /tasks/1/extend-deadline - STUDENT角色返回403")
    void extendDeadline_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/tasks/1/extend-deadline").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /tasks/1/copy - 返回200且复制后任务透传")
    void copy_authorized_returns_200() throws Exception {
        Task copy = sampleTask(1L, "副本");
        when(taskService.getById(1L)).thenReturn(sampleTask(1L, "作业1"));
        when(taskService.copyTask(eq(1L), any())).thenReturn(copy);
        mockMvc.perform(post("/tasks/1/copy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newTitle\":\"副本\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.title").value("副本"));
        verify(taskService).copyTask(eq(1L), any());
    }


    @Test
    @DisplayName("POST /tasks/1/copy - 未认证返回401")
    void copy_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/tasks/1/copy").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /tasks/1/copy - STUDENT角色返回403")
    void copy_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/tasks/1/copy").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /tasks/pending-grading - 返回200且待批阅任务列表透传")
    void pendingGrading_authorized_returns_200() throws Exception {
        when(taskService.getPendingGradingTasks(1L)).thenReturn(List.of(sampleTask(1L, "待批作业")));
        mockMvc.perform(get("/tasks/pending-grading").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(taskService).getPendingGradingTasks(eq(1L));
    }


    @Test
    @DisplayName("GET /tasks/pending-grading - STUDENT角色返回403")
    void pendingGrading_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/tasks/pending-grading").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /tasks/pending-grading - 未认证返回401")
    void pendingGrading_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/tasks/pending-grading"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /tasks/1/completion-stats - 返回200且完成率统计透传")
    void completionStats_authorized_returns_200() throws Exception {
        when(taskService.getById(1L)).thenReturn(sampleTask(1L, "作业1"));
        when(taskService.getTaskCompletionStats(1L)).thenReturn(new com.aicourse.vo.TaskCompletionStatsVO(
                1L, "task", 20L, 10L, 0L, 0L, 0L, 0.0, 0.0, null, "PUBLISHED"));
        mockMvc.perform(get("/tasks/1/completion-stats").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                // 原桩键为 submitted，真实契约无此键；VO 化后按 totalSubmissions 断言
                .andExpect(jsonPath("$.data.totalSubmissions").value(20));
        verify(taskService).getTaskCompletionStats(eq(1L));
    }


    @Test
    @DisplayName("GET /tasks/1/completion-stats - 未认证返回401")
    void completionStats_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/tasks/1/completion-stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /tasks - 跨教师课程创建返回403")
    void create_crossTeacher_forbidden_403() throws Exception {
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权操作其他教师的课程"))
                .when(courseAccessControl).assertCanManageCourse(eq(1L), any());
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"越权任务\",\"courseId\":1}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(taskService, never()).save(any(Task.class));
    }

    @Test
    @DisplayName("PUT /tasks/1 - 跨教师更新返回403")
    void update_crossTeacher_forbidden_403() throws Exception {
        when(taskService.getById(1L)).thenReturn(sampleTask(1L, "他人任务"));
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权操作其他教师的课程"))
                .when(courseAccessControl).assertCanManageCourse(eq(1L), any());
        mockMvc.perform(put("/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"越权更新\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(taskService, never()).updateById(any(Task.class));
    }

    @Test
    @DisplayName("DELETE /tasks/1 - 跨教师删除返回403")
    void delete_crossTeacher_forbidden_403() throws Exception {
        when(taskService.getById(1L)).thenReturn(sampleTask(1L, "他人任务"));
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权操作其他教师的课程"))
                .when(courseAccessControl).assertCanManageCourse(eq(1L), any());
        mockMvc.perform(delete("/tasks/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(taskService, never()).removeById(1L);
    }

    @Test
    @DisplayName("POST /tasks/1/publish - 跨教师发布返回403")
    void publish_crossTeacher_forbidden_403() throws Exception {
        when(taskService.getById(1L)).thenReturn(sampleTask(1L, "他人任务"));
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权操作其他教师的课程"))
                .when(courseAccessControl).assertCanManageCourse(eq(1L), any());
        mockMvc.perform(post("/tasks/1/publish").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(taskService, never()).publishTask(1L);
    }

    @Test
    @DisplayName("GET /tasks/1 - 跨教师读取返回403")
    void getById_crossTeacher_forbidden_403() throws Exception {
        when(taskService.getById(1L)).thenReturn(sampleTask(1L, "他人任务"));
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权查看其他教师课程的内容"))
                .when(activityVisibilityGuard).assertVisibleTask(any(Task.class), any());
        mockMvc.perform(get("/tasks/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /tasks/1/publish - 管理员发布返回403（WP6：治理只读）")
    void publish_admin_forbidden_403() throws Exception {
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "管理员仅治理只读，不得发布教学活动或修改目标教学班"))
                .when(activityVisibilityGuard).assertNotAdmin(any());
        mockMvc.perform(post("/tasks/1/publish").with(withUser(1L, "ADMIN")))
                .andExpect(status().isForbidden());
        verify(taskService, never()).publishTask(anyLong());
    }

    @Test
    @DisplayName("PUT /tasks/1/targets - 管理员改目标返回403（WP6：治理只读）")
    void updateTargets_admin_forbidden_403() throws Exception {
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "管理员仅治理只读，不得发布教学活动或修改目标教学班"))
                .when(activityVisibilityGuard).assertNotAdmin(any());
        mockMvc.perform(put("/tasks/1/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"CLASSES\",\"teachingClassIds\":[1]}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isForbidden());
        verify(activityTargetService, never()).replaceTaskTargets(anyLong(), any(), any());
    }

    @Test
    @DisplayName("PUT /tasks/1/targets - 教师设置目标教学班返回200")
    void updateTargets_teacher_returns_200() throws Exception {
        Task existing = sampleTask(1L, "任务");
        existing.setVisibility("COURSE");
        when(taskService.getById(1L)).thenReturn(existing);
        when(activityTargetService.normalizeAndValidate(eq(1L), eq(1L), eq("CLASSES"), any(), eq(false)))
                .thenReturn("CLASSES");
        when(activityTargetService.taskTargetClassIds(1L)).thenReturn(List.of(11L));
        mockMvc.perform(put("/tasks/1/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"CLASSES\",\"teachingClassIds\":[11]}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("CLASSES"))
                .andExpect(jsonPath("$.data.teachingClassIds[0]").value(11));
        verify(activityTargetService).replaceTaskTargets(1L, "CLASSES", List.of(11L));
    }

    @Test
    @DisplayName("GET /tasks?teachingClassId=11 - 教学班筛选委托可见性守卫")
    void listByParams_teachingClassId_delegates_guard() throws Exception {
        Page<Task> p = new Page<>(1, 1);
        p.setRecords(List.of(sampleTask(1L, "作业1")));
        p.setTotal(1);
        when(taskService.page(any(), any())).thenReturn(p);
        mockMvc.perform(get("/tasks?page=1&size=1&courseId=1&teachingClassId=11")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk());
        verify(activityVisibilityGuard).applyTaskScope(any(), any(), eq(11L));
    }
}
