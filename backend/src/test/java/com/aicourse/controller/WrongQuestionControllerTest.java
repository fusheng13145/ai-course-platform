package com.aicourse.controller;
import com.aicourse.controller.WrongQuestionController;
import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.vo.WrongQuestionStatsVO;
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
 * WrongQuestionController 接口测试（升级版）。
 * 将「伪覆盖弱断言」改造为真实业务断言：对每个 endpoint 依赖的 service 方法打桩返回合理业务对象，
 * 断言 HTTP 200 + ApiResponse.code=200 + 关键字段透传（jsonPath），并 verify 路由正确委托与参数透传。
 */
@WebMvcTest(WrongQuestionController.class)
class WrongQuestionControllerTest extends ControllerTestBase {

    @MockitoBean
    private WrongQuestionService wrongQuestionService;

    private WrongQuestion sampleWq() {
        WrongQuestion wq = new WrongQuestion();
        wq.setId(1L);
        wq.setStudentId(1L);
        return wq;
    }

    private Question sampleQuestion() {
        Question q = new Question();
        q.setId(1L);
        return q;
    }

    @Test
    @DisplayName("GET /wrong-questions?courseId=1&page=1&size=1 - 返回200且data.list为数组")
    void list_authorized_returns_success() throws Exception {
        when(wrongQuestionService.getWrongQuestionBook(any(), any(), anyInt(), anyInt())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());
        mockMvc.perform(get("/wrong-questions?courseId=1&page=1&size=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
        verify(wrongQuestionService).getWrongQuestionBook(eq(1L), eq(1L), eq(1), eq(1));
    }

    @Test
    @DisplayName("GET /wrong-questions?courseId=1&page=1&size=1 - 未认证返回401")
    void list_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/wrong-questions?courseId=1&page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /wrong-questions - 返回200且data透传错题id")
    void record_authorized_returns_success() throws Exception {
        when(wrongQuestionService.recordWrongAnswer(any(), any(), any(), any(), any())).thenReturn(sampleWq());
        mockMvc.perform(post("/wrong-questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":1,\"courseId\":1,\"wrongAnswer\":\"x\"}").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(wrongQuestionService).recordWrongAnswer(eq(1L), eq(1L), eq(1L), eq("x"), eq("PRACTICE"));
    }

    @Test
    @DisplayName("POST /wrong-questions - 未认证返回401")
    void record_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/wrong-questions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /wrong-questions/1/mastered - 返回200且正确委托markMastered")
    void markMastered_authorized_returns_success() throws Exception {
        when(wrongQuestionService.getById(eq(1L))).thenReturn(sampleWq());
        doNothing().when(wrongQuestionService).markMastered(any());
        mockMvc.perform(patch("/wrong-questions/1/mastered").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(wrongQuestionService).markMastered(eq(1L));
    }

    @Test
    @DisplayName("PATCH /wrong-questions/1/mastered - 学生标记他人错题返回403")
    void markMastered_othersWrongQuestion_forbidden_403() throws Exception {
        when(wrongQuestionService.getById(eq(1L))).thenReturn(sampleWq());
        mockMvc.perform(patch("/wrong-questions/1/mastered").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(wrongQuestionService, never()).markMastered(anyLong());
    }

    @Test
    @DisplayName("PATCH /wrong-questions/1/mastered - 未认证返回401")
    void markMastered_unauthorized_returns_401() throws Exception {
        mockMvc.perform(patch("/wrong-questions/1/mastered"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /wrong-questions/1/unmaster - 返回200且正确委托unmarkMastered")
    void unmarkMastered_authorized_returns_success() throws Exception {
        when(wrongQuestionService.getById(eq(1L))).thenReturn(sampleWq());
        doNothing().when(wrongQuestionService).unmarkMastered(any());
        mockMvc.perform(patch("/wrong-questions/1/unmaster").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(wrongQuestionService).unmarkMastered(eq(1L));
    }

    @Test
    @DisplayName("PATCH /wrong-questions/1/unmaster - 学生操作他人错题返回403")
    void unmarkMastered_othersWrongQuestion_forbidden_403() throws Exception {
        when(wrongQuestionService.getById(eq(1L))).thenReturn(sampleWq());
        mockMvc.perform(patch("/wrong-questions/1/unmaster").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(wrongQuestionService, never()).unmarkMastered(anyLong());
    }

    @Test
    @DisplayName("PATCH /wrong-questions/1/unmaster - 未认证返回401")
    void unmarkMastered_unauthorized_returns_401() throws Exception {
        mockMvc.perform(patch("/wrong-questions/1/unmaster"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /wrong-questions/unmastered?courseId=1 - 返回200且data为数组")
    void getUnmastered_authorized_returns_success() throws Exception {
        when(wrongQuestionService.getUnmastered(any(), any())).thenReturn(List.of(sampleWq()));
        mockMvc.perform(get("/wrong-questions/unmastered?courseId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(wrongQuestionService).getUnmastered(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("GET /wrong-questions/unmastered?courseId=1 - 未认证返回401")
    void getUnmastered_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/wrong-questions/unmastered?courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /wrong-questions/by-kp?courseId=1 - 返回200且data为分组Map")
    void getByKnowledgePoints_authorized_returns_success() throws Exception {
        when(wrongQuestionService.getByKnowledgePoints(any(), any())).thenReturn(Map.of("kp1", List.of(sampleWq())));
        mockMvc.perform(get("/wrong-questions/by-kp?courseId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.kp1").isArray());
        verify(wrongQuestionService).getByKnowledgePoints(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("GET /wrong-questions/by-kp?courseId=1 - 未认证返回401")
    void getByKnowledgePoints_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/wrong-questions/by-kp?courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /wrong-questions/stats?courseId=1 - 返回200且data透传统计")
    void stats_authorized_returns_success() throws Exception {
        when(wrongQuestionService.getWrongQuestionStats(any(), any())).thenReturn(
                new WrongQuestionStatsVO(1, 0, 1, 0.0, Map.of(), Map.of(), Map.of()));
        mockMvc.perform(get("/wrong-questions/stats?courseId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));
        verify(wrongQuestionService).getWrongQuestionStats(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("GET /wrong-questions/stats?courseId=1 - 未认证返回401")
    void stats_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/wrong-questions/stats?courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /wrong-questions/practice-paper - 返回200且data为题目数组")
    void generatePracticePaper_authorized_returns_success() throws Exception {
        when(wrongQuestionService.generatePracticePaper(any(), any(), anyInt())).thenReturn(List.of(sampleQuestion()));
        mockMvc.perform(post("/wrong-questions/practice-paper").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"count\":1}").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(wrongQuestionService).generatePracticePaper(eq(1L), eq(1L), eq(1));
    }

    @Test
    @DisplayName("POST /wrong-questions/practice-paper - 未认证返回401")
    void generatePracticePaper_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/wrong-questions/practice-paper").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /wrong-questions/1/related - 返回200且data为题目数组")
    void recommendRelated_authorized_returns_success() throws Exception {
        when(wrongQuestionService.recommendRelatedQuestions(any())).thenReturn(List.of(sampleQuestion()));
        mockMvc.perform(get("/wrong-questions/1/related").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(wrongQuestionService).recommendRelatedQuestions(eq(1L));
    }

    @Test
    @DisplayName("GET /wrong-questions/1/related - 未认证返回401")
    void recommendRelated_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/wrong-questions/1/related"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /wrong-questions/1 - STUDENT角色返回200且data透传更新错题")
    void update_authorized_returns_success() throws Exception {
        when(wrongQuestionService.updateById(any())).thenReturn(true);
        when(wrongQuestionService.getById(eq(1L))).thenReturn(sampleWq());
        mockMvc.perform(put("/wrong-questions/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(wrongQuestionService).updateById(any(WrongQuestion.class));
        verify(wrongQuestionService, times(2)).getById(eq(1L));
    }

    @Test
    @DisplayName("PUT /wrong-questions/1 - 学生修改他人错题返回403")
    void update_othersWrongQuestion_forbidden_403() throws Exception {
        when(wrongQuestionService.getById(eq(1L))).thenReturn(sampleWq());
        mockMvc.perform(put("/wrong-questions/1").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(wrongQuestionService, never()).updateById(any());
    }

    @Test
    @DisplayName("PUT /wrong-questions/1 - 未认证返回401")
    void update_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/wrong-questions/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /wrong-questions/1 - TEACHER角色返回403（仅STUDENT/ADMIN可改）")
    void update_teacher_forbidden_403() throws Exception {
        mockMvc.perform(put("/wrong-questions/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /wrong-questions/1 - STUDENT角色返回200且正确委托removeById")
    void delete_authorized_returns_success() throws Exception {
        when(wrongQuestionService.getById(eq(1L))).thenReturn(sampleWq());
        when(wrongQuestionService.removeById(any(Long.class))).thenReturn(true);
        mockMvc.perform(delete("/wrong-questions/1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(wrongQuestionService).removeById(eq(1L));
    }

    @Test
    @DisplayName("DELETE /wrong-questions/1 - 学生删除他人错题返回403")
    void delete_othersWrongQuestion_forbidden_403() throws Exception {
        when(wrongQuestionService.getById(eq(1L))).thenReturn(sampleWq());
        mockMvc.perform(delete("/wrong-questions/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(wrongQuestionService, never()).removeById(anyLong());
    }

    @Test
    @DisplayName("DELETE /wrong-questions/1 - 未认证返回401")
    void delete_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/wrong-questions/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /wrong-questions/1 - TEACHER角色返回403（仅STUDENT/ADMIN可删）")
    void delete_teacher_forbidden_403() throws Exception {
        mockMvc.perform(delete("/wrong-questions/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }
}
