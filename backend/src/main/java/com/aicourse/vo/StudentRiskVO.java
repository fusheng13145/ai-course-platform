package com.aicourse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生级学情风险预测视图对象（P2 预测性学情预警）。
 * <p>由 {@code LearningRiskService} 基于多维信号加权计算，给出 0-100 风险分、风险等级
 * 与可解释的中文原因，供教师端「学情预警」清单直接消费。</p>
 */
@Data
public class StudentRiskVO {

    /** 学生ID */
    private Long studentId;

    /** 学生姓名 */
    private String studentName;

    /** 学号 */
    private String studentNo;

    /** 风险评分（0-100，越高越危险） */
    private int riskScore;

    /** 风险等级：LOW / MID / HIGH */
    private String riskTier;

    /** 触发风险的可解释原因（中文） */
    private List<String> reasons;

    /** 最后活跃时间（登录 / 学习行为的最大时间），可能为 null */
    private LocalDateTime lastActiveAt;

    /** 课程学习进度（0-1，来自选课记录），可能为 null */
    private BigDecimal progress;

    /** 综合/期末成绩快照（百分制），可能为 null */
    private BigDecimal scoreSnapshot;
}
