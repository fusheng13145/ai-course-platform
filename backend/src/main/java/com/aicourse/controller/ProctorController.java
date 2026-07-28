package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.ProctorEvent;
import com.aicourse.entity.ProctorSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.ProctorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 在线监考（考试防作弊）接口
 * 基础路径：/api/proctor
 * 考生端：开始/上报事件/结束/查本人；教师端：查看事件与风险汇总
 */
@RestController
@RequestMapping("/proctor")
@Tag(name = "在线监考")
public class ProctorController {

    private final ProctorService proctorService;

    public ProctorController(ProctorService proctorService) {
        this.proctorService = proctorService;
    }

    @PostMapping("/session/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ProctorSession> start(@RequestParam Long examSessionId, Authentication auth) {
        return ApiResponse.success("监考已开始",
                proctorService.startSession(examSessionId, (Long) auth.getPrincipal()));
    }

    @PostMapping("/session/{id}/event")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ProctorEvent> logEvent(@PathVariable Long id,
                                              @RequestBody ProctorEvent event,
                                              Authentication auth) {
        Long studentId = (Long) auth.getPrincipal();
        ProctorSession session = proctorService.getById(id);
        if (session == null || !session.getStudentId().equals(studentId)) {
            throw new BusinessException("无权上报该会话事件");
        }
        return ApiResponse.success("已记录",
                proctorService.logEvent(id, event.getType(), event.getSeverity(),
                        event.getDetail(), event.getEvidenceUrl()));
    }

    @PostMapping("/session/{id}/end")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ProctorSession> end(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success("监考已结束", proctorService.endSession(id));
    }

    @GetMapping("/session/{id}/events")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<ProctorEvent>> events(@PathVariable Long id) {
        return ApiResponse.success(proctorService.listEvents(id));
    }

    @GetMapping("/exam/{examSessionId}/student/{studentId}/session")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    public ApiResponse<ProctorSession> studentSession(@PathVariable Long examSessionId,
                                                      @PathVariable Long studentId,
                                                      Authentication auth) {
        Long uid = (Long) auth.getPrincipal();
        boolean isStudent = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_STUDENT".equals(a.getAuthority()));
        if (isStudent && !uid.equals(studentId)) {
            throw new BusinessException("无权查看他人监考会话");
        }
        return ApiResponse.success(proctorService.getStudentSession(examSessionId, studentId));
    }

    @GetMapping("/exam/{examSessionId}/risk")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<Map<String, Object>>> risk(@PathVariable Long examSessionId) {
        return ApiResponse.success(proctorService.riskSummary(examSessionId));
    }

    // ==================== 考试治理（管理员调控 + 复核工作流） ====================

    /**
     * 读取当前监考风险判定阈值（教师/管理员可见）
     * GET /api/proctor/thresholds
     */
    @GetMapping("/thresholds")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Map<String, Object>> getThresholds() {
        return ApiResponse.success(proctorService.getThresholds());
    }

    /**
     * 管理员更新监考风险判定阈值（持久化至 system_configs）
     * PUT /api/proctor/thresholds
     */
    @PutMapping("/thresholds")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateThresholds(@RequestBody Map<String, Integer> thresholds) {
        proctorService.updateThresholds(thresholds);
        return ApiResponse.success("阈值已更新", null);
    }

    /**
     * 查询单条违规事件（含复核状态），供复核面板使用
     * GET /api/proctor/events/{eventId}
     */
    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ProctorEvent> getEvent(@PathVariable Long eventId) {
        return ApiResponse.success(proctorService.getEvent(eventId));
    }

    /**
     * 教师/管理员对单条违规事件复核：确认违规(REVIEWED) 或 豁免(EXEMPTED)
     * POST /api/proctor/events/{eventId}/review
     */
    @PostMapping("/events/{eventId}/review")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ProctorEvent> reviewEvent(@PathVariable Long eventId,
                                                @RequestBody Map<String, String> body,
                                                Authentication auth) {
        String status = body.get("status");
        String note = body.get("reviewNote");
        Long reviewerId = (Long) auth.getPrincipal();
        return ApiResponse.success("复核已提交",
                proctorService.reviewEvent(eventId, status, note, reviewerId));
    }
}
