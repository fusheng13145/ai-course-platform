package com.aicourse.application.exam;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.ExamSessionAccessControl;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.UserService;
import com.aicourse.vo.ActiveExamSessionVO;
import com.aicourse.vo.AutoGradeResultVO;
import com.aicourse.vo.ExamPaperSessionVO;
import com.aicourse.vo.ExamSessionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 考试会话应用服务（3E-03B）。
 *
 * <p>把原先堆在 {@code ExamSessionController} 里的编排职责收拢到一处：对象级授权
 * （{@link ExamSessionAccessControl} / {@link CourseAccessControl}）、试卷与课程归属校验、
 * 会话查询与展示行组装、交卷后重读、监考名单的课程过滤，全部在这里成为唯一事实源。
 * 控制器只做参数绑定、IP 解析与响应封装。</p>
 *
 * <p>刻意不引入 servlet 依赖：客户端 IP 与 User-Agent 由控制器从请求里取好后以
 * {@code String} 传入，应用层不感知 HTTP。</p>
 */
@Service
public class ExamSessionApplicationService {

    /** 阅卷台只关心已结束的会话状态。 */
    private static final String[] FINISHED_STATUSES = {"SUBMITTED", "GRADED", "TIMEOUT", "FORCE_SUBMITTED"};

    private final ExamSessionService examSessionService;
    private final ExamSessionAccessControl examSessionAccessControl;
    private final CourseAccessControl courseAccessControl;
    private final ExamPaperService examPaperService;
    private final UserService userService;

    public ExamSessionApplicationService(ExamSessionService examSessionService,
                                         ExamSessionAccessControl examSessionAccessControl,
                                         CourseAccessControl courseAccessControl,
                                         ExamPaperService examPaperService,
                                         UserService userService) {
        this.examSessionService = examSessionService;
        this.examSessionAccessControl = examSessionAccessControl;
        this.courseAccessControl = courseAccessControl;
        this.examPaperService = examPaperService;
        this.userService = userService;
    }

    // ====================== 教师阅卷台 ======================

