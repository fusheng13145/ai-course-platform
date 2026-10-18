package com.aicourse.controller;

import com.aicourse.service.CourseKnowledgeBaseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CourseKnowledgeBaseController 接口测试。
 * 覆盖：路由委托与参数透传、鉴权（未认证 401 / STUDENT 403 / TEACHER 与 ADMIN 放行）、
 * 以及「引擎不可用」响应体（mode=UNAVAILABLE / code=503）与「课程不存在」业务异常（404）的信封转换。
 */
@WebMvcTest(CourseKnowledgeBaseController.class)
class CourseKnowledgeBaseControllerTest extends ControllerTestBase {

    @MockitoBean
    private CourseKnowledgeBaseService courseKnowledgeBaseService;

    @Test
    @DisplayName("POST /rag/course/1/build - TEACHER 构建返回 code=200 且字段透传")
    void build_teacher_returns_success() throws Exception {
        when(courseKnowledgeBaseService.buildFromCourse(1L))
                .thenReturn("{\"courseId\":1,\"mode\":\"LOCAL\",\"total\":3,\"added\":0,\"skipped\":0}");

        mockMvc.perform(post("/rag/course/1/build").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.mode").value("LOCAL"))
                .andExpect(jsonPath("$.data.total").value(3));

        verify(courseKnowledgeBaseService).buildFromCourse(eq(1L));
    }

    @Test
    @DisplayName("POST /rag/course/2/build - ADMIN 构建返回 code=200")
    void build_admin_returns_success() throws Exception {
        when(courseKnowledgeBaseService.buildFromCourse(2L))
                .thenReturn("{\"courseId\":2,\"mode\":\"ENGINE\",\"added\":5}");

        mockMvc.perform(post("/rag/course/2/build").with(withUser(9L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.mode").value("ENGINE"))
                .andExpect(jsonPath("$.data.added").value(5));

        verify(courseKnowledgeBaseService).buildFromCourse(eq(2L));
    }

    @Test
    @DisplayName("POST /rag/course/1/build - 引擎不可用时透传 mode=UNAVAILABLE 与 code=503")
    void build_engine_unavailable_passes_through_degradation() throws Exception {
        when(courseKnowledgeBaseService.buildFromCourse(1L))
                .thenReturn("{\"courseId\":1,\"mode\":\"UNAVAILABLE\",\"total\":3,\"added\":0,\"skipped\":0,\"error\":\"引擎未接入\",\"code\":503}");

        mockMvc.perform(post("/rag/course/1/build").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.mode").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.code").value(503));
    }

    @Test
    @DisplayName("POST /rag/course/1/build - 课程不存在时业务异常返回 404")
    void build_course_not_found_returns_404() throws Exception {
        when(courseKnowledgeBaseService.buildFromCourse(99L))
                .thenThrow(new com.aicourse.exception.BusinessException(404, "课程不存在或已被删除"));

        mockMvc.perform(post("/rag/course/99/build").with(withUser(1L, "TEACHER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("POST /rag/course/1/build - 未认证返回401")
    void build_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/rag/course/1/build"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /rag/course/1/build - STUDENT角色返回403")
    void build_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/rag/course/1/build").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());

        verify(courseKnowledgeBaseService, never()).buildFromCourse(anyLong());
    }
}
