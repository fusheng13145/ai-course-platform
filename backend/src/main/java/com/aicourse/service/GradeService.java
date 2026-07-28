package com.aicourse.service;

import com.aicourse.entity.GradeItem;
import com.aicourse.entity.GradePolicy;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 过程性成绩（多维加权）服务
 */
public interface GradeService extends IService<GradePolicy> {

    /**
     * 保存/更新课程成绩方案（每门课程一套默认方案）
     */
    GradePolicy savePolicy(GradePolicy policy, Long userId);

    /**
     * 获取课程成绩方案
     */
    GradePolicy getPolicy(Long courseId);

    /**
     * 录入/更新某学生某一维度成绩（按 policyId+studentId+componentKey 幂等）
     */
    GradeItem upsertItem(Long policyId, Long studentId, String componentKey,
                         BigDecimal score, BigDecimal rawScore, Long sourceId,
                         Long graderId, String remark);

    /**
     * 计算某学生最终加权总成绩（含各维度明细）
     */
    Map<String, Object> computeFinal(Long policyId, Long studentId);

    /**
     * 教师视角：课程全员最终成绩报表（按最终分倒序）
     */
    List<Map<String, Object>> courseReport(Long courseId);
}
