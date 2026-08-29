package com.aicourse.service.component;

import com.aicourse.entity.Question;
import com.aicourse.entity.Task;
import com.aicourse.entity.TaskCompletion;
import com.aicourse.entity.VideoLearningRecord;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.QuestionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.VideoLearningRecordService;
import com.aicourse.service.WrongQuestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学生任务点协作者：任务完成标记、视频状态/心跳（#65 反拖拽收口）、测验提交与判分（P1-3 错题收录）。
 * <p>由 {@code StudentLearningServiceImpl} 门面委托调用。</p>
 */
@Component
public class StudentTaskComponent {

    private static final Logger log = LoggerFactory.getLogger(StudentTaskComponent.class);

    private static final long VIDEO_SEEK_TOLERANCE_SEC = 3L;

    private final TaskService taskService;
    private final VideoLearningRecordService videoLearningRecordService;
    private final QuestionService questionService;
    private final WrongQuestionService wrongQuestionService;
    private final StudentTaskSupport taskSupport;

    public StudentTaskComponent(TaskService taskService,
                                VideoLearningRecordService videoLearningRecordService,
                                QuestionService questionService,
                                WrongQuestionService wrongQuestionService,
                                StudentTaskSupport taskSupport) {
        this.taskService = taskService;
        this.videoLearningRecordService = videoLearningRecordService;
        this.questionService = questionService;
        this.wrongQuestionService = wrongQuestionService;
        this.taskSupport = taskSupport;
    }

    /** 标记任务点完成（手动/按类型） */
    @Transactional
    public Map<String, Object> markTaskComplete(Long userId, Long courseId, Long taskId, String completionType) {
        taskSupport.assertEnrolled(userId, courseId);
        Task task = taskService.getById(taskId);
        if (task == null) {
            throw new BusinessException("任务点不存在");
        }
        String ct = completionType != null ? completionType : (task.getTaskType() != null ? task.getTaskType() : "MANUAL");
        taskSupport.upsertCompletion(userId, courseId, taskId, ct, x -> {
            x.setCompleted(true);
            x.setCompletedAt(LocalDateTime.now());
        });
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("taskId", taskId);
        r.put("completed", true);
        r.put("completionType", ct);
        return r;
    }

