package com.aicourse.service.impl;

import com.aicourse.entity.ExamSession;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.service.component.exam.ExamSessionCheatingComponent;
import com.aicourse.service.component.exam.ExamSessionLifecycleComponent;
import com.aicourse.service.component.exam.ExamSessionQueryComponent;
import com.aicourse.service.impl.exam.ExamSessionGradingService;
import com.aicourse.vo.AutoGradeResultVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 考试会话服务门面：生命周期/作弊检测/查询职责委托给 service.component 包下协作者组件，
 * 阅卷职责继续委托 {@link ExamSessionGradingService}。
 * <p>拆分自原 672 行单类（2026-08-29）。保留 extends ServiceImpl 以承载 IService 能力，
 * 事务注解保留在门面委托方法上。</p>
 */
@Service
public class ExamSessionServiceImpl extends ServiceImpl<ExamSessionMapper, ExamSession> implements ExamSessionService {

    private final ExamSessionLifecycleComponent lifecycleComponent;
    private final ExamSessionCheatingComponent cheatingComponent;
    private final ExamSessionQueryComponent queryComponent;
    private final ExamSessionGradingService gradingService;

    @Autowired
    public ExamSessionServiceImpl(ExamSessionLifecycleComponent lifecycleComponent,
                                  ExamSessionCheatingComponent cheatingComponent,
                                  ExamSessionQueryComponent queryComponent,
                                  ExamSessionGradingService gradingService) {
        this.lifecycleComponent = lifecycleComponent;
        this.cheatingComponent = cheatingComponent;
        this.queryComponent = queryComponent;
        this.gradingService = gradingService;
    }

    // ===================== 生命周期 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamSession startExam(Long examPaperId, Long studentId, String ipAddress, String userAgent) {
        return lifecycleComponent.startExam(examPaperId, studentId, ipAddress, userAgent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoSaveAnswers(Long sessionId, Map<String, Object> answers) {
        lifecycleComponent.autoSaveAnswers(sessionId, answers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamSession submitExam(Long sessionId, Map<String, Object> answers) {
        return lifecycleComponent.submitExam(sessionId, answers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamSession forceSubmit(Long sessionId) {
        return lifecycleComponent.forceSubmit(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void timeoutSubmit(Long sessionId) {
        lifecycleComponent.timeoutSubmit(sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getRemainingTime(Long sessionId) {
        return lifecycleComponent.getRemainingTime(sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamSession getLatestSession(Long examPaperId, Long studentId) {
        return lifecycleComponent.getLatestSession(examPaperId, studentId);
    }

    // ===================== 作弊检测 =====================

    @Override
    @Transactional(readOnly = true)
    public List<String> detectCheating(Long sessionId) {
        return cheatingComponent.detectCheating(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> checkCheating(Long sessionId) {
        return cheatingComponent.checkCheating(sessionId);
    }

    // ===================== 查询/报告 =====================

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> generateScoreReport(Long sessionId) {
        return queryComponent.generateScoreReport(sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamSession> getActiveExams() {
        return queryComponent.getActiveExams();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSessionQuestions(Long sessionId) {
        return queryComponent.getSessionQuestions(sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMySessionsWithCourseRank(Long studentId) {
        return queryComponent.getMySessionsWithCourseRank(studentId);
    }

    // ===================== 阅卷（委托 ExamSessionGradingService） =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutoGradeResultVO autoGrade(Long sessionId) {
        return gradingService.autoGrade(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualGrade(Long sessionId, Long questionId, BigDecimal score, String comment) {
        gradingService.manualGrade(sessionId, questionId, score, comment);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalScore(Long sessionId) {
        return gradingService.calculateTotalScore(sessionId);
    }
}
