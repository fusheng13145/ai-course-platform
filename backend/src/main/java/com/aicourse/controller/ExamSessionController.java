package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.ExamSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.ExamSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 考试会话接口控制器
 */
@RestController
@RequestMapping("/exam-sessions")
public class ExamSessionController {

    private final ExamSessionService examSessionService;

    public ExamSessionController(ExamSessionService examSessionService) {
        this.examSessionService = examSessionService;
    }

    /**
     * 开始一场考试（增强版，含完整校验）
     * @param body 请求参数
     * @param auth 认证信息
     * @param request HTTP请求
     * @return 考试会话信息
     */
    @PostMapping("/start")
    public ApiResponse<ExamSession> start(@RequestBody Map<String, Long> body,
                                          Authentication auth,
                                          HttpServletRequest request) {
        Long examPaperId = body.get("examPaperId");
        Long studentId = (Long) auth.getPrincipal();
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        ExamSession session = examSessionService.startExam(examPaperId, studentId, ipAddress, userAgent);
        return ApiResponse.success("考试开始成功", session);
    }

    /**
     * 自动保存作答答案
     * @param id 会话ID
     * @param body 答案数据
     * @return 操作结果
     */
    @PostMapping("/{id}/auto-save")
    public ApiResponse<Void> autoSave(@PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        Map<String, Object> answers = (Map<String, Object>) body.get("answers");
        examSessionService.autoSaveAnswers(id, answers);
        return ApiResponse.success("答案保存成功", null);
    }

    /**
     * 提交考试答卷（增强版）
     * @param id 会话ID
     * @param body 答卷数据
     * @return 提交后的会话信息
     */
    @PostMapping("/{id}/submit")
    public ApiResponse<ExamSession> submit(@PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        Map<String, Object> answers = (Map<String, Object>) body.get("answers");
        ExamSession session = examSessionService.submitExam(id, answers);
        return ApiResponse.success("提交成功", session);
    }

    /**
     * 强制交卷（管理员操作）
     * @param id 会话ID
     * @return 强制提交后的会话信息
     */
    @PostMapping("/{id}/force-submit")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ExamSession> forceSubmit(@PathVariable Long id) {
        ExamSession session = examSessionService.forceSubmit(id);
        return ApiResponse.success("强制交卷成功", session);
    }

    /**
     * 获取考试剩余时间(秒)
     * @param id 会话ID
     * @return 剩余秒数
     */
    @GetMapping("/{id}/time-remaining")
    public ApiResponse<Long> timeRemaining(@PathVariable Long id) {
        Long remainingTime = examSessionService.getRemainingTime(id);
        return ApiResponse.success(remainingTime);
    }

    /**
     * 触发自动阅卷
     * @param id 会话ID
     * @return 阅卷结果
     */
    @PostMapping("/{id}/auto-grade")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Map<String, Object>> autoGrade(@PathVariable Long id) {
        Map<String, Object> result = examSessionService.autoGrade(id);
        return ApiResponse.success("自动阅卷完成", result);
    }

    /**
     * 人工评分（教师对主观题评分）
     * @param id 会话ID
     * @param body 包含questionId、score、comment
     * @return 操作结果
     */
    @PostMapping("/{id}/manual-grade")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> manualGrade(@PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        Long questionId = Long.valueOf(body.get("questionId").toString());
        BigDecimal score = new BigDecimal(body.get("score").toString());
        String comment = body.get("comment") != null ? body.get("comment").toString() : "";
        examSessionService.manualGrade(id, questionId, score, comment);
        return ApiResponse.success("人工评分完成", null);
    }

    /**
     * 查询成绩报告
     * @param id 会话ID
     * @return 成绩报告详情
     */
    @GetMapping("/{id}/report")
    public ApiResponse<Map<String, Object>> report(@PathVariable Long id) {
        Map<String, Object> report = examSessionService.generateScoreReport(id);
        return ApiResponse.success(report);
    }

    /**
     * 查询考试结果
     * @param id 会话ID
     * @return 会话详情（含成绩/结果）
     */
    @GetMapping("/{id}/result")
    public ApiResponse<ExamSession> result(@PathVariable Long id) {
        ExamSession session = examSessionService.getById(id);
        return ApiResponse.success(session);
    }

    /**
     * 检测作弊嫌疑
     * @param id 会话ID
     * @return 作弊嫌疑列表
     */
    @GetMapping("/{id}/cheating-detect")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<String>> detectCheating(@PathVariable Long id) {
        List<String> suspicions = examSessionService.detectCheating(id);
        return ApiResponse.success(suspicions);
    }

    /**
     * 获取正在进行的考试列表（管理员）
     * @return 进行中的考试会话列表
     */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<ExamSession>> activeExams() {
        List<ExamSession> activeExams = examSessionService.getActiveExams();
        return ApiResponse.success(activeExams);
    }

    /**
     * 查询用户在指定试卷上的最新一次考试会话
     * @param examPaperId 试卷ID
     * @param auth 认证信息
     * @return 最新会话信息
     */
    @GetMapping("/paper/{examPaperId}")
    public ApiResponse<Object> mySession(@PathVariable Long examPaperId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ExamSession session = examSessionService.getLatestSession(examPaperId, userId);
        return ApiResponse.success(session);
    }

    /**
     * 查询用户所有考试会话记录
     * @param auth 认证信息
     * @return 会话列表
     */
    @GetMapping("/my")
    public ApiResponse<Object> mySessions(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<ExamSession> queryWrapper = new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getStudentId, userId)
                .orderByDesc(ExamSession::getCreatedAt);
        return ApiResponse.success(examSessionService.list(queryWrapper));
    }
}
