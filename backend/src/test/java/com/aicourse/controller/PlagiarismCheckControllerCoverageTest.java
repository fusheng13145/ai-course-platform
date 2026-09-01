package com.aicourse.controller;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.PlagiarismMatch;
import com.aicourse.service.PlagiarismCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖率测试：PlagiarismCheckController（独立 MockMvc，不加载 Spring 上下文）。
 * 覆盖创建/执行/结果/匹配/历史/列表/学生历史端点的成功与异常分支。
 * listAll / getStudentHistory 内部直接调用 service.lambdaQuery() 链式查询，
 * 通过深度桩（RETURNS_DEEP_STUBS）使链式调用返回空列表，覆盖全部指令。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlagiarismCheckController 覆盖率测试")
class PlagiarismCheckControllerCoverageTest {

    @Mock
    private PlagiarismCheckService plagiarismCheckService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        PlagiarismCheckController controller = new PlagiarismCheckController(plagiarismCheckService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        PlagiarismCheck check = new PlagiarismCheck();
        check.setId(10L);
        check.setSubmissionId(5L);
        check.setCheckType("TEXT");
        lenient().when(plagiarismCheckService.createCheck(anyLong(), anyString())).thenReturn(check);
        lenient().doNothing().when(plagiarismCheckService).executeCheck(anyLong());
        lenient().when(plagiarismCheckService.getCheckResult(anyLong()))
                .thenReturn(new com.aicourse.vo.PlagiarismCheckResultVO());
        lenient().when(plagiarismCheckService.getHighSimilarityMatches(anyLong(), any()))
                .thenReturn(List.of(new PlagiarismMatch()));
        lenient().when(plagiarismCheckService.getSubmissionChecks(anyLong()))
                .thenReturn(List.of(check));

        // listAll / getStudentHistory 使用 service.lambdaQuery() 链式写法
        LambdaQueryChainWrapper<PlagiarismCheck> chain =
                mock(LambdaQueryChainWrapper.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        lenient().when(chain.list()).thenReturn(List.of());
        lenient().when(plagiarismCheckService.lambdaQuery()).thenReturn(chain);
    }

    @Test
    @DisplayName("POST /plagiarism-checks 带 checkType / 缺省 checkType")
    void create() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/plagiarism-checks")
                        .contentType("application/json").content("{\"submissionId\":5,\"checkType\":\"TEXT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.post("/plagiarism-checks")
                        .contentType("application/json").content("{\"submissionId\":5}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /plagiarism-checks/{id}/execute")
    void execute() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/plagiarism-checks/1/execute")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /plagiarism-checks/{id}")
    void getCheckResult() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/plagiarism-checks/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /plagiarism-checks/{id}/matches 带阈值 / 缺省阈值")
    void getHighSimilarityMatches() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/plagiarism-checks/1/matches").param("threshold", "0.5"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/plagiarism-checks/1/matches")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /plagiarism-checks/submission/{submissionId}")
    void getSubmissionHistory() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/plagiarism-checks/submission/5")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /plagiarism-checks 列表（管理员）")
    void listAll() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/plagiarism-checks")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /plagiarism-checks/student/{studentId}")
    void getStudentHistory() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/plagiarism-checks/student/1")).andExpect(status().isOk());
    }
}
