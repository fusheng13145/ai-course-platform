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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {

    private final TaskService taskService;
    private final PlagiarismCheckService plagiarismCheckService;
    private final AIService aiService;

    @Autowired
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
     * 退回作业
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
        submission.setFeedback(reason);
        submission.setReturnedAt(LocalDateTime.now());
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
}
