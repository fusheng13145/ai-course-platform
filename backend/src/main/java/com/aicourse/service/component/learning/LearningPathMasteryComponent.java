package com.aicourse.service.component.learning;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.WrongQuestionService;
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
     * 优先复用 {@link KnowledgePointService#calculateMastery(Long, Long)} 的统计算法，
     * 确保学习路径生成与知识点维度的掌握度口径一致；仅当知识点名称无法匹配课程内
     * 知识点时才回退到旧扣分公式。无错题的知识点不进入结果（由调用方按默认值 50 处理）。
     *
     * @param studentId 学生ID
     * @param courseId  课程ID
     * @return 知识点名称 → 掌握度；无学情数据时返回空 Map
     */
    public Map<String, Double> deriveMasteryFromWrongQuestions(Long studentId, Long courseId) {
        Map<String, Double> mastery = new HashMap<>();
        try {
            Map<String, List<WrongQuestion>> grouped =
                    wrongQuestionService.getByKnowledgePoints(studentId, courseId);
            if (grouped == null || grouped.isEmpty()) return mastery;

            // 建立课程内知识点名称 -> ID 映射，优先复用 KnowledgePointService.calculateMastery 的统计算法
            List<KnowledgePoint> coursePoints = knowledgePointService.list(
                    new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId));
            Map<String, KnowledgePoint> pointByName = coursePoints.stream()
                    .filter(p -> p.getName() != null && !p.getName().isBlank())
                    .collect(Collectors.toMap(KnowledgePoint::getName, p -> p, (a, b) -> a));

            for (Map.Entry<String, List<WrongQuestion>> e : grouped.entrySet()) {
                String kpName = e.getKey();
                if (kpName == null || kpName.isBlank()) continue;
                KnowledgePoint point = pointByName.get(kpName);
                if (point != null) {
                    // 与 KnowledgePointServiceImpl.calculateMastery 统一口径
                    BigDecimal rate = knowledgePointService.calculateMastery(point.getId(), studentId);
                    mastery.put(kpName, rate != null ? rate.doubleValue() : 50.0);
                } else {
                    // 知识点名称无法匹配时，使用兼容旧逻辑的兜底计算（保证不影响既有错题分组）
                    long unmastered = e.getValue().stream()
                            .filter(w -> w.getMastered() == null || !w.getMastered()).count();
                    long masteredCnt = e.getValue().size() - unmastered;
                    double score = 100.0 - unmastered * 25.0 - masteredCnt * 5.0;
                    mastery.put(kpName, Math.max(10.0, score));
                }
            }
        } catch (Exception ignore) {
            log.warn("学习路径相关操作失败: {}", ignore.getMessage(), ignore);
            // 学情数据不可用时静默降级（返回空 Map，走标准路径）
        }
        return mastery;
    }
}
