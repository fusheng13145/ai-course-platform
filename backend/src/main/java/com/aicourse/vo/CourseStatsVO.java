package com.aicourse.vo;

import lombok.Data;

/**
 * 课程统计数据出参（课程学生数、待审核数、状态等）。
 * 字段名与历史 Map 键一致（totalStudents / pendingStudents / courseId / courseName / status / approvalStatus）。
 * 原实现无条件 put 此六键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class CourseStatsVO {

    private long totalStudents;
    private long pendingStudents;
    private Long courseId;
    private String courseName;
    private String status;
    private String approvalStatus;

    public CourseStatsVO() {
    }

    public CourseStatsVO(long totalStudents, long pendingStudents, Long courseId,
                         String courseName, String status, String approvalStatus) {
        this.totalStudents = totalStudents;
        this.pendingStudents = pendingStudents;
        this.courseId = courseId;
        this.courseName = courseName;
        this.status = status;
        this.approvalStatus = approvalStatus;
    }
}
