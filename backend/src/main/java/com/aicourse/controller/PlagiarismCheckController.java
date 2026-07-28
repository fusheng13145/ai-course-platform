package com.aicourse.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.entity.PlagiarismCheck;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.entity.PlagiarismMatch;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.service.PlagiarismCheckService;
import com.aicourse.dto.PlagiarismCheckCreateDTO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 查重检测接口控制器
 */
@Tag(name = "查重检测", description = "作业查重检查与相似度匹配结果")
@RestController
@RequestMapping("/plagiarism-checks")
public class PlagiarismCheckController {

    private final PlagiarismCheckService plagiarismCheckService;

    public PlagiarismCheckController(PlagiarismCheckService plagiarismCheckService) {
        this.plagiarismCheckService = plagiarismCheckService;
    }

    /**
     * 创建查重任务
     * @param body 请求体（包含submissionId和checkType）
     * @return 新创建的查重任务
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<PlagiarismCheck> create(@Valid @RequestBody PlagiarismCheckCreateDTO dto) {
        Long submissionId = dto.getSubmissionId();
        String checkType = dto.getCheckType() != null ? dto.getCheckType() : "TEXT";
        PlagiarismCheck check = plagiarismCheckService.createCheck(submissionId, checkType);
        return ApiResponse.success("查重任务创建成功", check);
    }

    /**
     * 执行查重检测
     * @param id 查重任务ID
     * @return 操作结果
     */
    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> execute(@PathVariable Long id) {
        plagiarismCheckService.executeCheck(id);
        return ApiResponse.success("查重执行完成", null);
    }

    /**
     * 获取查重结果详情
     * @param id 查重任务ID
     * @return 查重结果详情（含匹配明细列表）
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getCheckResult(@PathVariable Long id) {
        Map<String, Object> result = plagiarismCheckService.getCheckResult(id);
        return ApiResponse.success(result);
    }

    /**
     * 获取高相似度匹配列表
     * @param id       查重任务ID
     * @param threshold 相似度阈值（可选，默认0.3）
     * @return 高相似度匹配明细列表
     */
    @GetMapping("/{id}/matches")
    public ApiResponse<List<PlagiarismMatch>> getHighSimilarityMatches(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0.3") BigDecimal threshold) {
        List<PlagiarismMatch> matches = plagiarismCheckService.getHighSimilarityMatches(id, threshold);
        return ApiResponse.success(matches);
    }

    /**
     * 获取某提交的查重历史记录
     * @param submissionId 提交记录ID
     * @return 查重历史列表
     */
    @GetMapping("/submission/{submissionId}")
    public ApiResponse<List<PlagiarismCheck>> getSubmissionHistory(@PathVariable Long submissionId) {
        List<PlagiarismCheck> checks = plagiarismCheckService.getSubmissionChecks(submissionId);
        return ApiResponse.success(checks);
    }
}
