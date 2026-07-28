package com.aicourse.service.impl.exam;

import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.QuestionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 考试会话查询 / 统计 / 防作弊协作者（委托模式）。
 * 负责：成绩报告 / 进行中列表 / 作弊检测与标记 / 剩余时间 / 最新会话 / 题目列表（含乱序）。
 * 由 {@code ExamSessionServiceImpl} 门面委托调用，本类只持有自己需要的依赖。
 */
@Component
public class ExamSessionQueryService {

    private final ExamPaperService examPaperService;
    private final ExamQuestionService examQuestionService;
    private final QuestionService questionService;
    private final ObjectMapper objectMapper;
    private final ExamSessionMapper examSessionMapper;

    public ExamSessionQueryService(ExamPaperService examPaperService,
                                  ExamQuestionService examQuestionService,
                                  QuestionService questionService,
                                  ObjectMapper objectMapper,
                                  ExamSessionMapper examSessionMapper) {
        this.examPaperService = examPaperService;
        this.examQuestionService = examQuestionService;
        this.questionService = questionService;
        this.objectMapper = objectMapper;
        this.examSessionMapper = examSessionMapper;
    }

    /**
     * 生成成绩报告
     */
    public Map<String, Object> generateScoreReport(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        ExamPaper examPaper = examPaperService.getById(session.getExamPaperId());

        Map<String, Object> report = new HashMap<>();
        report.put("sessionId", session.getId());
        report.put("examPaperId", session.getExamPaperId());
        report.put("examPaperTitle", examPaper != null ? examPaper.getTitle() : "未知试卷");
        report.put("studentId", session.getStudentId());
        report.put("status", session.getStatus());
        report.put("startTime", session.getStartTime());
        report.put("submitTime", session.getSubmitTime());
        report.put("timeSpent", session.getTimeSpent());
        report.put("attemptNumber", session.getAttemptNumber());
        report.put("totalScore", session.getTotalScore());
        report.put("percentage", session.getPercentage());

        // 解析每题得分详情
        if (session.getQuestionScores() != null) {
            Map<Long, BigDecimal> scores = parseScoresJson(session.getQuestionScores());
            report.put("questionDetails", scores);
        }

        // 等级评定
        if (session.getPercentage() != null) {
            double pct = session.getPercentage().doubleValue();
            String gradeLevel;
            if (pct >= 90) {
                gradeLevel = "优秀";
            } else if (pct >= 80) {
                gradeLevel = "良好";
            } else if (pct >= 70) {
                gradeLevel = "中等";
            } else if (pct >= 60) {
                gradeLevel = "及格";
            } else {
                gradeLevel = "不及格";
            }
            report.put("gradeLevel", gradeLevel);
        }

        return report;
    }

