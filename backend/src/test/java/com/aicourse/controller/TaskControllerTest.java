package com.aicourse.controller;
import com.aicourse.controller.TaskController;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.entity.Task;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
        when(taskService.publishTask(1L)).thenReturn(published);
        mockMvc.perform(post("/tasks/1/publish").with(withUser(1L, "TEACHER", "ADMIN")))
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

}
