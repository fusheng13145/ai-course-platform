package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.ExamSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.ExamSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
     * 开始一场考试
     * @param body 请求参数
     * @param auth 认证信息
     * @return 考试会话信息
     */
    @PostMapping("/start")
    public ApiResponse<ExamSession> start(@RequestBody Map<String, Long> body, Authentication auth) {
        Long examPaperId = body.get("examPaperId");
        Long studentId = (Long) auth.getPrincipal();

        ExamSession session = new ExamSession();
        session.setExamPaperId(examPaperId);
        session.setStudentId(studentId);
        session.setStatus("STARTED");
        session.setStartTime(LocalDateTime.now());
        session.setAttemptNumber(1);
        examSessionService.save(session);

        return ApiResponse.success("考试开始成功", session);
    }

    /**
     * 提交考试答卷
     * @param id 会话ID
     * @param body 答卷数据
     * @param auth 认证信息
     * @return 提交后的会话信息
     */
    @PostMapping("/{id}/submit")
    public ApiResponse<ExamSession> submit(@PathVariable Long id,
                                           @RequestBody Map<String, Object> body,
                                           Authentication auth) {
        ExamSession session = examSessionService.getById(id);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        Long userId = (Long) auth.getPrincipal();
        if (!session.getStudentId().equals(userId)) {
            throw new BusinessException("无权提交他人考试");
        }

        session.setStatus("SUBMITTED");
        session.setSubmitTime(LocalDateTime.now());
        session.setAnswers(body.get("answers").toString());
        examSessionService.updateById(session);

        return ApiResponse.success("提交成功", session);
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
     * 查询用户在指定试卷上的最新一次考试会话
     * @param examPaperId 试卷ID
     * @param auth 认证信息
     * @return 最新会话信息
     */
    @GetMapping("/paper/{examPaperId}")
    public ApiResponse<Object> mySession(@PathVariable Long examPaperId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<ExamSession> queryWrapper = new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, examPaperId)
                .eq(ExamSession::getStudentId, userId)
                .orderByDesc(ExamSession::getCreatedAt)
                .last("LIMIT 1");
        ExamSession session = examSessionService.getOne(queryWrapper);
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