package com.aicourse.vo;

import com.aicourse.entity.ExamSession;
import com.aicourse.entity.User;
import lombok.Data;

/**
 * 试卷下已交卷会话行（教师阅卷台）。
 *
 * <p>3E-03B：原实现由 {@code ExamSessionController} 逐行拼 {@code LinkedHashMap}，
 * 键名靠字符串约定维护。收成 VO 后字段名即契约，键与前端既有口径逐字一致
 * （{@code sessionId/studentId/username/fullName/studentNo/attemptNumber/status/
 * totalScore/percentage/submitTime}），且不再把 {@code User} 的邮箱/密码/角色带出去。</p>
 */
@Data
public class ExamPaperSessionVO {

    /** 考试会话ID。 */
    private Long sessionId;

    /** 学生账号主键。 */
    private Long studentId;

    /** 登录名（学生记录缺失时为 null）。 */
    private String username;

    /** 姓名（学生记录缺失时为 null）。 */
    private String fullName;

    /** 学号（{@code User.studentId} 列）。 */
    private String studentNo;

    /** 第几次作答。 */
    private Integer attemptNumber;

    /** 会话状态（SUBMITTED / GRADED / TIMEOUT / FORCE_SUBMITTED）。 */
    private String status;

    /** 总分。 */
    private java.math.BigDecimal totalScore;

    /** 得分率。 */
    private java.math.BigDecimal percentage;

    /**
     * 交卷时间。
     *
     * <p>刻意保持 {@code String}（原实现取 {@code LocalDateTime#toString()}），
     * 避免从「无时区本地字符串」静默变成别的序列化形态，前端无需改动。</p>
     */
    private String submitTime;

    public static ExamPaperSessionVO of(ExamSession session, User student) {
        ExamPaperSessionVO vo = new ExamPaperSessionVO();
        vo.setSessionId(session.getId());
        vo.setStudentId(session.getStudentId());
        vo.setUsername(student != null ? student.getUsername() : null);
        vo.setFullName(student != null ? student.getFullName() : null);
        vo.setStudentNo(student != null ? student.getStudentId() : null);
        vo.setAttemptNumber(session.getAttemptNumber());
        vo.setStatus(session.getStatus());
        vo.setTotalScore(session.getTotalScore());
        vo.setPercentage(session.getPercentage());
        vo.setSubmitTime(session.getSubmitTime() != null ? session.getSubmitTime().toString() : null);
        return vo;
    }
}
