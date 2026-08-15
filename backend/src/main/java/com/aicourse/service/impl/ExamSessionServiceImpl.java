package com.aicourse.service.impl;


import com.aicourse.constant.ExamSessionStatus;

import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.*;
import com.aicourse.service.impl.exam.ExamSessionGradingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;


/**
 * 考试会话服务实现：相关业务逻辑入口，提供增删改查与编排能力。
 */
@Slf4j
@Service
public class ExamSessionServiceImpl extends ServiceImpl<ExamSessionMapper, ExamSession> implements ExamSessionService {


    private final ExamPaperService examPaperService;
    private final ExamQuestionService examQuestionService;
    private final QuestionService questionService;
    private final WrongQuestionService wrongQuestionService;
    private final ExamSessionGradingService gradingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExamSessionServiceImpl(ExamPaperService examPaperService,
                                   ExamQuestionService examQuestionService,
                                   QuestionService questionService,
                                   WrongQuestionService wrongQuestionService,
                                   ExamSessionGradingService gradingService) {
        this.examPaperService = examPaperService;
        this.examQuestionService = examQuestionService;
        this.questionService = questionService;
        this.wrongQuestionService = wrongQuestionService;
        this.gradingService = gradingService;
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
                    .in(ExamSession::getStatus, ExamSessionStatus.STARTED, ExamSessionStatus.SUBMITTED, ExamSessionStatus.GRADED, ExamSessionStatus.TIMEOUT);
            long existingAttempts = this.count(countWrapper);
            if (existingAttempts >= maxAttempts) {
                throw new BusinessException("已达到最大尝试次数限制（" + maxAttempts + "次）");
            }
        }

