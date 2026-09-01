package com.aicourse.vo;

import lombok.Data;

import java.util.Map;

/**
 * 班级统计数据出参。
 * 字段名与历史 Map 键一致（totalStudents / activeStudents / inactiveStudents / maxCapacity / currentCount / majorDistribution）。
 * majorDistribution 为动态键嵌套 Map（键=专业名，值=人数），保留不强行结构化（同 VoteResultVO.counts 模式）。
 * 原实现无条件 put 此六键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class ClassroomStatsVO {

    private long totalStudents;
    private long activeStudents;
    private long inactiveStudents;
    private Integer maxCapacity;
    private Integer currentCount;
    private Map<String, Long> majorDistribution;

    public ClassroomStatsVO() {
    }

    public ClassroomStatsVO(long totalStudents, long activeStudents, long inactiveStudents,
                            Integer maxCapacity, Integer currentCount,
                            Map<String, Long> majorDistribution) {
        this.totalStudents = totalStudents;
        this.activeStudents = activeStudents;
        this.inactiveStudents = inactiveStudents;
        this.maxCapacity = maxCapacity;
        this.currentCount = currentCount;
        this.majorDistribution = majorDistribution;
    }
}
