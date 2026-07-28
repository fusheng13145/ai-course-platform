package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.dto.VideoHeartbeatDTO;
import com.aicourse.service.StudentLearningService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 学生端学习核心控制器
 * 基础路径：/api/student（与既有 StudentController 共享 /student 前缀，端点互补）
 * <p>
 * 提供对标超星学习通的学生核心能力：
 * - 学习空间/首页聚合驾驶舱
 * - 章节任务点学习中心（含闯关解锁）
 * - 任务点完成态（视频续播/作业/测验即时判分）
 * - 成绩总览
 */
@RestController
@RequestMapping("/student")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "学生学习")
@Slf4j
public class StudentLearningController {

    private final StudentLearningService studentLearningService;

    public StudentLearningController(StudentLearningService studentLearningService) {
        this.studentLearningService = studentLearningService;
    }

    /** 学生首页/学习空间聚合驾驶舱 */
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(studentLearningService.getStudentDashboard(userId));
    }

    /** 课程章节任务点学习中心（含闯关解锁态） */
    @GetMapping("/courses/{courseId}/learning-center")
    public ApiResponse<Map<String, Object>> learningCenter(@PathVariable Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(studentLearningService.getCourseLearningCenter(userId, courseId));
    }

    /** 课程各章节任务点完成度统计（X/Y） */
    @GetMapping("/courses/{courseId}/progress-summary")
    public ApiResponse<Map<String, Object>> progressSummary(@PathVariable Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(studentLearningService.getCourseProgressSummary(userId, courseId));
    }

    /** 标记任务点完成（视频/文档/阅读/测验非提交类） */
    @PostMapping("/courses/{courseId}/tasks/{taskId}/complete")
    public ApiResponse<Map<String, Object>> completeTask(@PathVariable Long courseId,
                                                         @PathVariable Long taskId,
                                                         @RequestBody(required = false) Map<String, String> body,
                                                         Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String completionType = body != null ? body.get("completionType") : null;
        return ApiResponse.success(studentLearningService.markTaskComplete(userId, courseId, taskId, completionType));
    }

    /** 获取视频任务点续播状态 */
    @GetMapping("/courses/{courseId}/tasks/{taskId}/video-state")
    public ApiResponse<Map<String, Object>> videoState(@PathVariable Long courseId,
                                                       @PathVariable Long taskId,
                                                       Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(studentLearningService.getVideoState(userId, courseId, taskId));
    }

    /** 上报视频观看心跳（累计观看时长 + 当前位置；达阈值自动判定完成） */
    @PostMapping("/courses/{courseId}/tasks/{taskId}/video-heartbeat")
    public ApiResponse<Map<String, Object>> videoHeartbeat(@PathVariable Long courseId,
                                                          @PathVariable Long taskId,
                                                          @Valid @RequestBody VideoHeartbeatDTO dto,
                                                          Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(studentLearningService.reportVideoHeartbeat(
                userId, courseId, taskId, dto.getResourceId(), dto.getPosition(),
                dto.getWatchedDuration(), dto.getTotalDuration(), dto.getPlaybackSpeed()));
    }

    /** 测验任务点即时判分 */
    @PostMapping("/courses/{courseId}/tasks/{taskId}/quiz-submit")
    public ApiResponse<Map<String, Object>> quizSubmit(@PathVariable Long courseId,
                                                       @PathVariable Long taskId,
                                                       @RequestBody Map<String, String> answers,
                                                       Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Map<Long, String> parsed = new LinkedHashMap<>();
        if (answers != null) {
            for (Map.Entry<String, String> e : answers.entrySet()) {
                try {
                    parsed.put(Long.valueOf(e.getKey()), e.getValue());
                } catch (NumberFormatException ignore) {
                    log.warn("解析答案键失败: key={}", e.getKey());
                }
            }
        }
        return ApiResponse.success(studentLearningService.submitQuiz(userId, courseId, taskId, parsed));
    }

    /** 学生成绩总览（按课程聚合作业/考试/总评） */
    @GetMapping("/grades")
    public ApiResponse<Map<String, Object>> grades(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(studentLearningService.getGrades(userId));
    }
}
