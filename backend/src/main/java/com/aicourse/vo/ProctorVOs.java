package com.aicourse.vo;

import com.aicourse.entity.ProctorEvent;
import com.aicourse.entity.ProctorSession;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监考出参：监考会话 / 监考事件。
 *
 * <p>字段与实体一一对应、零删减。监考事件里的 `evidenceUrl` 是考生端截屏/摄像证据，
 * 只有监考教师与管理员可达（`ProctorController` 各端点带 `@PreAuthorize` 与归属校验），
 * 故不裁剪。
 */
public final class ProctorVOs {

    private ProctorVOs() {
    }

    /** 监考会话出参。 */
    @Data
    public static class SessionVO {
        private Long id;
        private Long examSessionId;
        private Long studentId;
        private String status;
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;
        private Integer riskScore;
        private String riskLevel;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static SessionVO from(ProctorSession session) {
            if (session == null) {
                return null;
            }
            SessionVO vo = new SessionVO();
            vo.setId(session.getId());
            vo.setExamSessionId(session.getExamSessionId());
            vo.setStudentId(session.getStudentId());
            vo.setStatus(session.getStatus());
            vo.setStartedAt(session.getStartedAt());
            vo.setEndedAt(session.getEndedAt());
            vo.setRiskScore(session.getRiskScore());
            vo.setRiskLevel(session.getRiskLevel());
            vo.setCreatedAt(session.getCreatedAt());
            vo.setUpdatedAt(session.getUpdatedAt());
            return vo;
        }
    }

    /** 监考事件出参。 */
    @Data
    public static class EventVO {
        private Long id;
        private Long proctorSessionId;
        private String type;
        private String severity;
        private String detail;
        private String evidenceUrl;
        private LocalDateTime capturedAt;
        private String status;
        private String reviewNote;
        private LocalDateTime reviewedAt;
        private Long reviewerId;
        private LocalDateTime createdAt;

        public static EventVO from(ProctorEvent event) {
            if (event == null) {
                return null;
            }
            EventVO vo = new EventVO();
            vo.setId(event.getId());
            vo.setProctorSessionId(event.getProctorSessionId());
            vo.setType(event.getType());
            vo.setSeverity(event.getSeverity());
            vo.setDetail(event.getDetail());
            vo.setEvidenceUrl(event.getEvidenceUrl());
            vo.setCapturedAt(event.getCapturedAt());
            vo.setStatus(event.getStatus());
            vo.setReviewNote(event.getReviewNote());
            vo.setReviewedAt(event.getReviewedAt());
            vo.setReviewerId(event.getReviewerId());
            vo.setCreatedAt(event.getCreatedAt());
            return vo;
        }

        public static List<EventVO> from(List<ProctorEvent> events) {
            return events == null ? List.of() : events.stream().map(EventVO::from).toList();
        }
    }
}
