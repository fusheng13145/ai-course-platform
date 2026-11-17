package com.aicourse.controller;

import com.aicourse.service.QuestionPracticeService;
import com.aicourse.vo.QuestionPracticeResultVO;
import com.aicourse.vo.QuestionPracticeVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 学生题库练习接口测试（P0-A1）。
 * 只验证 Web 层参数接线与脱敏结果；课程开关/成员关系判定由 Service 单测覆盖。
 */
@WebMvcTest(QuestionPracticeController.class)
class QuestionPracticeControllerTest extends ControllerTestBase {

    @MockitoBean
    private QuestionPracticeService questionPracticeService;

    private QuestionPracticeVO sample() {
        QuestionPracticeVO vo = new QuestionPracticeVO();
        vo.setId(1L);
        vo.setCourseId(1L);
        vo.setType("SINGLE_CHOICE");
        vo.setContent("题干");
        return vo;
    }

    @Test
    @DisplayName("GET /questions/practice - 学生返回200且列表不含答案字段")
    void list_student_returns_success() throws Exception {
        Page<QuestionPracticeVO> page = new Page<>(1, 10, 1);
        page.setRecords(java.util.List.of(sample()));
        when(questionPracticeService.listPracticeQuestions(eq(1L), isNull(), isNull(), eq(1L), eq(10L), any()))
                .thenReturn(page);

        mockMvc.perform(get("/questions/practice?courseId=1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].id").value(1))
                .andExpect(jsonPath("$.data.list[0].answer").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].explanation").doesNotExist());
    }

    @Test
    @DisplayName("GET /questions/practice - 未认证返回401")
    void list_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/questions/practice?courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /questions/practice/check - 提交答案返回判分与解析")
    void check_returns_result() throws Exception {
        when(questionPracticeService.checkAnswer(eq(1L), eq("A"), any()))
                .thenReturn(new QuestionPracticeResultVO(1L, true, "A", "解析"));

        mockMvc.perform(post("/questions/practice/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":1,\"answer\":\"A\"}")
                        .with(withUser(2L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.correctAnswer").value("A"))
                .andExpect(jsonPath("$.data.explanation").value("解析"));
    }

    @Test
    @DisplayName("POST /questions/practice/check - 缺答案返回400且不触达判分")
    void check_missingAnswer_returns400() throws Exception {
        mockMvc.perform(post("/questions/practice/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":1}")
                        .with(withUser(2L, "STUDENT")))
                .andExpect(status().isBadRequest());
        verify(questionPracticeService, never()).checkAnswer(any(), any(), any());
    }
}
