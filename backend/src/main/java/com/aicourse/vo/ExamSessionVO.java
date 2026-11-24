package com.aicourse.vo;

import com.aicourse.entity.ExamSession;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试会话出参。
 *
 * <p>键集与实体完全一致（23 个字段），只换类型不改契约。
 *
 * <p>两档工厂的区别只有一处：{@link #forStudent} 会把 `cheatingFlags` 置空。
 * 作弊标记是教学侧审核信息，学生自查结果时不应看到系统对他的嫌疑判定
 * （`GET /{id}/result` 走 `assertReadable`，学生可达）。
 * `ipAddress` / `userAgent` 是考生本人的自身上报数据，不涉他人，保留。
 */
@Data
public class ExamSessionVO {

    private Long id;
    private Long examPaperId;
    private Long studentId;
    private String status;
    private Integer attemptNumber;
    private LocalDateTime startTime;
    private LocalDateTime submitTime;
    private Integer timeSpent;
    private BigDecimal totalScore;
    private BigDecimal percentage;
    private String answers;
    private String questionScores;
    private String questionComments;
    private String cheatingFlags;
    private String questionOrder;
    private String optionOrders;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long teachingClassId;

    /** 教学侧 / 管理侧视图：全字段。 */
    public static ExamSessionVO from(ExamSession session) {
        ExamSessionVO vo = copyBase(session);
        if (vo != null) {
            vo.setCheatingFlags(session.getCheatingFlags());
        }
        return vo;
    }

    /** 学生自查视图：隐去作弊标记，其余键保持原样（值为 null 而非删键）。 */
    public static ExamSessionVO forStudent(ExamSession session) {
        ExamSessionVO vo = copyBase(session);
        if (vo != null) {
            vo.setCheatingFlags(null);
        }
        return vo;
    }

    public static List<ExamSessionVO> from(List<ExamSession> sessions) {
        return sessions == null ? List.of() : sessions.stream().map(ExamSessionVO::from).toList();
    }

    private static ExamSessionVO copyBase(ExamSession session) {
        if (session == null) {
            return null;
        }
        ExamSessionVO vo = new ExamSessionVO();
        vo.setId(session.getId());
        vo.setExamPaperId(session.getExamPaperId());
        vo.setStudentId(session.getStudentId());
        vo.setStatus(session.getStatus());
        vo.setAttemptNumber(session.getAttemptNumber());
        vo.setStartTime(session.getStartTime());
        vo.setSubmitTime(session.getSubmitTime());
        vo.setTimeSpent(session.getTimeSpent());
        vo.setTotalScore(session.getTotalScore());
        vo.setPercentage(session.getPercentage());
        vo.setAnswers(session.getAnswers());
        vo.setQuestionScores(session.getQuestionScores());
        vo.setQuestionComments(session.getQuestionComments());
        vo.setQuestionOrder(session.getQuestionOrder());
        vo.setOptionOrders(session.getOptionOrders());
        vo.setIpAddress(session.getIpAddress());
        vo.setUserAgent(session.getUserAgent());
        vo.setCreatedAt(session.getCreatedAt());
        vo.setUpdatedAt(session.getUpdatedAt());
        vo.setTeachingClassId(session.getTeachingClassId());
        return vo;
    }
}