    /**
     * 获取正在进行的考试列表
     */
    public List<ExamSession> getActiveExams() {
        return examSessionMapper.selectList(new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getStatus, "STARTED")
                .orderByDesc(ExamSession::getStartTime));
    }

    /**
     * 检测作弊嫌疑
     */
    public List<String> detectCheating(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        List<String> suspicions = new ArrayList<>();

        // 检测IP地址变化：查询该学生在同一试卷的其他会话
        List<ExamSession> otherSessions = examSessionMapper.selectList(new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, session.getExamPaperId())
                .eq(ExamSession::getStudentId, session.getStudentId())
                .ne(ExamSession::getId, sessionId));

        for (ExamSession other : otherSessions) {
            if (other.getIpAddress() != null && session.getIpAddress() != null &&
                    !other.getIpAddress().equals(session.getIpAddress())) {
                suspicions.add("检测到IP地址变化：" + other.getIpAddress() + " -> " + session.getIpAddress());
            }
            if (other.getUserAgent() != null && session.getUserAgent() != null &&
                    !other.getUserAgent().equals(session.getUserAgent())) {
                suspicions.add("检测到浏览器/设备变化");
            }
        }

        // 检测异常短的答题时间
        if (session.getTimeSpent() != null && session.getTimeSpent() < 60) {
            suspicions.add("答题时间过短（少于1分钟），可能存在异常");
        }

        // 检测是否超时后提交
        if (session.getSubmitTime() != null) {
            ExamPaper paper = examPaperService.getById(session.getExamPaperId());
            if (paper != null && paper.getEndTime() != null &&
                    session.getSubmitTime().isAfter(paper.getEndTime())) {
                suspicions.add("在考试结束后提交");
            }
        }

        return suspicions;
    }

    /**
     * 作弊检测与标记（综合检测IP变化、异常行为等，记录检测结果）
     * 检测项包括：IP地址变化、浏览器/设备变化、异常短时答题、超时提交等
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> checkCheating(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        List<String> suspicions = detectCheating(sessionId);
        boolean hasSuspicion = !suspicions.isEmpty();

        // 修复 P0-3：作弊标记写入独立字段 cheatingFlags，绝不直接改写作答答案(answers)，
        // 避免字符串替换破坏答案 JSON（主观题文本含 '}' 时旧逻辑会损坏结构）。
        Map<String, Object> flagMap = new LinkedHashMap<>();
        flagMap.put("detectedAt", java.time.LocalDateTime.now().toString());
        flagMap.put("flagCount", suspicions.size());
        flagMap.put("riskLevel", determineRiskLevel(suspicions));
        flagMap.put("details", suspicions);
        try {
            session.setCheatingFlags(objectMapper.writeValueAsString(flagMap));
        } catch (Exception e) {
            session.setCheatingFlags(null);
        }
        examSessionMapper.updateById(session);

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("hasSuspicion", hasSuspicion);
        result.put("suspicionCount", suspicions.size());
        result.put("suspicions", suspicions);
        result.put("riskLevel", determineRiskLevel(suspicions));
        result.put("checkedAt", java.time.LocalDateTime.now());

        return result;
    }

    /**
     * 根据可疑项数量判定风险等级
     */
    private String determineRiskLevel(List<String> suspicions) {
        if (suspicions.size() >= 3) return "HIGH";
        if (suspicions.size() >= 1) return "MEDIUM";
        return "LOW";
    }

    /**
     * 获取考试剩余时间(秒)
     */
    public Long getRemainingTime(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (!"STARTED".equals(session.getStatus())) {
            return 0L;
        }

        ExamPaper paper = examPaperService.getById(session.getExamPaperId());
        if (paper == null) {
            return 0L;
        }

        LocalDateTime now = LocalDateTime.now();
        Long remainingSeconds = null;

        // 基于考试时长计算
        if (paper.getDuration() != null && paper.getDuration() > 0) {
            long elapsed = java.time.temporal.ChronoUnit.SECONDS.between(session.getStartTime(), now);
            long durationSeconds = paper.getDuration() * 60L;
            remainingSeconds = Math.max(0, durationSeconds - elapsed);
        }

        // 基于考试结束时间计算
        if (paper.getEndTime() != null) {
            long endRemaining = java.time.temporal.ChronoUnit.SECONDS.between(now, paper.getEndTime());
            if (remainingSeconds == null) {
                remainingSeconds = Math.max(0, endRemaining);
            } else {
                remainingSeconds = Math.min(remainingSeconds, Math.max(0, endRemaining));
            }
        }

        return remainingSeconds != null ? remainingSeconds : 0L;
    }

    /**
     * 获取学生某试卷的最新会话
     */
    public ExamSession getLatestSession(Long examPaperId, Long studentId) {
        LambdaQueryWrapper<ExamSession> wrapper = new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, examPaperId)
                .eq(ExamSession::getStudentId, studentId)
                .orderByDesc(ExamSession::getCreatedAt)
                .last("LIMIT 1");
        return examSessionMapper.selectOne(wrapper);
    }

    /**
     * 获取本场考试（按会话维度）的题目列表，已应用该会话独立的题目乱序与选项乱序。
     * 选项在原数组基础上按 optionOrders 重排，并以 A/B/C/D 标签重新呈现（标签仅展示用，
     * 学生作答后由 normalizeAnswers 还原为原题标签再比对）。
     */
    public List<Map<String, Object>> getSessionQuestions(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        // 题目顺序：优先使用会话独立乱序，缺省回退到试卷原始顺序
        List<Long> questionIds;
        if (session.getQuestionOrder() != null && !session.getQuestionOrder().isEmpty()) {
            questionIds = parseLongList(session.getQuestionOrder());
        } else {
            List<ExamQuestion> eqs = examQuestionService.list(
                    new LambdaQueryWrapper<ExamQuestion>()
                            .eq(ExamQuestion::getExamPaperId, session.getExamPaperId())
                            .orderByAsc(ExamQuestion::getOrderIndex));
            questionIds = new ArrayList<>();
            if (eqs != null) for (ExamQuestion eq : eqs) questionIds.add(eq.getQuestionId());
        }
        if (questionIds.isEmpty()) return new ArrayList<>();

        Map<Long, int[]> optMap = parseOptionOrders(session.getOptionOrders());

        Map<Long, Question> qMap = new HashMap<>();
        List<Question> questions = questionService.listByIds(questionIds);
        if (questions != null) for (Question q : questions) qMap.put(q.getId(), q);

        Map<Long, BigDecimal> pointsMap = new HashMap<>();
        List<ExamQuestion> eqs = examQuestionService.list(
                new LambdaQueryWrapper<ExamQuestion>()
                        .eq(ExamQuestion::getExamPaperId, session.getExamPaperId()));
        if (eqs != null) for (ExamQuestion eq : eqs) pointsMap.put(eq.getQuestionId(), eq.getPoints());

        List<Map<String, Object>> result = new ArrayList<>();
        int displayIndex = 0;
        for (Long qid : questionIds) {
            Question q = qMap.get(qid);
            if (q == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", qid);                 // 兼容前端以 q.id 作为 questionId
            item.put("questionId", qid);
            item.put("type", q.getType());
            item.put("content", q.getContent());
            item.put("score", pointsMap.get(qid) != null ? pointsMap.get(qid) : q.getScore());
            item.put("points", pointsMap.get(qid));
            item.put("displayIndex", displayIndex);

            int[] perm = optMap.get(qid);
            if (perm != null && isChoiceType(q.getType())) {
                item.put("options", reorderOptions(q.getOptions(), perm));
            } else {
                item.put("options", q.getOptions());
            }
            result.add(item);
            displayIndex++;
        }
        return result;
    }

    // ==================== 私有辅助方法 ====================

    private List<Long> parseLongList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Map<Long, int[]> parseOptionOrders(String json) {
        Map<Long, int[]> map = new LinkedHashMap<>();
        if (json == null || json.isEmpty()) return map;
        try {
            Map<Long, List<Integer>> raw = objectMapper.readValue(json,
                    new TypeReference<Map<Long, List<Integer>>>() {});
            for (Map.Entry<Long, List<Integer>> e : raw.entrySet()) {
                int[] arr = new int[e.getValue().size()];
                for (int i = 0; i < arr.length; i++) arr[i] = e.getValue().get(i);
                map.put(e.getKey(), arr);
            }
        } catch (Exception e) {
            // 解析失败则忽略乱序映射
        }
        return map;
    }

    private String reorderOptions(String optionsJson, int[] perm) {
        if (optionsJson == null || optionsJson.isEmpty()) return optionsJson;
        try {
            List<Object> original = objectMapper.readValue(optionsJson, new TypeReference<List<Object>>() {});
            if (original == null || original.isEmpty()) return optionsJson;
            List<Object> reordered = new ArrayList<>(original.size());
            for (int i = 0; i < perm.length; i++) {
                int src = perm[i];
                reordered.add(src >= 0 && src < original.size() ? original.get(src) : "");
            }
            return objectMapper.writeValueAsString(reordered);
        } catch (Exception e) {
            return optionsJson;
        }
    }

    private boolean isChoiceType(String type) {
        if (type == null) return false;
        String t = type.trim().toUpperCase();
        return "SINGLE".equals(t) || "SINGLE_CHOICE".equals(t)
                || "MULTIPLE".equals(t) || "MULTIPLE_CHOICE".equals(t);
    }

    private Map<Long, BigDecimal> parseScoresJson(String json) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (json == null || json.isEmpty() || "{}".equals(json.trim())) {
            return result;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<Long, BigDecimal>>() {});
        } catch (Exception e) {
            return result;
        }
    }
}