    /**
     * 试卷的全部已交卷会话（教师阅卷台用）。
     *
     * <p>只返回已交卷的会话，含学生姓名与学号，便于教师逐份核对主观题。
     * 学生信息一次 {@code listByIds} 批量取回，不逐行回查。</p>
     */
    public List<ExamPaperSessionVO> paperSessions(Long examPaperId, Authentication auth) {
        ExamPaper paper = examPaperService.getById(examPaperId);
        if (paper == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "试卷不存在");
        }
        if (paper.getCourseId() == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "试卷未关联课程");
        }
        courseAccessControl.assertCanManageCourse(paper.getCourseId(), auth);

        List<ExamSession> sessions = examSessionService.list(new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, examPaperId)
                .in(ExamSession::getStatus, (Object[]) FINISHED_STATUSES)
                .orderByDesc(ExamSession::getSubmitTime)
                .orderByDesc(ExamSession::getId));

        Set<Long> studentIds = sessions.stream()
                .map(ExamSession::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = studentIds.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(studentIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<ExamPaperSessionVO> rows = new ArrayList<>(sessions.size());
        for (ExamSession session : sessions) {
            rows.add(ExamPaperSessionVO.of(session, userMap.get(session.getStudentId())));
        }
        return rows;
    }

    // ====================== 学生作答链 ======================

    /** 开始一场考试：先过开考校验（选课学生），再由服务层落会话。 */
    public ExamSessionVO start(Long examPaperId, Long studentId, String ipAddress, String userAgent,
                               Authentication auth) {
        examSessionAccessControl.assertExamStartable(examPaperId, auth);
        ExamSession session = examSessionService.startExam(examPaperId, studentId, ipAddress, userAgent);
        return ExamSessionVO.forStudent(session);
    }

    /** 本场考试的题目列表（已应用题目乱序与选项乱序）。 */
    public List<Map<String, Object>> questions(Long sessionId, Authentication auth) {
        examSessionAccessControl.assertReadable(sessionId, auth);
        return examSessionService.getSessionQuestions(sessionId);
    }

    /** 自动保存作答进度。 */
    public void autoSave(Long sessionId, Map<String, Object> answers, Authentication auth) {
        examSessionAccessControl.assertOwnedByStudent(sessionId, auth);
        examSessionService.autoSaveAnswers(sessionId, answers);
    }

    /**
     * 正式交卷。
     *
     * <p>交卷事务提交后会触发客观题自动判分，故此处重读一次，返回最新状态
     * （通常是 GRADED + totalScore），而不是交卷瞬间的对象。</p>
     */
    public ExamSessionVO submit(Long sessionId, Map<String, Object> answers, Authentication auth) {
        examSessionAccessControl.assertOwnedByStudent(sessionId, auth);
        ExamSession session = examSessionService.submitExam(sessionId, answers);
        return ExamSessionVO.forStudent(examSessionService.getById(session.getId()));
    }

    /** 剩余时间（秒）。 */
    public Long timeRemaining(Long sessionId, Authentication auth) {
        examSessionAccessControl.assertReadable(sessionId, auth);
        return examSessionService.getRemainingTime(sessionId);
    }

    /** 当前用户在指定试卷上的最新一次会话。 */
    public ExamSessionVO mySession(Long examPaperId, Long userId) {
        return ExamSessionVO.forStudent(examSessionService.getLatestSession(examPaperId, userId));
    }

    /** 当前用户全部历史会话（附课程维度排名）。 */
    public List<Map<String, Object>> mySessions(Long userId) {
        return examSessionService.getMySessionsWithCourseRank(userId);
    }

    // ====================== 教师阅卷 / 监考 ======================

    /** 强制交卷（教学写面）。 */
    public ExamSessionVO forceSubmit(Long sessionId, Authentication auth) {
        examSessionAccessControl.assertManageable(sessionId, auth);
        return ExamSessionVO.from(examSessionService.forceSubmit(sessionId));
    }

    /** 自动阅卷（客观题判分）。 */
    public AutoGradeResultVO autoGrade(Long sessionId, Authentication auth) {
        examSessionAccessControl.assertManageable(sessionId, auth);
        return examSessionService.autoGrade(sessionId);
    }

    /** 人工评分（主观题）。 */
    public void manualGrade(Long sessionId, Long questionId, BigDecimal score, String comment,
                            Authentication auth) {
        examSessionAccessControl.assertManageable(sessionId, auth);
        examSessionService.manualGrade(sessionId, questionId, score, comment != null ? comment : "");
    }

    /** 成绩报告。 */
    public Map<String, Object> report(Long sessionId, Authentication auth) {
        examSessionAccessControl.assertReadable(sessionId, auth);
        return examSessionService.generateScoreReport(sessionId);
    }

    /** 考试结果：复用归属校验时已加载的会话对象，避免重复查询。 */
    public ExamSessionVO result(Long sessionId, Authentication auth) {
        ExamSession session = examSessionAccessControl.assertReadable(sessionId, auth);
        return ExamSessionVO.forStudent(session);
    }

    /** 作弊嫌疑检测（只读）。 */
    public List<String> detectCheating(Long sessionId, Authentication auth) {
        examSessionAccessControl.assertManageable(sessionId, auth);
        return examSessionService.detectCheating(sessionId);
    }

    /** 作弊检测并标记（写面）。 */
    public Map<String, Object> checkCheating(Long sessionId, Authentication auth) {
        examSessionAccessControl.assertManageable(sessionId, auth);
        return examSessionService.checkCheating(sessionId);
    }

    /**
     * 进行中的考试列表（监考链）。
     *
     * <p>对象级收口（2026-11-13 起沿用）：按 {@link CourseAccessControl#readableCourseIds}
     * 过滤——教师仅见自己课程的进行中会话（按 VO 的 courseId 匹配），管理员
     * （readableCourseIds 返回 null）不限。教师无任何课程时返回空名单，**不回退全量**。</p>
     */
    public List<ActiveExamSessionVO> activeExams(Authentication auth) {
        List<ActiveExamSessionVO> activeExams = examSessionService.getActiveExams();
        List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
        if (readableCourseIds == null) {
            return activeExams;
        }
        return activeExams.stream()
                .filter(vo -> vo.getCourseId() != null && readableCourseIds.contains(vo.getCourseId()))
                .toList();
    }
}
