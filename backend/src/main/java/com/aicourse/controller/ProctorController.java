package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.ProctorEvent;
import com.aicourse.entity.ProctorSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.ExamSessionAccessControl;
import com.aicourse.service.ProctorService;
import com.aicourse.vo.ProctorRiskSummaryVO;
import com.aicourse.vo.ProctorThresholdVO;
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

    private static final Logger log = LoggerFactory.getLogger(ProctorController.class);

    private final ProctorService proctorService;
    private final ExamSessionAccessControl examSessionAccessControl;

    public ProctorController(ProctorService proctorService,
                             ExamSessionAccessControl examSessionAccessControl) {
        this.proctorService = proctorService;
        this.examSessionAccessControl = examSessionAccessControl;
    }

    /**
     * 考生开始一场考试的监考会话。
     * <p>对象级收口（2026-11-13）：原先不验证 examSessionId 归属，任意学生可对他人考试会话
     * 拉起监考。复用 {@link ExamSessionAccessControl#assertOwnedByStudent} 限定仅考生本人。</p>
     */
    @PostMapping("/session/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ProctorSession> start(@RequestParam Long examSessionId, Authentication auth) {
        examSessionAccessControl.assertOwnedByStudent(examSessionId, auth);
        return ApiResponse.success("监考已开始",
                proctorService.startSession(examSessionId, (Long) auth.getPrincipal()));
    }

    /** 考生上报一条监考事件（越权上报会被拒绝） */
    @PostMapping("/session/{id}/event")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ProctorEvent> logEvent(@PathVariable Long id,
                                              @Valid @RequestBody ProctorEvent event,
                                              Authentication auth) {
        Long studentId = (Long) auth.getPrincipal();
        ProctorSession session = proctorService.getById(id);
        if (session == null || !session.getStudentId().equals(studentId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权上报该会话事件");
        }
        return ApiResponse.success("已记录",
                proctorService.logEvent(id, event.getType(), event.getSeverity(),
                        event.getDetail(), event.getEvidenceUrl()));
    }

    /**
     * 考生结束监考会话。
     * <p>对象级收口（2026-11-13）：与 logEvent 同款归属校验，防止任意学生结束他人监考。</p>
     */
    @PostMapping("/session/{id}/end")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ProctorSession> end(@PathVariable Long id, Authentication auth) {
        requireOwnedSession(id, (Long) auth.getPrincipal());
        return ApiResponse.success("监考已结束", proctorService.endSession(id));
    }

    /**
     * 教师/管理员查看某监考会话的全部事件。
     * <p>对象级收口（2026-11-13）：proctor session → exam_session → course 溯源，
     * 教师须为该考试所属课程的授课教师（复用 {@link ExamSessionAccessControl#assertManageable}）。</p>
     */
    @GetMapping("/session/{id}/events")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<ProctorEvent>> events(@PathVariable Long id, Authentication auth) {
        assertCourseManageable(id, auth);
        return ApiResponse.success(proctorService.listEvents(id));
    }

    /**
     * 查询某考生在某场考试中的监考会话（学生仅可查本人）。
     * <p>对象级收口（2026-11-13）：学生加会话本人校验；教师须为该考试所属课程的授课教师。</p>
     */
    @GetMapping("/exam/{examSessionId}/student/{studentId}/session")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    public ApiResponse<ProctorSession> studentSession(@PathVariable Long examSessionId,
                                                      @PathVariable Long studentId,
                                                      Authentication auth) {
        Long uid = (Long) auth.getPrincipal();
        boolean isStudent = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_STUDENT".equals(a.getAuthority()));
        if (isStudent) {
            if (!uid.equals(studentId)) {
                throw new BusinessException(ApiCode.FORBIDDEN, "无权查看他人监考会话");
            }
            examSessionAccessControl.assertOwnedByStudent(examSessionId, auth);
            return ApiResponse.success(proctorService.getStudentSession(examSessionId, studentId));
        }
        examSessionAccessControl.assertManageable(examSessionId, auth);
        return ApiResponse.success(proctorService.getStudentSession(examSessionId, studentId));
    }

    /**
     * 教师/管理员获取某场考试的风险汇总。
     * <p>对象级收口（2026-11-13）：教师须为该考试所属课程的授课教师。</p>
     */
    @GetMapping("/exam/{examSessionId}/risk")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<ProctorRiskSummaryVO>> risk(@PathVariable Long examSessionId,
                                                        Authentication auth) {
        examSessionAccessControl.assertManageable(examSessionId, auth);
        return ApiResponse.success(proctorService.riskSummary(examSessionId));
    }

    // ==================== 考试治理（管理员调控 + 复核工作流） ====================

    /**
     * 读取当前监考风险判定阈值（教师/管理员可见）
     * GET /api/proctor/thresholds
     */
    @GetMapping("/thresholds")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ProctorThresholdVO> getThresholds() {
        return ApiResponse.success(proctorService.getThresholds());
    }

    /**
     * 管理员更新监考风险判定阈值（持久化至 system_configs）
     * PUT /api/proctor/thresholds
     */
    @PutMapping("/thresholds")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateThresholds(@Valid @RequestBody Map<String, Integer> thresholds) {
        proctorService.updateThresholds(thresholds);
        return ApiResponse.success("阈值已更新", null);
    }

    /**
     * 查询单条违规事件（含复核状态），供复核面板使用。
     * <p>对象级收口（2026-11-13）：event → proctor session → exam_session → course 溯源，
     * 教师须为该考试所属课程的授课教师。</p>
     */
    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ProctorEvent> getEvent(@PathVariable Long eventId, Authentication auth) {
        ProctorEvent event = proctorService.getEvent(eventId);
        assertCourseManageable(requireEventSession(event), auth);
        return ApiResponse.success(event);
    }

    /**
     * 教师/管理员对单条违规事件复核：确认违规(REVIEWED) 或 豁免(EXEMPTED)。
     * <p>对象级收口（2026-11-13）：仅加课程管理权前置校验，复核/豁免触发重算回撤分的
     * 既有逻辑（{@code proctorService.reviewEvent} 内部）保持不动。</p>
     */
    @PostMapping("/events/{eventId}/review")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ProctorEvent> reviewEvent(@PathVariable Long eventId,
                                                @Valid @RequestBody Map<String, String> body,
                                                Authentication auth) {
        String status = body.get("status");
        String note = body.get("reviewNote");
        Long reviewerId = (Long) auth.getPrincipal();
        assertCourseManageable(requireEventSession(proctorService.getEvent(eventId)), auth);
        return ApiResponse.success("复核已提交",
                proctorService.reviewEvent(eventId, status, note, reviewerId));
    }

    // ==================== 对象级归属校验辅助 ====================

    /**
     * 监考会话级课程管理权校验：proctor session → exam_sessionId 交给
     * {@link ExamSessionAccessControl#assertManageable} 完成 session→paper→course→teacher 溯源
     * （学生拒绝、管理员放行、教师须为授课教师）。监考会话不存在时 404。
     */
    private void assertCourseManageable(Long proctorSessionId, Authentication auth) {
        ProctorSession session = requireProctorSession(proctorSessionId);
        examSessionAccessControl.assertManageable(session.getExamSessionId(), auth);
    }

    /** 取违规事件挂靠的监考会话ID；事件不存在或未挂靠会话时 404（fail-closed）。 */
    private Long requireEventSession(ProctorEvent event) {
        if (event == null || event.getProctorSessionId() == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "监考事件不存在");
        }
        return event.getProctorSessionId();
    }

    /** 取监考会话，不存在时 404。 */
    private ProctorSession requireProctorSession(Long proctorSessionId) {
        if (proctorSessionId == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "监考会话不存在");
        }
        ProctorSession session = proctorService.getById(proctorSessionId);
        if (session == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "监考会话不存在");
        }
        return session;
    }

    /** 考生本人归属校验（与 logEvent 同口径），会话不存在或非本人时 403。 */
    private void requireOwnedSession(Long proctorSessionId, Long studentId) {
        ProctorSession session = requireProctorSession(proctorSessionId);
        if (!session.getStudentId().equals(studentId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权操作他人监考会话");
        }
    }
}
