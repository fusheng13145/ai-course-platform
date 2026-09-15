package com.aicourse.service;

import com.aicourse.vo.CourseRiskPredictionVO;

/**
 * 学情风险预测服务（P2 消费者端增强：预测性学情预警）。
 * <p>基于「成绩 / 进度 / 登录活跃度 / 课程学习行为 / 累计学习时长」五维信号加权，
 * 输出每名学生的风险分（0-100）、风险等级（LOW/MID/HIGH）与可解释原因，
 * 供教师端「学情预警」清单进行早期干预。纯统计派生，不依赖任何外部 AI 引擎。</p>
 */
public interface LearningRiskService {

    /**
     * 预测指定课程的学生学情风险。
     *
     * @param courseId 课程ID
     * @return 课程级风险预测（含汇总与各学生风险明细）；courseId 为空或无在修学生时返回空结果
     */
    CourseRiskPredictionVO predictCourseRisk(Long courseId);
}
