package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 全站搜索接口（D6）。
 * 登录后可用：/search?keyword=xx&types=knowledge,question,course,note&limit=10
 * note 类型仅检索当前用户本人的笔记。
 */
@RestController
@RequestMapping("/search")
@Tag(name = "全站搜索")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "全站聚合搜索（知识点/题目/课程/笔记）")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> search(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "knowledge,question,course,note") String types,
            @RequestParam(required = false, defaultValue = "10") int limit,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<String> typeList = Arrays.stream(types.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();
        int capped = Math.min(Math.max(limit, 1), 20);
        return ApiResponse.success(searchService.search(keyword, typeList, userId, capped));
    }
}
