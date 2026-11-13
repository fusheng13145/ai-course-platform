package com.aicourse.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 班主任班级画像 VO（B-12／P-08 6A，WP9）。
 * <p>
 * 6A 拍板：班主任只拿<b>聚合画像</b>，学生个人成绩字段不出现在响应体——
 * 个人成绩另开口径（教师端既有成绩页）。新增字段前先回看这一条。</p>
 */
@Data
public class AdvisorClassProfileVO {

    /** 行政班ID */
    private Long adminClassId;

    /** 行政班名称 */
    private String className;

    /** 在册学生数（isActive 用户的 admin_class_id 归属口径） */
    private long studentCount;

    /** 平均课程进度（%，选课记录口径；无选课记录时为 null） */
    private BigDecimal avgProgress;

    /** 平均总评成绩（finalScore；无已出成绩记录时为 null） */
    private BigDecimal avgFinalScore;

    /** 预警数（finalScore < 60 的选课记录数） */
    private long atRiskCount;

    /** 已出总评的选课记录数 */
    private long gradedCount;
}
