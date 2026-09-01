package com.aicourse.vo;

import lombok.Data;

/**
 * 平台数据概览出参（管理员统计页）。
 * 字段名与历史 Map 键一致（totalUsers / totalCourses / totalEnrollments / roleDistribution / activeCourses）。
 * roleDistribution 为固定三键（student/teacher/admin）结构，一并收敛为 {@link RoleDistributionVO}。
 * 原实现无条件 put 此五键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class StatisticsOverviewVO {

    private long totalUsers;
    private long totalCourses;
    private long totalEnrollments;
    private RoleDistributionVO roleDistribution;
    private long activeCourses;

    public StatisticsOverviewVO() {
    }

    public StatisticsOverviewVO(long totalUsers, long totalCourses, long totalEnrollments,
                                RoleDistributionVO roleDistribution, long activeCourses) {
        this.totalUsers = totalUsers;
        this.totalCourses = totalCourses;
        this.totalEnrollments = totalEnrollments;
        this.roleDistribution = roleDistribution;
        this.activeCourses = activeCourses;
    }
}
