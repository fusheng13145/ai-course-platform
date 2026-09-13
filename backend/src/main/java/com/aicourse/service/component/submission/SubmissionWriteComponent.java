package com.aicourse.service.component.submission;

import com.aicourse.constant.SubmissionStatus;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 提交写组件：提交/草稿/重交/退回 四类写操作 + 逾期扣分共享解析。
 * <p>拆分自原 SubmissionServiceImpl（2026-08-29），组件持有 SubmissionMapper 直接落库（替代 this.getById/save/updateById）。</p>
 */
@Slf4j
@Component
public class SubmissionWriteComponent {

    private final SubmissionMapper submissionMapper;
    private final TaskService taskService;
    private final SubmissionJsonComponent jsonSupport;
    private final Counter submissionsCreated;

    public SubmissionWriteComponent(SubmissionMapper submissionMapper,
                                    @Lazy TaskService taskService,
                                    SubmissionJsonComponent jsonSupport,
                                    MeterRegistry meterRegistry) {
        this.submissionMapper = submissionMapper;
        this.taskService = taskService;
        this.jsonSupport = jsonSupport;
        this.submissionsCreated = Counter.builder("aicourse_submissions_created_total")
                .description("作业正式提交（含草稿转正）累计次数")
                .register(meterRegistry);
    }

    /**
     * 提交作业（含逾期判断、提交次数上限、草稿转正）
     */
    public Submission submitAssignment(Long taskId, Long studentId, String content, List<String> fileUrls) {
        log.info("提交提交");

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
        long submittedCount = submissionMapper.selectCount(submittedWrapper);

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
        Submission draft = submissionMapper.selectOne(draftWrapper, false);

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
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(now);
        submission.setAttemptNumber(attemptNumber);
        submission.setIsLate(isLate);
        submission.setLatePenalty(latePenalty);
        submission.setMaxScore(task.getMaxScore());
        submission.setViewCount(draft != null ? (draft.getViewCount() != null ? draft.getViewCount() : 0) : 0);

        if (draft != null) {
            submissionMapper.updateById(submission);
        } else {
            submissionMapper.insert(submission);
        }
        submissionsCreated.increment();
        return submission;
    }

    /**
     * 保存作业草稿（对标超星：可先存草稿再正式提交）
     * 每位学生每任务仅保留一份草稿，重复保存覆盖。
     */
    public Submission saveDraft(Long taskId, Long studentId, String content, List<String> fileUrls) {
        log.info("保存提交");

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
        Submission draft = submissionMapper.selectOne(draftWrapper, false);
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
            submissionMapper.insert(draft);
        } else {
            submissionMapper.updateById(draft);
        }
        return draft;
    }

    /**
     * 解析逾期扣分比例（小数）：latePenaltyPerDay 为百分比存储（如10=10%/天），null 时用默认 10%
     */
    public BigDecimal resolveLateRate(Task task) {
        if (task.getLatePenaltyPerDay() != null) {
            return task.getLatePenaltyPerDay().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }
        return new BigDecimal("0.1");
    }

    /**
     * 重新提交
     */
    public Submission resubmit(Long submissionId, String content, List<String> fileUrls) {
        Submission existing = submissionMapper.selectById(submissionId);
        if (existing == null) {
            throw new BusinessException("提交记录不存在");
        }
        if (!SubmissionStatus.RETURNED.equals(existing.getStatus()) && !SubmissionStatus.SUBMITTED.equals(existing.getStatus())) {
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
        newSubmission.setStatus(SubmissionStatus.RESUBMITTED);
        newSubmission.setSubmittedAt(now);
        newSubmission.setAttemptNumber(newAttemptNumber);
        newSubmission.setIsLate(isLate);
        newSubmission.setLatePenalty(latePenalty);
        newSubmission.setMaxScore(task != null ? task.getMaxScore() : existing.getMaxScore());
        newSubmission.setViewCount(0);

        submissionMapper.insert(newSubmission);
        return newSubmission;
    }

    /**
     * 退回作业（记录退回原因到feedback字段）
     */
    public Submission returnSubmission(Long submissionId, String reason) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }
        if (SubmissionStatus.GRADED.equals(submission.getStatus())) {
            throw new BusinessException("已评分的作业无法退回");
        }

        submission.setStatus(SubmissionStatus.RETURNED);
        // 将退回原因写入feedback字段作为returnReason
        String returnReason = reason != null ? reason : "未填写退回原因";
        submission.setFeedback(returnReason);
        submission.setReturnedAt(LocalDateTime.now());
        // 在submissionData中记录退回元数据
        String existingData = submission.getSubmissionData() != null ? submission.getSubmissionData() : "{}";
        String updatedData = jsonSupport.updateJsonField(existingData, "returnReason", returnReason);
        updatedData = jsonSupport.updateJsonField(updatedData, "returnedAt", LocalDateTime.now().toString());
        submission.setSubmissionData(updatedData);
        submissionMapper.updateById(submission);

        return submission;
    }
}
