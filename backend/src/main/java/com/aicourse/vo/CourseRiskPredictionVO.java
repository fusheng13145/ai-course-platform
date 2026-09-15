package com.aicourse.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程级学情风险预测视图对象（P2 预测性学情预警）。
 * <p>包含汇总（高/中/低风险人数与总数）与各学生的风险明细列表，按风险分降序排列。</p>
 */
@Data
public class CourseRiskPredictionVO {

    /** 课程ID */
    private Long courseId;

    /** 预测生成时间 */
    private LocalDateTime generatedAt;

    /** 高风险学生数 */
    private int highCount;

    /** 中风险学生数 */
    private int midCount;

    /** 低风险学生数 */
    private int lowCount;

    /** 参与预测的学生总数（仅统计有效在修） */
    private int total;

    /** 各学生风险明细（按风险分降序） */
    private List<StudentRiskVO> students;
}
