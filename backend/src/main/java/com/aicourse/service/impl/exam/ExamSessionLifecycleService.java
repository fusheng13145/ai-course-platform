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

import java.util.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 考试会话生命周期协作者（委托模式）。
 * 负责：开始考试 / 自动保存 / 提交 / 强制交卷 / 超时自动收卷。
 * 由 {@code ExamSessionServiceImpl} 门面委托调用，本类只持有自己需要的依赖。
 */
@Component
public class ExamSessionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ExamSessionLifecycleService.class);

    private final ExamPaperService examPaperService;
    private final ExamQuestionService examQuestionService;
    private final QuestionService questionService;
    private final ObjectMapper objectMapper;
    private final ExamSessionMapper examSessionMapper;
    private final ExamSessionGradingService gradingService;

    /** 乱序随机源（每次开始考试时生成独立的题目/选项顺序） */
    private final Random random = new Random();

    public ExamSessionLifecycleService(ExamPaperService examPaperService,
                                      ExamQuestionService examQuestionService,
                                      QuestionService questionService,
                                      ObjectMapper objectMapper,
                                      ExamSessionMapper examSessionMapper,
                                      ExamSessionGradingService gradingService) {
        this.examPaperService = examPaperService;
        this.examQuestionService = examQuestionService;
        this.questionService = questionService;
        this.objectMapper = objectMapper;
        this.examSessionMapper = examSessionMapper;
        this.gradingService = gradingService;
    }

    /**
     * 开始考试（含规则校验：时间窗口、尝试次数限制）
     */
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
            long existingAttempts = examSessionMapper.selectCount(countWrapper);
            if (existingAttempts >= maxAttempts) {
                throw new BusinessException("已达到最大尝试次数限制（" + maxAttempts + "次）");
            }
        }

        // 检查是否有正在进行的考试
        LambdaQueryWrapper<ExamSession> activeWrapper = new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, examPaperId)
                .eq(ExamSession::getStudentId, studentId)
                .eq(ExamSession::getStatus, "STARTED");
        ExamSession activeSession = examSessionMapper.selectOne(activeWrapper);
        if (activeSession != null) {
            throw new BusinessException("您有正在进行的考试，请先完成或结束当前考试");
        }

        // 创建新的考试会话
        int attemptNumber = (int) (long) examSessionMapper.selectCount(new LambdaQueryWrapper<ExamSession>()
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
        examSessionMapper.insert(session);

        // ===== P1-6 防作弊：为每场考试生成独立的题目乱序 + 选项乱序 =====
        try {
            generateShuffle(session);
        } catch (Exception e) {
            log.warn("乱序生成失败，降级为不乱序: sessionId={}", session.getId(), e);
        }

        return session;
    }

    /**
     * 生成乱序信息并写回会话。
     * - questionOrder：题目展示顺序（随机洗牌后的 questionId 数组）
     * - optionOrders：每道选择题的选项乱序映射 {questionId:[原下标...]}，下标=展示位、值=原题下标
     * 阅卷时依据 optionOrders 把学生提交的"展示位标签"还原为"原题标签"再比对 correctAnswers。
     */
    private void generateShuffle(ExamSession session) throws Exception {
        List<ExamQuestion> eqs = examQuestionService.list(
                new LambdaQueryWrapper<ExamQuestion>()
                        .eq(ExamQuestion::getExamPaperId, session.getExamPaperId())
                        .orderByAsc(ExamQuestion::getOrderIndex));
        if (eqs == null || eqs.isEmpty()) return;

        // 题目乱序（Fisher-Yates 洗牌）
        List<Long> questionIds = new ArrayList<>();
        for (ExamQuestion eq : eqs) questionIds.add(eq.getQuestionId());
        Long[] arr = questionIds.toArray(new Long[0]);
        for (int i = arr.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Long t = arr[i]; arr[i] = arr[j]; arr[j] = t;
        }
        session.setQuestionOrder(objectMapper.writeValueAsString(Arrays.asList(arr)));

        // 选项乱序（仅选择题）
        Map<Long, int[]> optMap = new LinkedHashMap<>();
        List<Question> questions = questionService.listByIds(questionIds);
        Map<Long, Question> qMap = new HashMap<>();
        if (questions != null) {
            for (Question q : questions) qMap.put(q.getId(), q);
        }
        for (ExamQuestion eq : eqs) {
            Question q = qMap.get(eq.getQuestionId());
            if (q == null || !isChoiceType(q.getType())) continue;
            int n = countOptions(q.getOptions());
            if (n < 2) continue;
            int[] perm = new int[n];
            for (int i = 0; i < n; i++) perm[i] = i;
            for (int i = n - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
            }
            optMap.put(eq.getQuestionId(), perm);
        }
        if (!optMap.isEmpty()) {
            session.setOptionOrders(objectMapper.writeValueAsString(optMap));
        }
        examSessionMapper.updateById(session);
    }

    /**
     * 自动保存作答答案
     */
    @Transactional(rollbackFor = Exception.class)
    public void autoSaveAnswers(Long sessionId, Map<String, Object> answers) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (!"STARTED".equals(session.getStatus())) {
            throw new BusinessException("当前考试状态不允许保存答案");
        }

        // 将答案Map转换为JSON字符串存储（规范化：展示位标签 -> 原题标签，便于阅卷比对）
        String answersJson = mapToJson(normalizeAnswers(answers, session.getOptionOrders()));
        session.setAnswers(answersJson);
        examSessionMapper.updateById(session);
    }

    /**
     * 提交试卷（含防重复提交）
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamSession submitExam(Long sessionId, Map<String, Object> answers) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        // 防重复提交：只有STARTED状态可提交
        if (!"STARTED".equals(session.getStatus())) {
            throw new BusinessException("该试卷已提交或已结束，无法重复提交");
        }

        // 保存最终答案（规范化：展示位标签 -> 原题标签，便于阅卷比对）
        String answersJson = mapToJson(normalizeAnswers(answers, session.getOptionOrders()));
        session.setAnswers(answersJson);

        // 计算考试用时
        LocalDateTime now = LocalDateTime.now();
        long secondsSpent = ChronoUnit.SECONDS.between(session.getStartTime(), now);
        session.setTimeSpent((int) secondsSpent);

        // 更新状态
        session.setStatus("SUBMITTED");
        session.setSubmitTime(now);
        examSessionMapper.updateById(session);

        return session;
    }

    /**
     * 强制交卷（管理员操作）
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamSession forceSubmit(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
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
        examSessionMapper.updateById(session);

        return session;
    }

    /**
     * 超时自动交卷（服务端定时任务调用）：当考试超过"开始时间+考试时长"或"试卷结束时间"
     * （取较早者）仍未提交时，自动收卷并触发自动阅卷，状态置为 TIMEOUT。
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamSession timeoutSubmit(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (!"STARTED".equals(session.getStatus())) {
            return session; // 已结束，无需处理
        }

        LocalDateTime deadline = computeDeadline(session);
        if (deadline == null || LocalDateTime.now().isBefore(deadline)) {
            return session; // 尚未超时
        }

        LocalDateTime now = LocalDateTime.now();
        long secondsSpent = ChronoUnit.SECONDS.between(session.getStartTime(), now);
        session.setTimeSpent((int) secondsSpent);
        session.setStatus("TIMEOUT");
        session.setSubmitTime(now);
        examSessionMapper.updateById(session);

        // 尝试自动阅卷；无作答等异常不影响收卷结果
        try {
            gradingService.autoGrade(sessionId);
        } catch (Exception e) {
            log.warn("超时自动阅卷失败，保留 TIMEOUT 状态由教师处理: sessionId={}", sessionId, e);
        }
        return examSessionMapper.selectById(sessionId);
    }

    /**
     * 计算考试截止时间：取 (开始时间 + 考试时长) 与 (试卷结束时间) 中较早者。
     */
    private LocalDateTime computeDeadline(ExamSession session) {
        ExamPaper paper = examPaperService.getById(session.getExamPaperId());
        if (paper == null) return null;
        LocalDateTime byDuration = null;
        if (paper.getDuration() != null && paper.getDuration() > 0 && session.getStartTime() != null) {
            byDuration = session.getStartTime().plusMinutes(paper.getDuration());
        }
        LocalDateTime byEnd = paper.getEndTime();
        if (byDuration != null && byEnd != null) {
            return byDuration.isBefore(byEnd) ? byDuration : byEnd;
        }
        return byDuration != null ? byDuration : byEnd;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 将Map转换为简单的JSON字符串
     */
    private String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 规范化作答答案：把前端提交的"展示位标签/下标"还原为"原题标签"(A/B/C/D)，
     * 并以 {questionId: 答案} 的 Map 形式返回，供 autoGrade 直接比对 correctAnswers。
     * 兼容多种前端提交格式（Map / 数组 / JSON 串），以及 index 或 letter 两种作答形式。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeAnswers(Object rawAnswers, String optionOrdersJson) {
        Map<Long, int[]> optMap = parseOptionOrders(optionOrdersJson);
        Map<String, Object> result = new LinkedHashMap<>();
        if (rawAnswers == null) {
            return result;
        }

        // 字符串：尝试作为 JSON 解析（可能是 Map 或数组）
        if (rawAnswers instanceof String) {
            String s = ((String) rawAnswers).trim();
            if (s.isEmpty() || "{}".equals(s) || "[]".equals(s)) {
                return result;
            }
            try {
                if (s.startsWith("{")) {
                    rawAnswers = objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {});
                } else if (s.startsWith("[")) {
                    rawAnswers = objectMapper.readValue(s, new TypeReference<List<Object>>() {});
                }
            } catch (Exception e) {
                return result;
            }
        }

        if (rawAnswers instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) rawAnswers;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                result.put(e.getKey(), normalizeValue(e.getKey(), e.getValue(), optMap));
            }
        } else if (rawAnswers instanceof List) {
            List<Object> list = (List<Object>) rawAnswers;
            for (Object o : list) {
                if (o instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    Object qid = m.get("questionId");
                    Object ans = m.get("answer");
                    if (qid != null) {
                        result.put(String.valueOf(qid), normalizeValue(String.valueOf(qid), ans, optMap));
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object normalizeValue(String questionIdStr, Object rawValue, Map<Long, int[]> optMap) {
        Long qid;
        try {
            qid = Long.valueOf(questionIdStr);
        } catch (Exception e) {
            qid = null;
        }
        int[] perm = qid != null ? optMap.get(qid) : null;

        // 选择题：把展示位标签/下标还原为原题标签
        if (perm != null) {
            if (rawValue instanceof List) {
                List<Object> src = (List<Object>) rawValue;
                List<String> translated = new ArrayList<>(src.size());
                for (Object o : src) translated.add(translateDisplayedToOriginal(o, perm));
                return toJsonArrayString(translated);
            }
            if (rawValue instanceof String) {
                String s = ((String) rawValue).trim();
                if (s.startsWith("[")) {
                    try {
                        List<Object> src = objectMapper.readValue(s, new TypeReference<List<Object>>() {});
                        List<String> translated = new ArrayList<>(src.size());
                        for (Object o : src) translated.add(translateDisplayedToOriginal(o, perm));
                        return toJsonArrayString(translated);
                    } catch (Exception e) {
                        // 解析失败则按单值处理
                    }
                }
                return translateDisplayedToOriginal(s, perm);
            }
            // 数字下标
            return translateDisplayedToOriginal(rawValue, perm);
        }

        // 非选择题（判断/简答等）
        if (rawValue instanceof Boolean) {
            return ((Boolean) rawValue) ? "true" : "false";
        }
        if (rawValue instanceof List) {
            List<Object> src = (List<Object>) rawValue;
            List<String> strs = new ArrayList<>(src.size());
            for (Object o : src) strs.add(o == null ? "" : o.toString());
            return toJsonArrayString(strs);
        }
        return rawValue == null ? "" : rawValue.toString();
    }

    /**
     * 把"展示位标签/下标"翻译回"原题标签"(A/B/C/D)。
     * displayed 可能是字母(A/B)或数字下标(0-based)；perm 中 perm[pos]=原题下标。
     */
    private String translateDisplayedToOriginal(Object displayed, int[] perm) {
        int pos = displayedPosition(displayed);
        if (pos < 0 || pos >= perm.length) {
            return displayed == null ? "" : displayed.toString();
        }
        int originalIdx = perm[pos];
        return String.valueOf((char) ('A' + originalIdx));
    }

    private int displayedPosition(Object displayed) {
        if (displayed instanceof Number) {
            return ((Number) displayed).intValue();
        }
        if (displayed instanceof String) {
            String s = ((String) displayed).trim();
            if (s.isEmpty()) return -1;
            if (s.length() == 1 && Character.isLetter(s.charAt(0))) {
                return Character.toUpperCase(s.charAt(0)) - 'A';
            }
            try {
                return Integer.parseInt(s);
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    private String toJsonArrayString(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
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

    private boolean isChoiceType(String type) {
        if (type == null) return false;
        String t = type.trim().toUpperCase();
        return "SINGLE".equals(t) || "SINGLE_CHOICE".equals(t)
                || "MULTIPLE".equals(t) || "MULTIPLE_CHOICE".equals(t);
    }

    private int countOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isEmpty()) return 0;
        try {
            List<?> list = objectMapper.readValue(optionsJson, new TypeReference<List<?>>() {});
            return list == null ? 0 : list.size();
        } catch (Exception e) {
            return optionsJson.split("\\r?\\n").length;
        }
    }
}
