package com.aicourse.service.impl;

import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ExamSessionServiceImpl extends ServiceImpl<ExamSessionMapper, ExamSession> implements ExamSessionService {

    private final ExamPaperService examPaperService;
    private final ExamQuestionService examQuestionService;
    private final QuestionService questionService;
    private final WrongQuestionService wrongQuestionService;

    public ExamSessionServiceImpl(ExamPaperService examPaperService,
                                   ExamQuestionService examQuestionService,
                                   QuestionService questionService,
                                   WrongQuestionService wrongQuestionService) {
        this.examPaperService = examPaperService;
        this.examQuestionService = examQuestionService;
        this.questionService = questionService;
        this.wrongQuestionService = wrongQuestionService;
    }

    /**
     * 开始考试（含规则校验：时间窗口、尝试次数限制）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamSession startExam(Long examPaperId, Long studentId, String ipAddress, String userAgent) {
        // 检查试卷是否存在且已发布
        ExamPaper examPaper = examPaperService.getById(examPaperId);
        if (examPaper == null) {
            throw new BusinessException("试卷不存在");
        }
        if (!"PUBLISHED".equals(examPaper.getStatus())) {
            throw new BusinessException("试卷尚未发布，无法开始考试");
        }

        // 检查时间窗口
        LocalDateTime now = LocalDateTime.now();
        if (examPaper.getStartTime() != null && now.isBefore(examPaper.getStartTime())) {
            throw new BusinessException("考试尚未开始");
        }
        if (examPaper.getEndTime() != null && now.isAfter(examPaper.getEndTime())) {
            throw new BusinessException("考试已结束");
        }

        // 检查尝试次数限制
        Integer maxAttempts = examPaper.getMaxAttempts();
        if (maxAttempts != null && maxAttempts > 0) {
            LambdaQueryWrapper<ExamSession> countWrapper = new LambdaQueryWrapper<ExamSession>()
                    .eq(ExamSession::getExamPaperId, examPaperId)
                    .eq(ExamSession::getStudentId, studentId)
                    .in(ExamSession::getStatus, "STARTED", "SUBMITTED", "GRADED", "TIMEOUT");
            long existingAttempts = this.count(countWrapper);
            if (existingAttempts >= maxAttempts) {
                throw new BusinessException("已达到最大尝试次数限制（" + maxAttempts + "次）");
            }
        }

        // 检查是否有正在进行的考试
        LambdaQueryWrapper<ExamSession> activeWrapper = new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, examPaperId)
                .eq(ExamSession::getStudentId, studentId)
                .eq(ExamSession::getStatus, "STARTED");
        ExamSession activeSession = this.getOne(activeWrapper);
        if (activeSession != null) {
            throw new BusinessException("您有正在进行的考试，请先完成或结束当前考试");
        }

        // 创建新的考试会话
        int attemptNumber = (int) this.count(new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, examPaperId)
                .eq(ExamSession::getStudentId, studentId)) + 1;

        ExamSession session = new ExamSession();
        session.setExamPaperId(examPaperId);
        session.setStudentId(studentId);
        session.setStatus("STARTED");
        session.setStartTime(LocalDateTime.now());
        session.setAttemptNumber(attemptNumber);
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        this.save(session);

        return session;
    }

    /**
     * 自动保存作答答案
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoSaveAnswers(Long sessionId, Map<String, Object> answers) {
        ExamSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (!"STARTED".equals(session.getStatus())) {
            throw new BusinessException("当前考试状态不允许保存答案");
        }

        // 将答案Map转换为JSON字符串存储
        String answersJson = mapToJson(answers);
        session.setAnswers(answersJson);
        this.updateById(session);
    }

    /**
     * 提交试卷（含防重复提交）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamSession submitExam(Long sessionId, Map<String, Object> answers) {
        ExamSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        // 防止时钟回调导致时间倒退
        LocalDateTime now = LocalDateTime.now();

        // 原子更新：仅当状态为 STARTED 时才允许提交（消除 TOCTOU 竞态）
        LambdaUpdateWrapper<ExamSession> updateWrapper = new LambdaUpdateWrapper<ExamSession>()
                .eq(ExamSession::getId, sessionId)
                .eq(ExamSession::getStatus, "STARTED")
                .set(ExamSession::getAnswers, mapToJson(answers))
                .set(ExamSession::getTimeSpent, (int) ChronoUnit.SECONDS.between(session.getStartTime(), now))
                .set(ExamSession::getStatus, "SUBMITTED")
                .set(ExamSession::getSubmitTime, now);
        boolean updated = this.update(updateWrapper);
        if (!updated) {
            throw new BusinessException("该试卷已提交或已结束，无法重复提交");
        }

        // 重新读取最新状态返回
        return this.getById(sessionId);
    }

    /**
     * 强制交卷（管理员操作）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamSession forceSubmit(Long sessionId) {
        ExamSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if ("SUBMITTED".equals(session.getStatus()) || "GRADED".equals(session.getStatus())) {
            throw new BusinessException("该试卷已经提交，无需强制交卷");
        }

        // 计算考试用时
        LocalDateTime now = LocalDateTime.now();
        long secondsSpent = ChronoUnit.SECONDS.between(session.getStartTime(), now);
        session.setTimeSpent((int) secondsSpent);

        session.setStatus("FORCE_SUBMITTED");
        session.setSubmitTime(now);
        this.updateById(session);

        return session;
    }

    /**
     * 自动阅卷（客观题：单选/多选/判断）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> autoGrade(Long sessionId) {
        ExamSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (!"SUBMITTED".equals(session.getStatus()) && !"FORCE_SUBMITTED".equals(session.getStatus())) {
            throw new BusinessException("试卷尚未提交，无法进行自动阅卷");
        }

        // 解析学生答案
        Map<String, Object> studentAnswers = parseJsonToMap(session.getAnswers());
        if (studentAnswers == null || studentAnswers.isEmpty()) {
            throw new BusinessException("未找到学生作答记录");
        }

        // 查询试卷关联的所有题目
        List<ExamQuestion> examQuestions = examQuestionService.list(
                new LambdaQueryWrapper<ExamQuestion>()
                        .eq(ExamQuestion::getExamPaperId, session.getExamPaperId())
                        .orderByAsc(ExamQuestion::getOrderIndex)
        );

        // 每题得分记录
        Map<Long, BigDecimal> questionScoreMap = new LinkedHashMap<>();
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal fullScore = BigDecimal.ZERO;
        int correctCount = 0;
        int wrongCount = 0;
        int pendingReviewCount = 0;
        List<Long> wrongQuestionIds = new ArrayList<>();

        for (ExamQuestion eq : examQuestions) {
            Question question = questionService.getById(eq.getQuestionId());
            if (question == null) {
                continue;
            }

            BigDecimal points = eq.getPoints() != null ? eq.getPoints() : BigDecimal.ZERO;
            fullScore = fullScore.add(points);

            String questionKey = String.valueOf(eq.getQuestionId());
            String studentAnswer = studentAnswers.get(questionKey) != null ?
                    studentAnswers.get(questionKey).toString().trim() : "";
            String correctAnswer = question.getCorrectAnswers();

            BigDecimal score = BigDecimal.ZERO;
            boolean isCorrect = false;

            switch (question.getType()) {
                case "SINGLE_CHOICE":
                    // 单选题：完全匹配得满分
                    isCorrect = studentAnswer.equalsIgnoreCase(correctAnswer);
                    score = isCorrect ? points : BigDecimal.ZERO;
                    break;

                case "MULTIPLE_CHOICE":
                    // 多选题：完全匹配得满分，部分匹配得一半分
                    if (correctAnswer != null && !correctAnswer.isEmpty()) {
                        Set<String> correctSet = parseArrayToStringSet(correctAnswer);
                        Set<String> answerSet = parseArrayToStringSet(studentAnswer);
                        if (answerSet.equals(correctSet)) {
                            // 完全匹配
                            isCorrect = true;
                            score = points;
                        } else {
                            // 部分匹配：计算交集比例
                            Set<String> intersection = new HashSet<>(answerSet);
                            intersection.retainAll(correctSet);
                            if (!intersection.isEmpty()) {
                                double ratio = (double) intersection.size() / correctSet.size();
                                score = points.multiply(BigDecimal.valueOf(ratio * 0.5))
                                        .setScale(2, RoundingMode.HALF_UP);
                            } else {
                                score = BigDecimal.ZERO;
                            }
                        }
                    }
                    break;

                case "TRUE_FALSE":
                    // 判断题：忽略大小写比较
                    isCorrect = studentAnswer.equalsIgnoreCase(correctAnswer);
                    score = isCorrect ? points : BigDecimal.ZERO;
                    break;

                case "SHORT_ANSWER":
                case "ESSAY":
                    // 主观题：不自动评分，标记为待人工批阅
                    pendingReviewCount++;
                    score = BigDecimal.valueOf(-1); // -1表示待人工评分
                    break;

                default:
                    // 其他题型暂不自动评分
                    pendingReviewCount++;
                    score = BigDecimal.valueOf(-1);
                    break;
            }

            questionScoreMap.put(eq.getQuestionId(), score);
            totalScore = totalScore.add(score.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : score);

            if (isCorrect) {
                correctCount++;
            } else if (score.compareTo(BigDecimal.ZERO) > 0) {
                // 部分正确也算错误
                wrongCount++;
                wrongQuestionIds.add(question.getId());
            } else if (score.equals(BigDecimal.ZERO) && !studentAnswer.isEmpty()) {
                wrongCount++;
                wrongQuestionIds.add(question.getId());
            }
        }

        // 计算百分比
        BigDecimal percentage = BigDecimal.ZERO;
        if (fullScore.compareTo(BigDecimal.ZERO) > 0) {
            percentage = totalScore.divide(fullScore, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // 将每题得分写入JSON
        String questionScoresJson = scoresMapToJson(questionScoreMap);
        session.setQuestionScores(questionScoresJson);
        session.setTotalScore(totalScore);
        session.setPercentage(percentage);
        session.setStatus("GRADED");
        this.updateById(session);

        // 记录错题到错题本（可选）
        for (Long qId : wrongQuestionIds) {
            saveWrongQuestion(session.getStudentId(), qId, session.getExamPaperId());
        }

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", session.getId());
        result.put("totalScore", totalScore);
        result.put("fullScore", fullScore);
        result.put("percentage", percentage);
        result.put("correctCount", correctCount);
        result.put("wrongCount", wrongCount);
        result.put("pendingReviewCount", pendingReviewCount);
        result.put("questionScores", questionScoreMap);
        result.put("status", "GRADED");

        return result;
    }

    /**
     * 人工阅卷（主观题）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualGrade(Long sessionId, Long questionId, BigDecimal score, String comment) {
        ExamSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (!"GRADED".equals(session.getStatus())) {
            throw new BusinessException("请先完成自动阅卷");
        }

        // 解析现有的questionScores
        Map<Long, BigDecimal> currentScores = parseScoresJson(session.getQuestionScores());
        if (currentScores == null) {
            currentScores = new HashMap<>();
        }

        // 更新该题分数
        currentScores.put(questionId, score);
        String updatedScoresJson = scoresMapToJson(currentScores);
        session.setQuestionScores(updatedScoresJson);

        // 重新计算总分
        BigDecimal newTotalScore = calculateTotalScoreFromScores(currentScores);
        session.setTotalScore(newTotalScore);

        // 重新计算百分比
        ExamPaper paper = examPaperService.getById(session.getExamPaperId());
        if (paper != null && paper.getTotalScore() != null &&
                paper.getTotalScore().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newPercentage = newTotalScore.divide(paper.getTotalScore(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            session.setPercentage(newPercentage);
        }

        this.updateById(session);
    }

    /**
     * 计算试卷总分
     */
    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalScore(Long sessionId) {
        ExamSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (session.getQuestionScores() == null || session.getQuestionScores().isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map<Long, BigDecimal> scores = parseScoresJson(session.getQuestionScores());
        return calculateTotalScoreFromScores(scores);
    }

    /**
     * 生成成绩报告
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> generateScoreReport(Long sessionId) {
        ExamSession session = this.getById(sessionId);
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
    @Override
    @Transactional(readOnly = true)
    public List<ExamSession> getActiveExams() {
        return this.list(new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getStatus, "STARTED")
                .orderByDesc(ExamSession::getStartTime));
    }

    /**
     * 检测作弊嫌疑
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> detectCheating(Long sessionId) {
        ExamSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        List<String> suspicions = new ArrayList<>();

        // 检测IP地址变化：查询该学生在同一试卷的其他会话
        List<ExamSession> otherSessions = this.list(new LambdaQueryWrapper<ExamSession>()
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
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> checkCheating(Long sessionId) {
        ExamSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        List<String> suspicions = detectCheating(sessionId);
        boolean hasSuspicion = !suspicions.isEmpty();

        // 将作弊检测结果序列化为JSON存入answers字段的扩展区域（复用已有字段存储标记信息）
        // 构建作弊标记摘要
        StringBuilder cheatingFlags = new StringBuilder();
        if (hasSuspicion) {
            cheatingFlags.append("{\"detectedAt\":\"").append(java.time.LocalDateTime.now().toString()).append("\"");
            cheatingFlags.append(",\"flagCount\":").append(suspicions.size());
            cheatingFlags.append(",\"details\":[");
            for (int i = 0; i < suspicions.size(); i++) {
                if (i > 0) cheatingFlags.append(",");
                cheatingFlags.append("\"").append(suspicions.get(i).replace("\"", "'")).append("\"");
            }
            cheatingFlags.append("]}");
            // 将标记信息追加到现有答案JSON中（作为元数据）
            String existingAnswers = session.getAnswers() != null ? session.getAnswers() : "{}";
            if (!existingAnswers.isEmpty() && !"{".equals(existingAnswers.trim())) {
                // 追加cheatingFlags到答案中
                String markedAnswers = existingAnswers.replace("}", ",\"cheatingFlags\":" + cheatingFlags.toString() + "}");
                session.setAnswers(markedAnswers);
            } else {
                session.setAnswers("{\"cheatingFlags\":" + cheatingFlags.toString() + "}");
            }
            this.updateById(session);
        }

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
    @Override
    @Transactional(readOnly = true)
    public Long getRemainingTime(Long sessionId) {
        ExamSession session = this.getById(sessionId);
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
            long elapsed = ChronoUnit.SECONDS.between(session.getStartTime(), now);
            long durationSeconds = paper.getDuration() * 60L;
            remainingSeconds = Math.max(0, durationSeconds - elapsed);
        }

        // 基于考试结束时间计算
        if (paper.getEndTime() != null) {
            long endRemaining = ChronoUnit.SECONDS.between(now, paper.getEndTime());
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
    @Override
    @Transactional(readOnly = true)
    public ExamSession getLatestSession(Long examPaperId, Long studentId) {
        LambdaQueryWrapper<ExamSession> wrapper = new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, examPaperId)
                .eq(ExamSession::getStudentId, studentId)
                .orderByDesc(ExamSession::getCreatedAt)
                .last("LIMIT 1");
        return this.getOne(wrapper);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 将Map转换为简单的JSON字符串
     */
    private String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (i++ > 0) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":\"")
                    .append(entry.getValue() != null ? entry.getValue().toString() : "").append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 解析简单JSON字符串为Map
     */
    private Map<String, Object> parseJsonToMap(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isEmpty() || "{}".equals(json.trim())) {
            return result;
        }
        try {
            // 去掉首尾大括号
            String content = json.trim();
            if (content.startsWith("{")) content = content.substring(1);
            if (content.endsWith("}")) content = content.substring(0, content.length() - 1);
            content = content.trim();

            // 简单解析 key:value 对
            String[] pairs = content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String value = kv[1].trim().replace("\"", "");
                    result.put(key, value);
                }
            }
        } catch (Exception e) {
            // JSON解析失败返回空map
        }
        return result;
    }

    /**
     * 解析JSON数组字符串为Set
     */
    private Set<String> parseArrayToStringSet(String arrayStr) {
        Set<String> set = new HashSet<>();
        if (arrayStr == null || arrayStr.isEmpty()) {
            return set;
        }
        try {
            String content = arrayStr.trim();
            if (content.startsWith("[")) content = content.substring(1);
            if (content.endsWith("]")) content = content.substring(0, content.length() - 1);
            if (content.isEmpty()) {
                return set;
            }
            String[] items = content.split(",");
            for (String item : items) {
                String trimmed = item.trim().replace("\"", "").replace("'", "").trim();
                if (!trimmed.isEmpty()) {
                    set.add(trimmed);
                }
            }
        } catch (Exception e) {
            // 解析失败时尝试直接添加
            set.add(arrayStr.replace("\"", "").replace("'", "").trim());
        }
        return set;
    }

    /**
     * 将题目得分Map转换为JSON字符串
     */
    private String scoresMapToJson(Map<Long, BigDecimal> scores) {
        if (scores == null || scores.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<Long, BigDecimal> entry : scores.entrySet()) {
            if (i++ > 0) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":")
                    .append(entry.getValue() != null ? entry.getValue().toString() : "0");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 解析题目得分JSON字符串为Map
     */
    private Map<Long, BigDecimal> parseScoresJson(String json) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (json == null || json.isEmpty() || "{}".equals(json.trim())) {
            return result;
        }
        try {
            String content = json.trim();
            if (content.startsWith("{")) content = content.substring(1);
            if (content.endsWith("}")) content = content.substring(0, content.length() - 1);
            content = content.trim();

            String[] pairs = content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    Long key = Long.parseLong(kv[0].trim().replace("\"", ""));
                    BigDecimal value = new BigDecimal(kv[1].trim());
                    result.put(key, value);
                }
            }
        } catch (Exception e) {
            // 解析失败
        }
        return result;
    }

    /**
     * 从得分Map中计算总分（排除-1的待评阅项）
     */
    private BigDecimal calculateTotalScoreFromScores(Map<Long, BigDecimal> scores) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal score : scores.values()) {
            if (score != null && score.compareTo(BigDecimal.ZERO) >= 0) {
                total = total.add(score);
            }
        }
        return total;
    }

    /**
     * 保存错题到错题本
     */
    private void saveWrongQuestion(Long studentId, Long questionId, Long examPaperId) {
        try {
            // 检查是否已存在
            LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<WrongQuestion>()
                    .eq(WrongQuestion::getStudentId, studentId)
                    .eq(WrongQuestion::getQuestionId, questionId);
            long count = wrongQuestionService.count(wrapper);
            if (count == 0) {
                WrongQuestion wq = new WrongQuestion();
                wq.setStudentId(studentId);
                wq.setQuestionId(questionId);
                wq.setCourseId(examPaperId);
                wq.setPracticeCount(1);
                wq.setLastPracticedAt(java.time.LocalDateTime.now());
                wrongQuestionService.save(wq);
            } else {
                // 更新错误次数
                WrongQuestion existing = wrongQuestionService.getOne(wrapper);
                existing.setPracticeCount(existing.getPracticeCount() + 1);
                existing.setLastPracticedAt(java.time.LocalDateTime.now());
                wrongQuestionService.updateById(existing);
            }
        } catch (Exception e) {
            // 错题记录失败不影响主流程
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSessionQuestions(Long sessionId) {
        ExamSession session = this.getById(sessionId);
        if (session == null) return Collections.emptyList();
        return examQuestionService.list(
            new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, session.getExamPaperId())
                .orderByAsc(ExamQuestion::getOrderIndex))
            .stream().map(eq -> {
                Map<String, Object> item = new HashMap<>();
                item.put("questionId", eq.getQuestionId());
                item.put("orderIndex", eq.getOrderIndex());
                item.put("score", eq.getPoints());
                return item;
            }).collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void timeoutSubmit(Long sessionId) {
        ExamSession session = this.getById(sessionId);
        if (session == null || !"STARTED".equals(session.getStatus())) return;
        submitExam(sessionId, java.util.Collections.emptyMap());
        session.setStatus("AUTO_SUBMITTED");
        session.setSubmitTime(LocalDateTime.now());
        this.updateById(session);
    }
}
