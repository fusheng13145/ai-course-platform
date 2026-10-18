package com.aicourse.controller;

import com.aicourse.service.AIService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.QuestionService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aicourse.entity.Question;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分页参数边界（T3）——QuestionController 题库列表页，与 Course 侧同口径的现状断言。
 *
 * <p>题库列表页 {@code @PreAuthorize(hasAnyRole('TEACHER','ADMIN'))}，越权面由 ACL 用例覆盖，
 * 此处聚焦分页参数：current≤0 兜底为 1，size 原样透传（无上限，风险项）。详见
 * {@link CourseControllerPaginationBoundaryTest} 类注释。</p>
 */
@WebMvcTest(QuestionController.class)
class QuestionControllerPaginationBoundaryTest extends ControllerTestBase {

    @MockitoBean
    private QuestionService questionService;
    @MockitoBean
    private AIService aiService;
    @MockitoBean
    private KnowledgePointService knowledgePointService;
    /** 出题治理策略：本类只测分页，不触发出题入口，故无需打桩（mock 默认 false 也不会被读到）。 */
    @MockitoBean
    private com.aicourse.service.component.ai.AiGenerationPolicy aiGenerationPolicy;

    @ParameterizedTest(name = "page={0} size={1} -> current={2} size={3}")
    @CsvSource({
            "1,10,1,10",
            "0,10,1,10",
            "-1,10,1,10",
            "1,0,1,0",
            "1,100000,1,100000",
            "99999,10,99999,10"
    })
    @DisplayName("GET /questions - 页码≤0 兜底为1、每页条数原样透传（无上限，风险项）")
    void list_normalizesCurrentButNotSize(long page, long size, long expectedCurrent, long expectedSize) throws Exception {
        when(questionService.page(any(), any())).thenReturn(new Page<Question>());
        mockMvc.perform(get("/questions")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk());

        ArgumentCaptor<IPage> captor = ArgumentCaptor.forClass(IPage.class);
        verify(questionService).page(captor.capture(), any());
        IPage<?> forwarded = captor.getValue();
        assertEquals(expectedCurrent, forwarded.getCurrent(), "页码≤0 应被兜底为 1，合法值原样透传");
        assertEquals(expectedSize, forwarded.getSize(), "每页条数应原样透传（无上限钳制，风险项）");
    }
}
