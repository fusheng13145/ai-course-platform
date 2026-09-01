package com.aicourse.vo;

import lombok.Data;

/**
 * 课程各班级待批改作业数出参（驾驶舱班级 Tab 待批徽标数据源）。
 * 字段名与历史 Map 键一致（classroomId / classroomName / pendingCount）。
 * 原实现无条件 put 此三键（恒含，含真实 0 值），故默认 INCLUDE.ALWAYS，精确复刻。
 */
@Data
public class ClassroomPendingCountVO {

    private Long classroomId;
    private String classroomName;
    private long pendingCount;

    public ClassroomPendingCountVO() {
    }

    public ClassroomPendingCountVO(Long classroomId, String classroomName, long pendingCount) {
        this.classroomId = classroomId;
        this.classroomName = classroomName;
        this.pendingCount = pendingCount;
    }
}
