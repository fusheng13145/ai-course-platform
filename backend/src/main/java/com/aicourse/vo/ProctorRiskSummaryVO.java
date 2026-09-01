package com.aicourse.vo;

import lombok.Data;

import java.util.Map;

/**
 * 考试风险汇总出参（GET /proctor/exam/{examSessionId}/risk）。
 * 字段名与历史 Map 键一致（sessionId / studentId / status / riskScore / riskLevel / eventCount / typeCounts）。
 * typeCounts 为动态键嵌套 Map（键=事件类型，值=次数），保留 Map 形态（同 VoteResultVO.counts 模式）。
 * 原实现无条件 put 此七键（恒含），故默认 INCLUDE.ALWAYS，精确复刻。
 */
@Data
public class ProctorRiskSummaryVO {

    private Long sessionId;
    private Long studentId;
    private String status;
    private Integer riskScore;
    private String riskLevel;
    private int eventCount;
    private Map<String, Long> typeCounts;

    public ProctorRiskSummaryVO() {
    }

    public ProctorRiskSummaryVO(Long sessionId, Long studentId, String status, Integer riskScore,
                                String riskLevel, int eventCount, Map<String, Long> typeCounts) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.status = status;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.eventCount = eventCount;
        this.typeCounts = typeCounts;
    }
}