        // 检查是否有正在进行的考试
        LambdaQueryWrapper<ExamSession> activeWrapper = new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, examPaperId)
                .eq(ExamSession::getStudentId, studentId)
                .eq(ExamSession::getStatus, ExamSessionStatus.STARTED);
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
        session.setStatus(ExamSessionStatus.STARTED);
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
        if (!ExamSessionStatus.STARTED.equals(session.getStatus())) {
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
        log.info("提交考试会话");

        ExamSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        // 防止时钟回调导致时间倒退
        LocalDateTime now = LocalDateTime.now();

        // 原子更新：仅当状态为 STARTED 时才允许提交（消除 TOCTOU 竞态）
        LambdaUpdateWrapper<ExamSession> updateWrapper = new LambdaUpdateWrapper<ExamSession>()
                .eq(ExamSession::getId, sessionId)
                .eq(ExamSession::getStatus, ExamSessionStatus.STARTED)
                .set(ExamSession::getAnswers, mapToJson(answers))
                .set(ExamSession::getTimeSpent, (int) ChronoUnit.SECONDS.between(session.getStartTime(), now))
                .set(ExamSession::getStatus, ExamSessionStatus.SUBMITTED)
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
        if (ExamSessionStatus.SUBMITTED.equals(session.getStatus()) || ExamSessionStatus.GRADED.equals(session.getStatus())) {
            throw new BusinessException("该试卷已经提交，无需强制交卷");
        }

        // 计算考试用时
        LocalDateTime now = LocalDateTime.now();
        long secondsSpent = ChronoUnit.SECONDS.between(session.getStartTime(), now);
        session.setTimeSpent((int) secondsSpent);

        session.setStatus(ExamSessionStatus.FORCE_SUBMITTED);
        session.setSubmitTime(now);
        this.updateById(session);

        return session;
    }


    /** 考试会话相关操作 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> autoGrade(Long sessionId) {
        // 阅卷逻辑统一收口到 ExamSessionGradingService（含 TIMEOUT 状态支持与批量取题），
        // 本门面仅做委托，避免双份实现漂移
        return gradingService.autoGrade(sessionId);
    }



    /** 考试会话相关操作 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualGrade(Long sessionId, Long questionId, BigDecimal score, String comment) {
        gradingService.manualGrade(sessionId, questionId, score, comment);
    }



    /** 考试会话相关操作 */
    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalScore(Long sessionId) {
        return gradingService.calculateTotalScore(sessionId);
    }


    /**
     * 生成成绩报告
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> generateScoreReport(Long sessionId) {
        log.info("生成考试会话");

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
                .eq(ExamSession::getStatus, ExamSessionStatus.STARTED)
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

        // 将作弊检测结果以 JSON 树形式写入 answers 字段的扩展区域（复用已有字段存储标记信息）。
        // 修复 P0-1：原实现用 String.replace("}", ...) 字符串级注入，会破坏答案 JSON 中
        // 所有嵌套对象与含 "}" 的文本，导致该场考试无法自动阅卷。现改为 Jackson 树操作：
        // 将现有 answers 解析为 JsonNode，向根对象 put("cheatingFlags", ...) 后重新序列化，
        // 对任何合法 JSON 均幂等安全。
        if (hasSuspicion) {
            ObjectNode flags = objectMapper.createObjectNode();
            flags.put("detectedAt", LocalDateTime.now().toString());
            flags.put("flagCount", suspicions.size());
            com.fasterxml.jackson.databind.node.ArrayNode details = flags.putArray("details");
            for (String s : suspicions) {
                details.add(s);
            }

            try {
                JsonNode root = objectMapper.readTree(session.getAnswers() != null ? session.getAnswers() : "{}");
                ObjectNode merged;
                if (root == null || root.isNull() || root.isMissingNode() || !root.isObject()) {
                    merged = objectMapper.createObjectNode();
                } else {
                    merged = (ObjectNode) root;
                }
                merged.set("cheatingFlags", flags);
                session.setAnswers(objectMapper.writeValueAsString(merged));
                this.updateById(session);
            } catch (JsonProcessingException e) {
                // 答案数据损坏（历史遗留非法 JSON）时兜底：以作弊标记独立 JSON 保存，
                // 不覆盖也无法修复原答案，但绝不能把损坏数据再次写回。
                log.warn("checkCheating 解析 answers 失败，作弊标记将以独立 JSON 记录: sessionId={}", sessionId, e);
                session.setAnswers("{\"cheatingFlags\":" + flags.toString() + "}");
                this.updateById(session);
            }
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
        if (!ExamSessionStatus.STARTED.equals(session.getStatus())) {
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
     * 将Map转换为JSON字符串。
     * 修复 P1-13：原手拼 StringBuilder 不转义引号/反斜杠/换行，答案含特殊字符即生成非法 JSON。
     * 现委托 Jackson 序列化，任何值均可正确转义。
     */
    private String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("查询考试会话失败: {}", e.getMessage(), e);

            throw new BusinessException("答案序列化失败");
        }
    }

    /**
     * 解析题目得分JSON字符串为Map（Jackson 严格解析，替代原手写字符串分割）
     */
    private Map<Long, BigDecimal> parseScoresJson(String json) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (json == null || json.isEmpty() || "{}".equals(json.trim())) {
            return result;
        }
        try {
            Map<Long, BigDecimal> parsed = objectMapper.readValue(json, new TypeReference<Map<Long, BigDecimal>>() {});
            if (parsed != null) {
                result.putAll(parsed);
            }
        } catch (JsonProcessingException e) {
            log.error("查询考试会话失败: {}", e.getMessage(), e);

            // 历史非法数据容错：返回空集合，不向上抛导致成绩查询失败
        }
        return result;
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
            log.error("查询考试会话失败: {}", e.getMessage(), e);

            // 错题记录失败不影响主流程
        }
    }


    /** 查询考试会话 */
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


    /** 考试会话相关操作 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void timeoutSubmit(Long sessionId) {
        ExamSession session = this.getById(sessionId);
        if (session == null || !ExamSessionStatus.STARTED.equals(session.getStatus())) return;

        // 修复 P0-2：原实现调用 submitExam(emptyMap) 会把学生已保存的 answers 整体覆盖为 "{}"，
        // 超时收卷即清空答案、阅卷按空卷给 0 分。超时收卷只应流转状态与提交时间，
        // 保留学生考试过程中 autoSaveAnswers 已持久化的作答内容。
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ExamSession> updateWrapper = new LambdaUpdateWrapper<ExamSession>()
                .eq(ExamSession::getId, sessionId)
                .eq(ExamSession::getStatus, ExamSessionStatus.STARTED)
                .set(ExamSession::getStatus, ExamSessionStatus.AUTO_SUBMITTED)
                .set(ExamSession::getSubmitTime, now)
                .set(ExamSession::getTimeSpent, (int) ChronoUnit.SECONDS.between(session.getStartTime(), now));
        this.update(updateWrapper);
    }
}
