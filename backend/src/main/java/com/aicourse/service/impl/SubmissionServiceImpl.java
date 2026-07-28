package com.aicourse.service.impl;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.PlagiarismCheckService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {

    private final TaskService taskService;
    private final PlagiarismCheckService plagiarismCheckService;
    private final AIService aiService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public SubmissionServiceImpl(@Lazy TaskService taskService,
                                  PlagiarismCheckService plagiarismCheckService,
                                  AIService aiService,
                                  ObjectMapper objectMapper,
                                  NotificationService notificationService) {
        this.taskService = taskService;
        this.plagiarismCheckService = plagiarismCheckService;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    /**
     * 提交作业（含逾期判断、提交次数上限、草稿转正）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Submission submitAssignment(Long taskId, Long studentId, String content, List<String> fileUrls) {
        // 检查任务是否存在
        Task task = taskService.getById(taskId);
        if (task == null) {
            throw new BusinessException("作业任务不存在");
        }
        if (!Boolean.TRUE.equals(task.getIsPublished())) {
            throw new BusinessException("该作业尚未发布");
        }

        // 统计"正式提交"次数（草稿不计入），用于提交次数上限判断与尝试序号
        LambdaQueryWrapper<Submission> submittedWrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, taskId)
                .eq(Submission::getStudentId, studentId)
                .ne(Submission::getStatus, "DRAFT");
        long submittedCount = this.count(submittedWrapper);

        // 提交次数上限（maxSubmissions > 0 时生效），对标超星可配提交上限
        Integer maxSubmissions = task.getMaxSubmissions();
        if (maxSubmissions != null && maxSubmissions > 0 && submittedCount >= maxSubmissions) {
            throw new BusinessException("已达到最大提交次数限制（" + maxSubmissions + "次），无法再次提交");
        }

        // 判断是否逾期
        LocalDateTime now = LocalDateTime.now();
        boolean isLate = false;
        BigDecimal latePenalty = BigDecimal.ZERO;

        if (task.getDueDate() != null && now.isAfter(task.getDueDate())) {
            isLate = true;
            // 计算逾期天数
            long lateDays = ChronoUnit.DAYS.between(task.getDueDate().toLocalDate(), now.toLocalDate());
            if (lateDays < 1) lateDays = 1; // 至少算1天

            // 可配逾期策略：latePenaltyPerDay 为百分比（如10=10%/天），null 时使用默认 10%
            BigDecimal maxScore = task.getMaxScore() != null ? task.getMaxScore() : new BigDecimal("100");
            latePenalty = maxScore.multiply(resolveLateRate(task)).multiply(BigDecimal.valueOf(lateDays));
            // 不超过满分
            if (latePenalty.compareTo(maxScore) > 0) {
                latePenalty = maxScore;
            }
            latePenalty = latePenalty.setScale(2, RoundingMode.HALF_UP);
        }

        // 复用该学生的草稿行转为正式提交（避免草稿+正式双行），否则新建
        LambdaQueryWrapper<Submission> draftWrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, taskId)
                .eq(Submission::getStudentId, studentId)
                .eq(Submission::getStatus, "DRAFT")
                .last("LIMIT 1");
        Submission draft = this.getOne(draftWrapper);

        int attemptNumber = (int) submittedCount + 1;

        Submission submission;
        if (draft != null) {
            submission = draft;
        } else {
            submission = new Submission();
            submission.setTaskId(taskId);
            submission.setStudentId(studentId);
        }
        submission.setContent(content);
        if (fileUrls != null && !fileUrls.isEmpty()) {
            submission.setFileUrls(String.join(",", fileUrls));
        } else if (draft != null) {
            submission.setFileUrls(draft.getFileUrls()); // 正式提交未带新附件时保留草稿期附件
        }
        submission.setStatus("SUBMITTED");
        submission.setSubmittedAt(now);
        submission.setAttemptNumber(attemptNumber);
        submission.setIsLate(isLate);
        submission.setLatePenalty(latePenalty);
        submission.setMaxScore(task.getMaxScore());
        submission.setViewCount(draft != null ? (draft.getViewCount() != null ? draft.getViewCount() : 0) : 0);

        if (draft != null) {
            this.updateById(submission);
        } else {
            this.save(submission);
        }
        return submission;
    }

    /**
     * 保存作业草稿（对标超星：可先存草稿再正式提交）
     * 每位学生每任务仅保留一份草稿，重复保存覆盖。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Submission saveDraft(Long taskId, Long studentId, String content, List<String> fileUrls) {
        Task task = taskService.getById(taskId);
        if (task == null) {
            throw new BusinessException("作业任务不存在");
        }
        if (!Boolean.TRUE.equals(task.getIsPublished())) {
            throw new BusinessException("该作业尚未发布");
        }

        LambdaQueryWrapper<Submission> draftWrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, taskId)
                .eq(Submission::getStudentId, studentId)
                .eq(Submission::getStatus, "DRAFT")
                .last("LIMIT 1");
        Submission draft = this.getOne(draftWrapper);
        if (draft == null) {
            draft = new Submission();
            draft.setTaskId(taskId);
            draft.setStudentId(studentId);
            draft.setStatus("DRAFT");
            draft.setViewCount(0);
        }
        draft.setContent(content);
        if (fileUrls != null && !fileUrls.isEmpty()) {
            draft.setFileUrls(String.join(",", fileUrls));
        }
        draft.setMaxScore(task.getMaxScore());
        if (draft.getId() == null) {
            this.save(draft);
        } else {
            this.updateById(draft);
        }
        return draft;
    }

    /**
     * 解析逾期扣分比例（小数）：latePenaltyPerDay 为百分比存储（如10=10%/天），null 时用默认 10%
     */
    private BigDecimal resolveLateRate(Task task) {
        if (task.getLatePenaltyPerDay() != null) {
            return task.getLatePenaltyPerDay().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }
        return new BigDecimal("0.1");
    }

    /**
     * 重新提交
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Submission resubmit(Long submissionId, String content, List<String> fileUrls) {
        Submission existing = this.getById(submissionId);
        if (existing == null) {
            throw new BusinessException("提交记录不存在");
        }
        if (!"RETURNED".equals(existing.getStatus()) && !"SUBMITTED".equals(existing.getStatus())) {
            throw new BusinessException("当前状态不允许重新提交");
        }

        // 获取任务信息判断逾期
        Task task = taskService.getById(existing.getTaskId());
        LocalDateTime now = LocalDateTime.now();
        boolean isLate = false;
        BigDecimal latePenalty = BigDecimal.ZERO;

        if (task != null && task.getDueDate() != null && now.isAfter(task.getDueDate())) {
            isLate = true;
            long lateDays = ChronoUnit.DAYS.between(task.getDueDate().toLocalDate(), now.toLocalDate());
            if (lateDays < 1) lateDays = 1;
            BigDecimal maxScore = task.getMaxScore() != null ? task.getMaxScore() : new BigDecimal("100");
            latePenalty = maxScore.multiply(resolveLateRate(task))
                    .multiply(BigDecimal.valueOf(lateDays));
            if (latePenalty.compareTo(maxScore) > 0) {
                latePenalty = maxScore;
            }
            latePenalty = latePenalty.setScale(2, RoundingMode.HALF_UP);
        }

        // 创建新的提交记录
        int newAttemptNumber = existing.getAttemptNumber() + 1;
        Submission newSubmission = new Submission();
        newSubmission.setTaskId(existing.getTaskId());
        newSubmission.setStudentId(existing.getStudentId());
        newSubmission.setContent(content);
        if (fileUrls != null && !fileUrls.isEmpty()) {
            newSubmission.setFileUrls(String.join(",", fileUrls));
        }
        newSubmission.setStatus("RESUBMITTED");
        newSubmission.setSubmittedAt(now);
        newSubmission.setAttemptNumber(newAttemptNumber);
        newSubmission.setIsLate(isLate);
        newSubmission.setLatePenalty(latePenalty);
        newSubmission.setMaxScore(task != null ? task.getMaxScore() : existing.getMaxScore());
        newSubmission.setViewCount(0);

        this.save(newSubmission);
        return newSubmission;
    }

    /**
     * 退回作业（记录退回原因到feedback字段）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Submission returnSubmission(Long submissionId, String reason) {
        Submission submission = this.getById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }
        if ("GRADED".equals(submission.getStatus())) {
            throw new BusinessException("已评分的作业无法退回");
        }

        submission.setStatus("RETURNED");
        // 将退回原因写入feedback字段作为returnReason
        String returnReason = reason != null ? reason : "未填写退回原因";
        submission.setFeedback(returnReason);
        submission.setReturnedAt(LocalDateTime.now());
        // 在submissionData中记录退回元数据
        String existingData = submission.getSubmissionData() != null ? submission.getSubmissionData() : "{}";
        String updatedData = updateJsonField(existingData, "returnReason", returnReason);
        updatedData = updateJsonField(updatedData, "returnedAt", LocalDateTime.now().toString());
        submission.setSubmissionData(updatedData);
        this.updateById(submission);

        return submission;
    }

    /**
     * 教师评分
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = "analytics", key = "'platform'"),
            @CacheEvict(cacheNames = "analytics", key = "'overview'"),
            @CacheEvict(cacheNames = "analytics", key = "'teacher_' + #graderId")
    })
    public Submission gradeSubmission(Long submissionId, BigDecimal score, String feedback, Long graderId) {
        Submission submission = this.getById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }

        // 计算实际得分（扣除逾期扣分）
        BigDecimal finalScore = score;
        if (Boolean.TRUE.equals(submission.getIsLate()) && submission.getLatePenalty() != null &&
                submission.getLatePenalty().compareTo(BigDecimal.ZERO) > 0) {
            finalScore = score.subtract(submission.getLatePenalty());
            if (finalScore.compareTo(BigDecimal.ZERO) < 0) {
                finalScore = BigDecimal.ZERO;
            }
        }

        submission.setScore(finalScore);
        submission.setFeedback(feedback);
        submission.setGraderId(graderId);
        submission.setGradedAt(LocalDateTime.now());
        submission.setStatus("GRADED");

        // 计算百分比
        if (submission.getMaxScore() != null && submission.getMaxScore().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percentage = finalScore.divide(submission.getMaxScore(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            submission.setPercentage(percentage);
        }

        // 在submissionData中记录批阅次数（reviewCount）
        String existingData = submission.getSubmissionData() != null ? submission.getSubmissionData() : "{}";
        int currentReviewCount = extractIntFromJson(existingData, "reviewCount");
        String updatedData = updateJsonField(existingData, "reviewCount", String.valueOf(currentReviewCount + 1));
        updatedData = updateJsonField(updatedData, "lastReviewAt", LocalDateTime.now().toString());
        updatedData = updateJsonField(updatedData, "lastGraderId", String.valueOf(graderId));
        submission.setSubmissionData(updatedData);

        this.updateById(submission);

        // 修复 P1-1：作业批阅完成后自动通知学生（铃铛不再失效）
        try {
            Task task = taskService.getById(submission.getTaskId());
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("studentId", submission.getStudentId());
            eventData.put("submissionId", submission.getId());
            eventData.put("taskTitle", task != null ? task.getTitle() : "");
            notificationService.onEvent("SUBMISSION_GRADED", eventData);
        } catch (RuntimeException e) {
            log.warn("作业批阅通知学生失败: submissionId={}", submissionId, e);
            // 通知失败不影响批阅主流程
        }

        return submission;
    }

    /**
     * AI智能评分（增强版：LLM 返回评分+评语并解析结构化分数）
     * <p>
     * 1) 构建结构化提示词要求 LLM 输出 JSON：{ "score": 85, "feedback": "...", "highlights": "...", "suggestions": "..." }
     * 2) 解析分数，存入 submissionData.aiSuggestedScore + aiSuggestedFeedback
     * 3) 教师可在批阅时参考 AI 建议分数，一键确认或人工调整
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Submission aiGradeSubmission(Long submissionId) {
        Submission submission = this.getById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }
        if (submission.getContent() == null || submission.getContent().isEmpty()) {
            throw new BusinessException("提交内容为空，无法进行AI评分");
        }

        try {
            Task task = taskService.getById(submission.getTaskId());
            String maxScoreStr = task != null && task.getMaxScore() != null
                    ? task.getMaxScore().toString() : "100";
            String requirements = task != null && task.getRequirements() != null
                    ? task.getRequirements() : "无额外要求";

            // 结构化提示词：要求 LLM 返回 JSON，包含评分、评语、亮点、改进建议
            String prompt = "请以一位严格的大学教师身份批改以下学生作业，满分" + maxScoreStr + "分。\n"
                    + "【作业要求】\n" + requirements + "\n"
                    + "【学生提交内容】\n" + submission.getContent() + "\n\n"
                    + "请返回纯JSON（不要Markdown代码块）：\n"
                    + "{\n"
                    + "  \"score\": 数字分数(0-" + maxScoreStr + "),\n"
                    + "  \"feedback\": \"总体评语（100字以内）\",\n"
                    + "  \"highlights\": \"亮点分析（50字以内）\",\n"
                    + "  \"suggestions\": \"改进建议（100字以内）\"\n"
                    + "}";

            String aiResponse = aiService.generateContent("homework_grading", prompt);

            // 解析 LLM 返回的 JSON
            BigDecimal aiScore = null;
            String aiFeedback = null;
            String highlights = null;
            String suggestions = null;
            try {
                ObjectMapper om = new ObjectMapper();
                JsonNode root = om.readTree(aiResponse);
                if (root.has("score")) {
                    aiScore = new BigDecimal(root.get("score").asText());
                }
                if (root.has("feedback")) {
                    aiFeedback = root.get("feedback").asText();
                }
                if (root.has("highlights")) {
                    highlights = root.get("highlights").asText();
                }
                if (root.has("suggestions")) {
                    suggestions = root.get("suggestions").asText();
                }
            } catch (Exception parseEx) {
                // JSON 解析失败时使用原始文本作为评语
                log.warn("AI评分JSON解析失败，使用原始文本: {}", parseEx.getMessage());
                aiFeedback = aiResponse;
            }

            // 保存 AI 评语到 aiFeedback 字段
            if (aiFeedback != null) {
                submission.setAiFeedback(aiFeedback);
            }

            // 保存 AI 建议分数和结构化评语到 submissionData JSON
            String existingData = submission.getSubmissionData() != null ? submission.getSubmissionData() : "{}";
            if (aiScore != null) {
                existingData = updateJsonField(existingData, "aiSuggestedScore", aiScore.toString());
            }
            if (highlights != null) {
                existingData = updateJsonField(existingData, "aiHighlights", highlights);
            }
            if (suggestions != null) {
                existingData = updateJsonField(existingData, "aiSuggestions", suggestions);
            }
            existingData = updateJsonField(existingData, "aiGradedAt", LocalDateTime.now().toString());
            submission.setSubmissionData(existingData);

            this.updateById(submission);

            log.info("AI评分完成: submissionId={}, aiScore={}, hasFeedback={}",
                    submissionId, aiScore, aiFeedback != null);

        } catch (RuntimeException e) {
            // AI评分失败不影响主流程，仅记录错误信息
            log.error("AI评分失败: submissionId={}", submissionId, e);
            submission.setAiFeedback("AI评分失败：" + e.getMessage());
            this.updateById(submission);
        }

        return submission;
    }

    /**
     * 计算逾期扣分
     */
    @Override
    public BigDecimal calculateLatePenalty(Long submissionId) {
        Submission submission = this.getById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }
        if (submission.getLatePenalty() != null) {
            return submission.getLatePenalty();
        }

        // 如果还没有计算过，重新计算
        Task task = taskService.getById(submission.getTaskId());
        if (task == null || task.getDueDate() == null || submission.getSubmittedAt() == null) {
            return BigDecimal.ZERO;
        }

        if (submission.getSubmittedAt().isAfter(task.getDueDate())) {
            long lateDays = ChronoUnit.DAYS.between(task.getDueDate().toLocalDate(),
                    submission.getSubmittedAt().toLocalDate());
            if (lateDays < 1) lateDays = 1;

            BigDecimal maxScore = task.getMaxScore() != null ? task.getMaxScore() : new BigDecimal("100");
            BigDecimal penalty = maxScore.multiply(resolveLateRate(task))
                    .multiply(BigDecimal.valueOf(lateDays));

            if (penalty.compareTo(maxScore) > 0) {
                penalty = maxScore;
            }
            return penalty.setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    /**
     * 增加查看次数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementViewCount(Long submissionId) {
        Submission submission = this.getById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }

        int currentViewCount = submission.getViewCount() != null ? submission.getViewCount() : 0;
        submission.setViewCount(currentViewCount + 1);
        this.updateById(submission);
    }

    /**
     * 触发查重检测
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlagiarismCheck triggerPlagiarismCheck(Long submissionId) {
        Submission submission = this.getById(submissionId);
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
            List<Submission> otherSubmissions = this.list(
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
                        log.warn("查重相似度解析失败: submissionId={}, result={}", submissionId, result);
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
            check.setStatus("ERROR");
            check.setErrorMessage(e.getMessage());
        }

        plagiarismCheckService.updateById(check);
        return check;
    }

    /**
     * 获取学生的任务提交记录
     */
    @Override
    public Submission getByTaskAndStudent(Long taskId, Long studentId) {
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, taskId)
                .eq(Submission::getStudentId, studentId)
                .orderByDesc(Submission::getCreatedAt)
                .last("LIMIT 1");
        return this.getOne(wrapper);
    }

    /**
     * 获取提交的查重摘要信息
     * 查询该提交关联的所有查重记录，汇总相似度、详情等
     */
    @Override
    public Map<String, Object> getPlagiarismSummary(Long submissionId) {
        Submission submission = this.getById(submissionId);
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
    @Override
    public Map<String, Object> getSubmissionDetail(Long submissionId) {
        Submission submission = this.getById(submissionId);
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
            int reviewCount = extractIntFromJson(submission.getSubmissionData(), "reviewCount");
            detail.put("reviewCount", reviewCount);
            String returnReason = extractStringFromJson(submission.getSubmissionData(), "returnReason");
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

    // ==================== 私有JSON辅助方法（修复 P0-4：统一用 ObjectMapper，杜绝字符串拼接导致的非法 JSON） ====================

    /**
     * 将 submissionData JSON 解析为可变 Map（容错：解析失败返回空 Map）
     */
    private Map<String, Object> parseData(String json) {
        if (json == null || json.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return map != null ? map : new LinkedHashMap<>();
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 将 Map 序列化回 JSON 字符串（容错）
     */
    private String serializeData(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (IOException e) {
            return "{}";
        }
    }

    /**
     * 更新 JSON 中的字段值（值为字符串形式存储，与历史数据兼容）
     */
    private String updateJsonField(String json, String fieldKey, String fieldValue) {
        Map<String, Object> map = parseData(json);
        map.put(fieldKey, fieldValue);
        return serializeData(map);
    }

    /**
     * 从 JSON 中提取整数字段（兼容字符串/数字两种存储形态）
     */
    private int extractIntFromJson(String json, String fieldKey) {
        Map<String, Object> map = parseData(json);
        Object value = map.get(fieldKey);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 从 JSON 中提取字符串字段
     */
    private String extractStringFromJson(String json, String fieldKey) {
        Map<String, Object> map = parseData(json);
        Object value = map.get(fieldKey);
        return value != null ? value.toString() : null;
    }
}
