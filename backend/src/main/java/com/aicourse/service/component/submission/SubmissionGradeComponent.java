package com.aicourse.service.component.submission;

import com.aicourse.constant.SubmissionStatus;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * 提交评分组件：教师评分 / 教师端批改 / AI 智能评分 / 逾期扣分重算。
 * <p>拆分自原 SubmissionServiceImpl（2026-08-29）。注意：gradeSubmission 的 @Caching 缓存失效注解
 * 保留在门面委托方法上（Spring 缓存代理按方法拦截），本组件只承载业务逻辑。</p>
 */
@Slf4j
@Component
public class SubmissionGradeComponent {

    private final SubmissionMapper submissionMapper;
    private final TaskService taskService;
    private final NotificationService notificationService;
    private final AIService aiService;
    private final SubmissionJsonComponent jsonSupport;
    private final SubmissionWriteComponent writeComponent;

    public SubmissionGradeComponent(SubmissionMapper submissionMapper,
                                    @Lazy TaskService taskService,
                                    NotificationService notificationService,
                                    AIService aiService,
                                    SubmissionJsonComponent jsonSupport,
                                    SubmissionWriteComponent writeComponent) {
        this.submissionMapper = submissionMapper;
        this.taskService = taskService;
        this.notificationService = notificationService;
        this.aiService = aiService;
        this.jsonSupport = jsonSupport;
        this.writeComponent = writeComponent;
    }

    /**
     * 教师评分
     */
    public Submission gradeSubmission(Long submissionId, BigDecimal score, String feedback, Long graderId) {
        Submission submission = submissionMapper.selectById(submissionId);
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
        submission.setStatus(SubmissionStatus.GRADED);

        // 计算百分比
        if (submission.getMaxScore() != null && submission.getMaxScore().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percentage = finalScore.divide(submission.getMaxScore(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            submission.setPercentage(percentage);
        }

        // 在submissionData中记录批阅次数（reviewCount）
        String existingData = submission.getSubmissionData() != null ? submission.getSubmissionData() : "{}";
        int currentReviewCount = jsonSupport.extractIntFromJson(existingData, "reviewCount");
        String updatedData = jsonSupport.updateJsonField(existingData, "reviewCount", String.valueOf(currentReviewCount + 1));
        updatedData = jsonSupport.updateJsonField(updatedData, "lastReviewAt", LocalDateTime.now().toString());
        updatedData = jsonSupport.updateJsonField(updatedData, "lastGraderId", String.valueOf(graderId));
        submission.setSubmissionData(updatedData);

        submissionMapper.updateById(submission);

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
     * 教师端批改作业（TeacherController.gradeSubmission 业务下沉，行为保持一致）
     */
    public Submission gradeSubmissionByTeacher(Long assignmentId, Long submissionId, Submission grade, Long graderId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }
        if (!assignmentId.equals(submission.getTaskId())) {
            throw new BusinessException(403, "提交记录不属于该作业");
        }
        submission.setScore(grade.getScore());
        submission.setFeedback(grade.getFeedback());
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(LocalDateTime.now());
        submission.setGraderId(graderId);
        submissionMapper.updateById(submission);
        return submission;
    }

    /**
     * AI智能评分（增强版：LLM 返回评分+评语并解析结构化分数）
     * <p>
     * 1) 构建结构化提示词要求 LLM 输出 JSON：{ "score": 85, "feedback": "...", "highlights": "...", "suggestions": "..." }
     * 2) 解析分数，存入 submissionData.aiSuggestedScore + aiSuggestedFeedback
     * 3) 教师可在批阅时参考 AI 建议分数，一键确认或人工调整
     */
    public Submission aiGradeSubmission(Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
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
                JsonNode root = jsonSupport.readTree(aiResponse);
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
                existingData = jsonSupport.updateJsonField(existingData, "aiSuggestedScore", aiScore.toString());
            }
            if (highlights != null) {
                existingData = jsonSupport.updateJsonField(existingData, "aiHighlights", highlights);
            }
            if (suggestions != null) {
                existingData = jsonSupport.updateJsonField(existingData, "aiSuggestions", suggestions);
            }
            existingData = jsonSupport.updateJsonField(existingData, "aiGradedAt", LocalDateTime.now().toString());
            submission.setSubmissionData(existingData);

            submissionMapper.updateById(submission);

            log.info("AI评分完成: submissionId={}, aiScore={}, hasFeedback={}",
                    submissionId, aiScore, aiFeedback != null);

        } catch (RuntimeException e) {
            // AI评分失败不影响主流程，仅记录错误信息
            log.error("AI评分失败: submissionId={}", submissionId, e);
            submission.setAiFeedback("AI评分失败：" + e.getMessage());
            submissionMapper.updateById(submission);
        }

        return submission;
    }

    /**
     * 计算逾期扣分
     */
    public BigDecimal calculateLatePenalty(Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
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
            BigDecimal penalty = maxScore.multiply(writeComponent.resolveLateRate(task))
                    .multiply(BigDecimal.valueOf(lateDays));

            if (penalty.compareTo(maxScore) > 0) {
                penalty = maxScore;
            }
            return penalty.setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }
}
