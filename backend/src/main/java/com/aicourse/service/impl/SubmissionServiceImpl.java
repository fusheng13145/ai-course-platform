package com.aicourse.service.impl;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.PlagiarismCheckService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {

    private final TaskService taskService;
    private final PlagiarismCheckService plagiarismCheckService;
    private final AIService aiService;

    public SubmissionServiceImpl(@Lazy TaskService taskService,
                                  PlagiarismCheckService plagiarismCheckService,
                                  AIService aiService) {
        this.taskService = taskService;
        this.plagiarismCheckService = plagiarismCheckService;
        this.aiService = aiService;
    }

    /**
     * 提交作业（含逾期判断）
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

        // 判断是否逾期
        LocalDateTime now = LocalDateTime.now();
        boolean isLate = false;
        BigDecimal latePenalty = BigDecimal.ZERO;

        if (task.getDueDate() != null && now.isAfter(task.getDueDate())) {
            isLate = true;
            // 计算逾期天数
            long lateDays = ChronoUnit.DAYS.between(task.getDueDate().toLocalDate(), now.toLocalDate());
            if (lateDays < 1) lateDays = 1; // 至少算1天

            // 计算逾期扣分（每天扣10%满分，不超过满分）
            BigDecimal maxScore = task.getMaxScore() != null ? task.getMaxScore() : new BigDecimal("100");
            latePenalty = maxScore.multiply(new BigDecimal("0.1"))
                    .multiply(BigDecimal.valueOf(lateDays));
            // 不超过满分
            if (latePenalty.compareTo(maxScore) > 0) {
                latePenalty = maxScore;
            }
            latePenalty = latePenalty.setScale(2, RoundingMode.HALF_UP);
        }

        // 计算提交次数
        int attemptNumber = 1;
        LambdaQueryWrapper<Submission> existingWrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, taskId)
                .eq(Submission::getStudentId, studentId);
        long existingCount = this.count(existingWrapper);
        if (existingCount > 0) {
            attemptNumber = (int) existingCount + 1;
        }

        // 创建提交记录
        Submission submission = new Submission();
        submission.setTaskId(taskId);
        submission.setStudentId(studentId);
        submission.setContent(content);
        if (fileUrls != null && !fileUrls.isEmpty()) {
            submission.setFileUrls(String.join(",", fileUrls));
        }
        submission.setStatus("SUBMITTED");
        submission.setSubmittedAt(now);
        submission.setAttemptNumber(attemptNumber);
        submission.setIsLate(isLate);
        submission.setLatePenalty(latePenalty);
        submission.setMaxScore(task.getMaxScore());
        submission.setViewCount(0);

        this.save(submission);
        return submission;
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
            latePenalty = maxScore.multiply(new BigDecimal("0.1"))
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
        return submission;
    }

    /**
     * AI智能评分
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
            // 构建AI评分参数
            StringBuilder paramsBuilder = new StringBuilder();
            paramsBuilder.append("作业内容：").append(submission.getContent());

            Task task = taskService.getById(submission.getTaskId());
            if (task != null) {
                paramsBuilder.append("\n作业要求：").append(task.getRequirements() != null ? task.getRequirements() : "");
                paramsBuilder.append("\n满分分值：").append(task.getMaxScore() != null ? task.getMaxScore() : "100");
            }

            // 调用AI服务生成评语
            String aiFeedback = aiService.generateContent("homework_grading", paramsBuilder.toString());

            submission.setAiFeedback(aiFeedback);
            this.updateById(submission);

        } catch (Exception e) {
            // AI评分失败不影响主流程，仅记录错误信息
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
            BigDecimal penalty = maxScore.multiply(new BigDecimal("0.1"))
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
                        // 解析失败跳过
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

        } catch (Exception e) {
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
        } catch (Exception e) {
            // 查重信息获取失败不影响主流程
        }

        return detail;
    }

    // ==================== 私有JSON辅助方法 ====================

    /**
     * 简单更新JSON字符串中的字段值
     */
    private String updateJsonField(String json, String fieldKey, String fieldValue) {
        if (json == null || json.isEmpty()) json = "{}";
        String content = json.trim();
        if (!content.startsWith("{")) content = "{" + content;
        if (!content.endsWith("}")) content = content + "}";
        // 如果字段已存在则替换，否则追加
        String searchStr = "\"" + fieldKey + "\":";
        if (content.contains(searchStr)) {
            // 简单替换：找到字段后的值部分进行替换
            int keyStart = content.indexOf(searchStr);
            int colonPos = keyStart + searchStr.length();
            // 找到下一个逗号或结尾符来确定值的范围
            int valueEnd = content.indexOf(",", colonPos);
            if (valueEnd < 0) valueEnd = content.lastIndexOf("}");
            return content.substring(0, colonPos) + "\"" + fieldValue + "\"" + content.substring(valueEnd);
        } else {
            // 追加新字段：替换最后一个 } 为新字段+}
            int lastBrace = content.lastIndexOf("}");
            return content.substring(0, lastBrace) + ",\"" + fieldKey + "\":\"" + fieldValue + "\"}";
        }
    }

    /**
     * 从简单JSON中提取整数值
     */
    private int extractIntFromJson(String json, String fieldKey) {
        if (json == null || json.isEmpty()) return 0;
        try {
            String searchStr = "\"" + fieldKey + "\":";
            int keyStart = json.indexOf(searchStr);
            if (keyStart < 0) return 0;
            int valueStart = keyStart + searchStr.length();
            StringBuilder valueSb = new StringBuilder();
            for (int i = valueStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (Character.isDigit(c)) {
                    valueSb.append(c);
                } else if (valueSb.length() > 0) {
                    break;
                }
            }
            return valueSb.length() > 0 ? Integer.parseInt(valueSb.toString()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 从简单JSON中提取字符串值
     */
    private String extractStringFromJson(String json, String fieldKey) {
        if (json == null || json.isEmpty()) return null;
        try {
            String searchStr = "\"" + fieldKey + "\":\"";
            int keyStart = json.indexOf(searchStr);
            if (keyStart < 0) return null;
            int valueStart = keyStart + searchStr.length();
            int valueEnd = json.indexOf("\"", valueStart);
            if (valueEnd < 0) return null;
            return json.substring(valueStart, valueEnd);
        } catch (Exception e) {
            return null;
        }
    }
}
