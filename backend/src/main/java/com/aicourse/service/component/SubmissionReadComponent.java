package com.aicourse.service.component;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.Submission;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.PlagiarismCheckService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 提交读/查重组件：查看计数 / 查重检测 / 按任务学生查询 / 查重摘要 / 完整详情。
 * <p>拆分自原 SubmissionServiceImpl（2026-08-29），组件持有 SubmissionMapper 直接查询（替代 this.getById/list）。</p>
 */
@Slf4j
@Component
public class SubmissionReadComponent {

    private final SubmissionMapper submissionMapper;
    private final PlagiarismCheckService plagiarismCheckService;
    private final AIService aiService;
    private final SubmissionJsonSupport jsonSupport;

    public SubmissionReadComponent(SubmissionMapper submissionMapper,
                                   PlagiarismCheckService plagiarismCheckService,
                                   AIService aiService,
                                   SubmissionJsonSupport jsonSupport) {
        this.submissionMapper = submissionMapper;
        this.plagiarismCheckService = plagiarismCheckService;
        this.aiService = aiService;
        this.jsonSupport = jsonSupport;
    }

    /**
     * 增加查看次数
     */
    public void incrementViewCount(Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }

        int currentViewCount = submission.getViewCount() != null ? submission.getViewCount() : 0;
        submission.setViewCount(currentViewCount + 1);
        submissionMapper.updateById(submission);
    }

    /**
     * 触发查重检测
     */
    public PlagiarismCheck triggerPlagiarismCheck(Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }
        if (submission.getContent() == null || submission.getContent().isEmpty()) {
            throw new BusinessException("提交内容为空，无法进行查重检测");
        }

        // 创建查重记录
        PlagiarismCheck check = new PlagiarismCheck();
        check.setSubmissionId(submissionId);
        check.setStatus("PENDING");
        check.setCheckedAt(LocalDateTime.now());
        plagiarismCheckService.save(check);

        // 简单的查重逻辑：与同任务的其他提交进行对比
        try {
            List<Submission> otherSubmissions = submissionMapper.selectList(
                    new LambdaQueryWrapper<Submission>()
                            .eq(Submission::getTaskId, submission.getTaskId())
                            .ne(Submission::getId, submissionId)
                            .isNotNull(Submission::getContent)
            );

            double maxSimilarity = 0.0;
            for (Submission other : otherSubmissions) {
                if (other.getContent() != null && !other.getContent().isEmpty()) {
                    // 使用AI服务进行相似度检测
                    String result = aiService.checkPlagiarism(submission.getContent(), other.getContent());
                    // 解析结果获取相似度（简单处理）
                    try {
                        double similarity = Double.parseDouble(result.replaceAll("[^0-9.]", ""));
                        if (similarity > maxSimilarity) {
                            maxSimilarity = similarity;
                        }
                    } catch (NumberFormatException ignored) {
                        log.warn("查重相似度解析失败: submissionId={}, result={}", submissionId, result, ignored);
                    }
                }
            }

            check.setSimilarityScore(new BigDecimal(maxSimilarity).setScale(2, RoundingMode.HALF_UP));
            if (maxSimilarity >= 80.0) {
                check.setStatus("HIGH_RISK");
            } else if (maxSimilarity >= 50.0) {
                check.setStatus("MEDIUM_RISK");
            } else {
                check.setStatus("LOW_RISK");
            }

        } catch (RuntimeException e) {
            log.error("提交相关操作失败: {}", e.getMessage(), e);

            check.setStatus("ERROR");
            check.setErrorMessage(e.getMessage());
        }

        plagiarismCheckService.updateById(check);
        return check;
    }

    /**
     * 获取学生的任务提交记录
     */
    public Submission getByTaskAndStudent(Long taskId, Long studentId) {
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, taskId)
                .eq(Submission::getStudentId, studentId)
                .orderByDesc(Submission::getCreatedAt)
                .last("LIMIT 1");
        return submissionMapper.selectOne(wrapper, false);
    }

    /**
     * 获取提交的查重摘要信息
     * 查询该提交关联的所有查重记录，汇总相似度、详情等
     */
    public Map<String, Object> getPlagiarismSummary(Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("submissionId", submissionId);

        // 查询该提交的所有查重记录
        List<PlagiarismCheck> checks = plagiarismCheckService.list(
                new LambdaQueryWrapper<PlagiarismCheck>()
                        .eq(PlagiarismCheck::getSubmissionId, submissionId)
                        .orderByDesc(PlagiarismCheck::getCreatedAt)
        );

        if (checks.isEmpty()) {
            summary.put("hasCheck", false);
            summary.put("message", "暂无查重记录");
            return summary;
        }

        // 取最新一次查重结果
        PlagiarismCheck latestCheck = checks.get(0);
        summary.put("hasCheck", true);
        summary.put("latestCheckId", latestCheck.getId());
        summary.put("similarityScore", latestCheck.getSimilarityScore());
        summary.put("status", latestCheck.getStatus());
        summary.put("resultSummary", latestCheck.getResultSummary());
        summary.put("checkedAt", latestCheck.getCheckedAt());
        summary.put("highSimilarityCount", latestCheck.getHighSimilarityCount());

        // 风险等级判定
        String riskLevel;
        if (latestCheck.getSimilarityScore() != null) {
            double score = latestCheck.getSimilarityScore().doubleValue();
            if (score >= 0.7) riskLevel = "HIGH_RISK";
            else if (score >= 0.4) riskLevel = "MEDIUM_RISK";
            else if (score >= 0.2) riskLevel = "LOW_RISK";
            else riskLevel = "SAFE";
        } else {
            riskLevel = "UNKNOWN";
        }
        summary.put("riskLevel", riskLevel);

        // 查重历史次数
        summary.put("totalCheckCount", checks.size());

        // 将查重详情（checkDetail）写入摘要
        if (latestCheck.getCheckDetail() != null && !latestCheck.getCheckDetail().isEmpty()) {
            summary.put("checkDetail", latestCheck.getCheckDetail());
        }

        return summary;
    }

    /**
     * 获取提交完整详细信息（含批阅次数、查重、逾期等扩展数据）
     */
    public Map<String, Object> getSubmissionDetail(Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("id", submission.getId());
        detail.put("taskId", submission.getTaskId());
        detail.put("studentId", submission.getStudentId());
        detail.put("status", submission.getStatus());
        detail.put("score", submission.getScore());
        detail.put("maxScore", submission.getMaxScore());
        detail.put("percentage", submission.getPercentage());
        detail.put("feedback", submission.getFeedback());
        detail.put("aiFeedback", submission.getAiFeedback());
        detail.put("submittedAt", submission.getSubmittedAt());
        detail.put("gradedAt", submission.getGradedAt());
        detail.put("isLate", submission.getIsLate());
        detail.put("latePenalty", submission.getLatePenalty());
        detail.put("attemptNumber", submission.getAttemptNumber());

        // 从submissionData中解析扩展数据
        if (submission.getSubmissionData() != null && !submission.getSubmissionData().isEmpty()) {
            int reviewCount = jsonSupport.extractIntFromJson(submission.getSubmissionData(), "reviewCount");
            detail.put("reviewCount", reviewCount);
            String returnReason = jsonSupport.extractStringFromJson(submission.getSubmissionData(), "returnReason");
            if (returnReason != null && !returnReason.isEmpty()) {
                detail.put("returnReason", returnReason);
            }
        } else {
            detail.put("reviewCount", 0);
        }

        // 附加查重摘要
        try {
            Map<String, Object> plagiarismSummary = getPlagiarismSummary(submissionId);
            detail.put("plagiarismInfo", plagiarismSummary);
        } catch (RuntimeException e) {
            log.warn("获取查重摘要失败: submissionId={}", submissionId, e);
            // 查重信息获取失败不影响主流程
        }

        return detail;
    }
}
