package com.aicourse.controller;

import com.aicourse.service.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全站搜索控制器（/search，D6）接口测试。
 *
 * <p>该控制器此前**零覆盖**（全仓无 SearchController 测试、URL 字面量 0 命中）。
 * 类上标注 {@code @PreAuthorize("isAuthenticated()")}，故：未登录 → 401，
 * 已登录（任意角色）均可搜索——搜索本身不含角色越权面，越权体现在「note 类型仅检索本人笔记」，
 * 该归属隔离由 SearchServiceImpl 保证（此处以 userId 透传验证）。</p>
 *
 * <p>limit 归一化：控制器写死 {@code Math.min(Math.max(limit,1),20)}，
 * 故无论请求传何值，最终传给 Service 的 capped 恒在 [1,20]。断言按**现状**而非理想行为。</p>
 */
@WebMvcTest(SearchController.class)
class SearchControllerTest extends ControllerTestBase {

    @MockitoBean
    private SearchService searchService;

    private static List<Map<String, Object>> sampleResult() {
        return List.of(Map.of("type", "knowledge", "id", 1L, "title", "导数", "score", 0.9));
    }

    @Test
    @DisplayName("GET /search - 未登录返回401，且绝不调用搜索服务")
    void search_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/search").param("keyword", "导数"))
                .andExpect(status().isUnauthorized());
        verify(searchService, never()).search(anyString(), anyList(), any(), anyInt());
    }

    @Test
    @DisplayName("GET /search - 学生登录可搜索，userId 作为归属主体透传（note 仅检索本人）")
    void search_student_returns200_passesUserId() throws Exception {
        when(searchService.search(eq("导数"), anyList(), eq(10L), anyInt())).thenReturn(sampleResult());
        mockMvc.perform(get("/search").param("keyword", "导数").with(withUser(10L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].type").value("knowledge"))
                .andExpect(jsonPath("$.data[0].title").value("导数"));
        // 关键：控制器必须把当前登录用户 ID 传给 Service，否则「仅检索本人笔记」无从隔离。
        verify(searchService).search(eq("导数"), anyList(), eq(10L), anyInt());
    }

    @Test
    @DisplayName("GET /search - 默认 types 拆分为 knowledge/question/course/note 四类")
    void search_defaultTypes_splitsIntoFour() throws Exception {
        when(searchService.search(anyString(), anyList(), any(), anyInt())).thenReturn(List.of());
        mockMvc.perform(get("/search").param("keyword", "k").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(searchService).search(eq("k"), captor.capture(), eq(1L), anyInt());
        assertEquals(List.of("knowledge", "question", "course", "note"), captor.getValue());
    }

    @Test
    @DisplayName("GET /search - types 含空白/空项时被清洗（trim + 去空）")
    void search_typesWithBlanks_trimmedAndFiltered() throws Exception {
        when(searchService.search(anyString(), anyList(), any(), anyInt())).thenReturn(List.of());
        mockMvc.perform(get("/search")
                        .param("keyword", "k")
                        .param("types", " course , , note ,")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(searchService).search(eq("k"), captor.capture(), eq(1L), anyInt());
        assertEquals(List.of("course", "note"), captor.getValue());
    }

    @Test
    @DisplayName("GET /search - limit 归一化到 [1,20]：0→1、超20→20、正常区间不变")
    void search_limitCappedToRange() throws Exception {
        when(searchService.search(anyString(), anyList(), any(), anyInt())).thenReturn(List.of());

        // 下界钳制：limit=0（及负数）归一化为 1
        mockMvc.perform(get("/search").param("keyword", "k").param("limit", "0")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk());
        // 上界钳制：limit=100000 归一化为 20
        mockMvc.perform(get("/search").param("keyword", "k").param("limit", "100000")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk());
        // 区间内：limit=5 原样透传
        mockMvc.perform(get("/search").param("keyword", "k").param("limit", "5")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk());

        ArgumentCaptor<Integer> cap = ArgumentCaptor.forClass(Integer.class);
        verify(searchService, org.mockito.Mockito.times(3))
                .search(eq("k"), anyList(), eq(1L), cap.capture());
        List<Integer> limits = cap.getAllValues();
        assertEquals(3, limits.size());
        assertTrue(limits.get(0) == 1, "limit=0 应被钳制为下界 1，实际=" + limits.get(0));
        assertTrue(limits.get(1) == 20, "limit=100000 应被钳制为上界 20，实际=" + limits.get(1));
        assertTrue(limits.get(2) == 5, "limit=5 在合法区间应原样透传，实际=" + limits.get(2));
    }

    @Test
    @DisplayName("GET /search - 缺少必填 keyword 应被拦截返回400")
    void search_missingKeyword_returns400() throws Exception {
        mockMvc.perform(get("/search").with(withUser(1L, "STUDENT")))
                .andExpect(status().isBadRequest());
        verify(searchService, never()).search(anyString(), anyList(), any(), anyInt());
    }
}
