package com.aicourse.service.component.submission;

import com.aicourse.common.JsonUtils;
import com.aicourse.constant.SubmissionStatus;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.integration.rag.RagContract.RagSource;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.TaskService;
import com.aicourse.service.component.ai.CourseEvidenceComponent;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
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
    private final CourseEvidenceComponent evidence;

    public SubmissionGradeComponent(SubmissionMapper submissionMapper,
                                    @Lazy TaskService taskService,
                                    NotificationService notificationService,
                                    AIService aiService,
                                    SubmissionJsonComponent jsonSupport,
                                    SubmissionWriteComponent writeComponent,
                                    CourseEvidenceComponent evidence) {
        this.submissionMapper = submissionMapper;
        this.taskService = taskService;
        this.notificationService = notificationService;
        this.aiService = aiService;
        this.jsonSupport = jsonSupport;
        this.writeComponent = writeComponent;
        this.evidence = evidence;
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

            // 课程证据：引擎召回资料 + 知识图谱命中知识点（任何一步失败都降级为空，不阻断批改）
            Long courseId = task != null ? task.getCourseId() : null;
            String taskTitle = task != null && task.getTitle() != null ? task.getTitle() : "";
            String evidenceQuery = (taskTitle + " " + requirements).trim();
            List<RagSource> sources = evidence.retrieve(evidenceQuery, courseId, 5);
            List<KnowledgePoint> points = evidence.loadPoints(courseId);
            List<KnowledgePoint> matched = evidence.matchPoints(points, evidenceQuery);

            StringBuilder evidenceContext = new StringBuilder();
            boolean hasEvidence = false;
            if (!sources.isEmpty()) {
                evidenceContext.append("\n【课程参考资料】\n").append(evidence.renderSources(sources));
                hasEvidence = true;
            }
            if (!matched.isEmpty()) {
                evidenceContext.append("\n【课程知识点（知识图谱）】\n").append(evidence.renderPoints(matched));
                hasEvidence = true;
            } else if (!points.isEmpty()) {
                String scope = evidence.scopeText(points);
                if (!scope.isBlank()) {
                    evidenceContext.append("\n【课程知识点范围】\n").append(scope).append('\n');
                    hasEvidence = true;
                }
            }
            if (hasEvidence) {
                evidenceContext.append("\n批改口径：以课程资料与知识点为准；学生答案覆盖要点计分，明显违背课程资料时扣分；"
                        + "资料未覆盖处按一般学科标准判断，不得臆造课程事实。\n");
            }

            // 结构化提示词：要求 LLM 返回 JSON，包含评分、评语、亮点、改进建议
            String prompt = "请以一位严格的大学教师身份批改以下学生作业，满分" + maxScoreStr + "分。\n"
                    + "【作业要求】\n" + requirements + "\n"
                    + "【学生提交内容】\n" + submission.getContent() + "\n"
                    + evidenceContext
                    + "\n请返回纯JSON（不要Markdown代码块）：\n"
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
                // LLM 常把 JSON 包进 ```json 栅栏；不先剥壳就会解析失败并走到下面的"原始文本当评语"
                // 分支——那是把 ``` 连同栅栏一起存进学生可见的 aiFeedback，且分数丢失（DR-87 同族）。
                JsonNode root = jsonSupport.readTree(JsonUtils.extractJsonObject(aiResponse));
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
            // 记录本次批改实际依据的证据名（无证据则不写该字段，避免伪造溯源）
            List<String> names = evidence.evidenceNames(sources, matched.isEmpty() ? points : matched);
            if (!names.isEmpty()) {
                existingData = jsonSupport.updateJsonField(existingData, "aiEvidence", String.join("、", names));
            }
            existingData = jsonSupport.updateJsonField(existingData, "aiGradedAt", LocalDateTime.now().toString());
            submission.setSubmissionData(existingData);

            updateAiColumns(submissionId, aiFeedback, existingData);

            log.info("AI评分完成: submissionId={}, aiScore={}, hasFeedback={}",
                    submissionId, aiScore, aiFeedback != null);

        } catch (RuntimeException e) {
            // AI评分失败不影响主流程，仅记录错误信息；同样只写 AI 列，不用陈旧实体整行更新
            log.error("AI评分失败: submissionId={}", submissionId, e);
            String failureFeedback = "AI评分失败：" + e.getMessage();
            submission.setAiFeedback(failureFeedback);
            updateAiColumns(submissionId, failureFeedback, null);
        }

        return submission;
    }

    /**
     * 只更新 AI 派生列。异步预批改读到的是一条陈旧快照，整行 updateById 会把窗口内
     * 教师已写入的 status/score/graderId/gradedAt 一并冲回 SUBMITTED。
     */
    private void updateAiColumns(Long submissionId, String aiFeedback, String submissionData) {
        LambdaUpdateWrapper<Submission> update = new LambdaUpdateWrapper<Submission>()
                .eq(Submission::getId, submissionId);
        boolean hasSet = false;
        if (aiFeedback != null) {
            update.set(Submission::getAiFeedback, aiFeedback);
            hasSet = true;
        }
        if (submissionData != null) {
            update.set(Submission::getSubmissionData, submissionData);
            hasSet = true;
        }
        if (hasSet) {
            // update(null, wrapper) 不会触发实体上的 INSERT_UPDATE 自动填充，显式补时间戳
            update.set(Submission::getUpdatedAt, LocalDateTime.now());
            submissionMapper.update(null, update);
        }
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
