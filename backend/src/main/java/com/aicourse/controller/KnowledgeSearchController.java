package com.aicourse.controller;

import com.aicourse.application.knowledge.KnowledgeSearchApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.vo.KnowledgeOverviewVO;
import com.aicourse.vo.KnowledgeVOs;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识点检索与图谱概览（2026-11-18 从 KnowledgeController 拆出）。
 *
 * <p>3E-05D：可见范围短路、概览统计（覆盖率 / 核心点 / 快照版本）与 3 处 wrapper 已下移到
 * {@link KnowledgeSearchApplicationService}。</p>
 */
@RestController
@RequestMapping("/knowledge")
@Tag(name = "知识图谱-检索与概览")
public class KnowledgeSearchController {

    private final KnowledgeSearchApplicationService knowledgeSearchApplicationService;

    public KnowledgeSearchController(KnowledgeSearchApplicationService knowledgeSearchApplicationService) {
        this.knowledgeSearchApplicationService = knowledgeSearchApplicationService;
    }

    /** 知识点搜索（按名称模糊匹配）。 */
    @GetMapping("/search")
    public ApiResponse<List<KnowledgeVOs.PointVO>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        return ApiResponse.success(knowledgeSearchApplicationService.search(keyword, courseId, auth));
    }

    /**
     * 知识图谱概览统计。
     * 给定 courseId 时为课程口径；courseId 为空时按当前用户可读课程集合统计。
     */
    @GetMapping("/overview")
    public ApiResponse<KnowledgeOverviewVO> overview(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        return ApiResponse.success(knowledgeSearchApplicationService.overview(courseId, auth));
    }
}