    /** 视频学习状态 */
    @Transactional(readOnly = true)
    public Map<String, Object> getVideoState(Long userId, Long courseId, Long taskId) {
        TaskCompletion tc = taskSupport.getOneCompletion(taskId, userId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("taskId", taskId);
        r.put("positionSeconds", tc != null && tc.getLastPosition() != null ? tc.getLastPosition() : 0);
        r.put("watchedDuration", tc != null && tc.getWatchedDuration() != null ? tc.getWatchedDuration() : 0);
        r.put("totalDuration", tc != null && tc.getTotalDuration() != null ? tc.getTotalDuration() : 0);
        r.put("completed", tc != null && Boolean.TRUE.equals(tc.getCompleted()));
        return r;
    }

    /** 视频心跳上报（#65：视频进度唯一真相源收口，反拖拽累计） */
    @Transactional
    public Map<String, Object> reportVideoHeartbeat(Long userId, Long courseId, Long taskId, Long resourceId,
                                                    Integer position, Integer watchedDuration,
                                                    Integer totalDuration, Double playbackSpeed) {
        taskSupport.assertEnrolled(userId, courseId);
        Task task = taskService.getById(taskId);
        if (task == null) {
            throw new BusinessException("任务点不存在");
        }

        int validWatched;
        int lastPosition;
        int totalDur;
        boolean videoCompleted;

        if (resourceId != null) {
            VideoLearningRecord rec = videoLearningRecordService.updateProgress(
                    userId, resourceId, position, totalDuration, playbackSpeed);
            validWatched = rec.getWatchDuration() != null ? rec.getWatchDuration() : 0;
            lastPosition = rec.getLastPosition() != null ? rec.getLastPosition() : 0;
            totalDur = rec.getTotalDuration() != null ? rec.getTotalDuration() : 0;
            videoCompleted = rec.getCompleteCount() != null && rec.getCompleteCount() > 0;
        } else {
            // 退化路径：以 task_completions 自身存储状态做反拖拽累计
            TaskCompletion existing = taskSupport.getOneCompletion(taskId, userId);
            int oldPos = existing != null && existing.getLastPosition() != null ? existing.getLastPosition() : 0;
            int oldWd = existing != null && existing.getWatchedDuration() != null ? existing.getWatchedDuration() : 0;
            LocalDateTime prevUpdate = existing != null
                    ? (existing.getUpdatedAt() != null ? existing.getUpdatedAt() : existing.getCreatedAt())
                    : null;
            long realElapsed = prevUpdate != null
                    ? Math.max(0, ChronoUnit.SECONDS.between(prevUpdate, LocalDateTime.now()))
                    : 0;
            int pos = position != null ? position : 0;
            int claimedWd = watchedDuration != null ? watchedDuration : 0;
            int posDelta = pos - oldPos;
            int claimedInc = claimedWd - oldWd;
            // 有效增量 = max(0, min(max(位置增量, 声称时长增量), 真实经过时长 + 容差))
            int validInc = (int) Math.max(0, Math.min(Math.max(posDelta, claimedInc), realElapsed + VIDEO_SEEK_TOLERANCE_SEC));
            validWatched = oldWd + validInc;
            lastPosition = pos;
            totalDur = totalDuration != null ? totalDuration : 0;
            videoCompleted = totalDur > 0 && validWatched >= totalDur * 0.9;
        }

        final int wd = validWatched;
        final int pos = lastPosition;
        final int td = totalDur;
        final boolean done = videoCompleted;
        taskSupport.upsertCompletion(userId, courseId, taskId, "VIDEO", x -> {
            x.setLastPosition(pos);
            x.setWatchedDuration(wd);
            x.setTotalDuration(td);
            if (td > 0 && wd >= td * 0.9) {
                x.setCompleted(true);
                x.setCompletedAt(LocalDateTime.now());
            } else if (done) {
                x.setCompleted(true);
                x.setCompletedAt(LocalDateTime.now());
            }
        });
        return getVideoState(userId, courseId, taskId);
    }

    /** 测验提交与即时判分（P1-3：答错客观题自动收录错题本） */
    @Transactional
    public Map<String, Object> submitQuiz(Long userId, Long courseId, Long taskId, Map<String, String> rawAnswers) {
        taskSupport.assertEnrolled(userId, courseId);
        Task task = taskService.getById(taskId);
        if (task == null) {
            throw new BusinessException("任务点不存在");
        }
        int total = 0, correct = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        // 原始表单解析收口：题目ID字符串键 -> Long；非法键跳过并告警
        Map<Long, String> answers = new LinkedHashMap<>();
        if (rawAnswers != null) {
            for (Map.Entry<String, String> e : rawAnswers.entrySet()) {
                try {
                    answers.put(Long.valueOf(e.getKey()), e.getValue());
                } catch (NumberFormatException ignore) {
                    log.warn("解析答案键失败: key={}", e.getKey(), ignore);
                }
            }
        }
        if (!answers.isEmpty()) {
            // 批量预取题目信息，消除 N+1
            Set<Long> qids = answers.keySet();
            Map<Long, Question> questionMap = qids.isEmpty() ? Collections.emptyMap()
                    : questionService.listByIds(qids).stream()
                    .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));
            for (Map.Entry<Long, String> e : answers.entrySet()) {
                Long qid = e.getKey();
                String ans = e.getValue();
                Question q = questionMap.get(qid);
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("questionId", qid);
                total++; // 每个提交的答案都计入总数（题目不存在也按答错处理）
                if (q != null) {
                    boolean ok = taskSupport.normalize(q.getCorrectAnswers()).equals(taskSupport.normalize(ans));
                    d.put("correct", ok);
                    d.put("correctAnswer", q.getCorrectAnswers());
                    d.put("explanation", q.getExplanation());
                    if (ok) {
                        correct++;
                    } else if (taskSupport.isObjectiveQuestion(q.getType())) {
                        // 修复 P1-3：练习/测验(quiz) 答错自动收录错题本，与考试(EXAM)对齐
                        try {
                            wrongQuestionService.recordWrongAnswer(userId, qid, courseId, ans, "QUIZ");
                        } catch (Exception ignore) {
                            log.warn("错题收录失败: questionId={}, userId={}", qid, userId, ignore);
                        }
                    }
                } else {
                    d.put("correct", false);
                    d.put("correctAnswer", null);
                    d.put("explanation", null);
                }
                details.add(d);
            }
        }
        BigDecimal score = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(Math.round(correct * 100.0 / total));
        taskSupport.upsertCompletion(userId, courseId, taskId, "QUIZ", x -> {
            x.setScore(score);
            x.setCompleted(true);
            x.setCompletedAt(LocalDateTime.now());
        });
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("taskId", taskId);
        r.put("total", total);
        r.put("correct", correct);
        r.put("score", score);
        r.put("details", details);
        return r;
    }
}
