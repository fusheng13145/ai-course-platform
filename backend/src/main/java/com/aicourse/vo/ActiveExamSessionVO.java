package com.aicourse.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 进行中考试会话出参（监考链）。
 *
 * <p>B-13 ⑤：原 {@code GET /exam-sessions/active} 直接返回裸 {@code ExamSession}，
 * 不带试卷名、课程与课程 ID、学生姓名，前端只能按 id 再发两次请求（试卷/课程回填、
 * {@code GET /users} 用户名单）才能渲染；用户名单还需要管理员权限。
 * 本 VO 在服务端批量补全展示字段，并给出同一试卷当前进行中的会话数
 * （{@code participantCount}，监考页一屏看到人数，不再逐卷查询）。</p>
 *
 * <p>兼容性：原有字段 {@code id/examPaperId/studentId/status/startTime/submitTime/
 * attemptNumber/timeSpent} 全部保留，只做加键。</p>
 */
@Data
public class ActiveExamSessionVO {

    /** 考试会话ID */
    private Long id;

    /** 试卷ID */
    private Long examPaperId;

    /** 试卷标题（试卷缺失时为 null） */
    private String paperTitle;

    /** 所属课程ID（试卷未关联课程时为 null） */
    private Long courseId;

    /** 所属课程名称（课程缺失时为 null） */
    private String courseName;

    /** 作答学生ID */
    private Long studentId;

    /** 学生姓名（fullName 优先，回落 username；用户缺失时为 null） */
    private String studentName;

    /** 作答状态 */
    private String status;

    /** 作答次数 */
    private Integer attemptNumber;

    /** 考试开始时间 */
    private LocalDateTime startTime;

    /** 提交时间（进行中通常为空） */
    private LocalDateTime submitTime;

    /** 作答耗时 */
    private Integer timeSpent;

    /** 同一试卷当前进行中的会话数（监考页用，非课程总人数） */
    private Integer participantCount;
}
