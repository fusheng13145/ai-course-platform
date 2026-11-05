package com.aicourse.service.component.learning;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.vo.WrongQuestionGroupVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学习路径掌握度组件：从学生错题本推导知识点掌握度（0~100）。
 * <p>拆分自原 LearningPathServiceImpl（2026-08-29）。</p>
 */
@Component
public class LearningPathMasteryComponent {

    private static final Logger log = LoggerFactory.getLogger(LearningPathMasteryComponent.class);

    private final KnowledgePointService knowledgePointService;
    private final WrongQuestionService wrongQuestionService;

    public LearningPathMasteryComponent(KnowledgePointService knowledgePointService,
                                        WrongQuestionService wrongQuestionService) {
        this.knowledgePointService = knowledgePointService;
        this.wrongQuestionService = wrongQuestionService;
    }

    /**
     * 从学生错题本自动推导知识点掌握度（0~100）。
     * 只采用与课程知识图谱节点同名匹配的错题分组，并复用
     * {@link KnowledgePointService#calculateMastery(Long, Long)} 的统计算法，保证学习路径与
     * 知识点维度的掌握度口径一致。名称无法匹配图谱节点的分组直接跳过（记录 WARN），
     * 不产出兜底分数；这些知识点由调用方按默认值 50 处理并标记为“未评估”。
     *
     * @param studentId 学生ID
     * @param courseId  课程ID
     * @return 知识点名称 → 掌握度；无学情数据时返回空 Map
     */
    public Map<String, Double> deriveMasteryFromWrongQuestions(Long studentId, Long courseId) {
        Map<String, Double> mastery = new HashMap<>();
        try {
            // 修复（2026-09-23，P2-8）：改为结构化分组数组。
            // 旧实现拿到的 Map 键是知识点原始 JSON 文本（如 ["Python基础"]），
            // 与下方 pointByName 的纯名称键永远匹配不上 —— 使统一口径分支形同虚设、
            // 恒定走兜底公式。现结构中的名称已解析干净，可按名称正确匹配。
            List<WrongQuestionGroupVO> grouped =
                    wrongQuestionService.getByKnowledgePoints(studentId, courseId);
            if (grouped == null || grouped.isEmpty()) return mastery;

            // 建立课程内知识点名称 -> ID 映射，优先复用 KnowledgePointService.calculateMastery 的统计算法
            List<KnowledgePoint> coursePoints = knowledgePointService.list(
                    new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId));
            Map<String, KnowledgePoint> pointByName = coursePoints.stream()
                    .filter(p -> p.getName() != null && !p.getName().isBlank())
                    .collect(Collectors.toMap(KnowledgePoint::getName, p -> p, (a, b) -> a));

            int unmatched = 0;
            StringBuilder unmatchedSamples = new StringBuilder();
            for (WrongQuestionGroupVO group : grouped) {
                String kpName = group.getKnowledgePointName();
                if (kpName == null || kpName.isBlank()) continue;
                KnowledgePoint point = pointByName.get(kpName);
                if (point != null) {
                    // 与 KnowledgePointServiceImpl.calculateMastery 统一口径
                    BigDecimal rate = knowledgePointService.calculateMastery(point.getId(), studentId);
                    mastery.put(kpName, rate != null ? rate.doubleValue() : 50.0);
                } else {
                    // 名称无法映射到图谱节点：不写兜底分数（那会把“无依据”伪装成学情诊断）。
                    // 由调用方按未评估默认值处理，这里只留可排查的告警，样例最多 3 个避免刷屏。
                    unmatched++;
                    if (unmatched <= 3) {
                        if (unmatchedSamples.length() > 0) unmatchedSamples.append("、");
                        unmatchedSamples.append(kpName);
                    }
                }
            }
            if (unmatched > 0) {
                log.warn("错题分组中有 {} 个知识点未匹配课程图谱节点，按未评估处理（样例：{}）",
                        unmatched, unmatchedSamples);
            }
        } catch (Exception ignore) {
            log.warn("学习路径相关操作失败: {}", ignore.getMessage(), ignore);
            // 学情数据不可用时静默降级（返回空 Map，走标准路径）
        }
        return mastery;
    }
}
