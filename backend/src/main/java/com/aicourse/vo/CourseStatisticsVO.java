package com.aicourse.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 课程统计出参（GET /admin/statistics/courses）。
 * 字段名与历史 Map 键一致（totalCourses / activeCourses / categoryDistribution / topCourses）。
 * categoryDistribution 为动态键嵌套 Map（键=课程分类，值=数量），保留 Map 形态；
 * topCourses 为 analyticsService.getCourseRanking() 的原始聚合结构（多字段动态），同样保留 List<Map> 形态。
 * 原实现无条件 put 此四键（恒含），故默认 INCLUDE.ALWAYS，精确复刻。
 */
@Data
public class CourseStatisticsVO {

    private long totalCourses;
    private long activeCourses;
    private Map<String, Long> categoryDistribution;
    private List<Map<String, Object>> topCourses;

    public CourseStatisticsVO() {
    }

    public CourseStatisticsVO(long totalCourses, long activeCourses,
                              Map<String, Long> categoryDistribution,
                              List<Map<String, Object>> topCourses) {
        this.totalCourses = totalCourses;
        this.activeCourses = activeCourses;
        this.categoryDistribution = categoryDistribution;
        this.topCourses = topCourses;
    }
}
