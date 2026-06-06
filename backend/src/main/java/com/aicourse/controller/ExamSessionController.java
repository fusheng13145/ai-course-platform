package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.ExamSession;
import com.aicourse.service.ExamSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/exam-sessions")
public class ExamSessionController {
    private final ExamSessionService examSessionService;

    public ExamSessionController(ExamSessionService examSessionService) {
        this.examSessionService = examSessionService;
    }

    @PostMapping("/start")
    public ApiResponse<ExamSession> start(@RequestBody Map<String, Long> body, Authentication auth) {
        ExamSession session = new ExamSession();
        session.setExamPaperId(body.get("examPaperId"));
        session.setStudentId((Long) auth.getPrincipal());
        session.setStatus("STARTED");
        session.setStartTime(LocalDateTime.now());
        session.setAttemptNumber(1);
        examSessionService.save(session);
        return ApiResponse.success("考试开始", session);
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ExamSession> submit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ExamSession session = examSessionService.getById(id);
        session.setStatus("SUBMITTED");
        session.setSubmitTime(LocalDateTime.now());
        session.setAnswers(body.get("answers").toString());
        examSessionService.updateById(session);
        return ApiResponse.success("提交成功", session);
    }

    @GetMapping("/{id}/result")
    public ApiResponse<ExamSession> result(@PathVariable Long id) {
        return ApiResponse.success(examSessionService.getById(id));
    }

    @GetMapping("/paper/{examPaperId}")
    public ApiResponse<Object> mySession(@PathVariable Long examPaperId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ExamSession session = examSessionService.getOne(
            new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, examPaperId)
                .eq(ExamSession::getStudentId, userId)
                .orderByDesc(ExamSession::getCreatedAt)
                .last("LIMIT 1"));
        return ApiResponse.success(session);
    }
}
