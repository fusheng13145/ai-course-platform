package com.aicourse.vo;

import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 学习路径完整详情出参（path + nodes + statusStats + 条件解析键）。
 * 字段名与历史 Map 键一致（path / nodes / statusStats / totalNodes /
 * stagesParsed / stagesRaw / planDetailParsed / planDetailRaw）。
 * stagesParsed 与 stagesRaw 互斥（stages JSON 解析成功出前者、失败出后者，stages 为空则二者均缺）；
 * planDetailParsed 与 planDetailRaw 同理。借 {@code @JsonInclude(NON_NULL)} 精确复刻
 * 原 Map「条件键缺失」行为——恒含四键值永不 null 不受影响，条件键 null 即不序列化，保证前端零改动。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LearningPathDetailVO {

    private LearningPath path;
    private List<LearningPathNode> nodes;
    private Map<String, Long> statusStats;
    private Integer totalNodes;
    private List<Map<String, Object>> stagesParsed;
    private String stagesRaw;
    private Map<String, Object> planDetailParsed;
    private String planDetailRaw;

    public LearningPathDetailVO() {
    }

    public LearningPathDetailVO(LearningPath path, List<LearningPathNode> nodes,
                                Map<String, Long> statusStats, Integer totalNodes,
                                List<Map<String, Object>> stagesParsed, String stagesRaw,
                                Map<String, Object> planDetailParsed, String planDetailRaw) {
        this.path = path;
        this.nodes = nodes;
        this.statusStats = statusStats;
        this.totalNodes = totalNodes;
        this.stagesParsed = stagesParsed;
        this.stagesRaw = stagesRaw;
        this.planDetailParsed = planDetailParsed;
        this.planDetailRaw = planDetailRaw;
    }
}
