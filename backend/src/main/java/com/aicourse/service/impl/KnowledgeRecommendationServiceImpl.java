package com.aicourse.service.impl;


import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.mapper.KnowledgePointRelationMapper;
import com.aicourse.service.KnowledgeRecommendationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于知识图谱关系的推荐引擎（纯算法，不依赖 LLM）。
 * <p>
 * 核心思路：利用 {@code knowledge_point_relations} 的 PREREQUISITE/INCLUDES 等关系，
 * 从学生薄弱点做 BFS 反向追溯（找前置依赖）和前向探索（找下一步学习目标）。
 */
@Service
@Slf4j
public class KnowledgeRecommendationServiceImpl implements KnowledgeRecommendationService {


    private final KnowledgePointMapper knowledgePointMapper;
    private final KnowledgePointRelationMapper relationMapper;

    public KnowledgeRecommendationServiceImpl(KnowledgePointMapper knowledgePointMapper,
                                               KnowledgePointRelationMapper relationMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
        this.relationMapper = relationMapper;
    }


    /** 知识点推荐相关操作 */
    @Override
    public List<Map<String, Object>> recommendFromWeakPoints(Long courseId,
                                                              Map<String, Double> weakPoints,
                                                              int limit) {
        if (weakPoints == null || weakPoints.isEmpty()) {
            return Collections.emptyList();
        }

        List<KnowledgePoint> allPoints = loadCoursePoints(courseId);
        if (allPoints.isEmpty()) return Collections.emptyList();

        Map<String, KnowledgePoint> nameMap = allPoints.stream()
                .collect(Collectors.toMap(p -> p.getName().toLowerCase(), p -> p, (a, b) -> a));

        // 构建前置索引：targetId → [sourceId]（即从目标找前置依赖）
        List<KnowledgePointRelation> allRelations = loadCourseRelations(allPoints);
        Map<Long, List<KnowledgePointRelation>> prereqIndex = new HashMap<>(); // targetId → prerequisites
        Map<Long, List<KnowledgePointRelation>> relatedIndex = new HashMap<>(); // sourceId → related
        for (KnowledgePointRelation rel : allRelations) {
            String type = rel.getRelationType() != null ? rel.getRelationType().toUpperCase() : "";
            if (type.contains("PREREQUISITE") || type.contains("DEPENDS") || type.contains("前驱")) {
                prereqIndex.computeIfAbsent(rel.getTargetId(), k -> new ArrayList<>()).add(rel);
            } else {
                relatedIndex.computeIfAbsent(rel.getSourceId(), k -> new ArrayList<>()).add(rel);
            }
        }

        Map<Long, KnowledgePoint> idMap = allPoints.stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, p -> p, (a, b) -> a));

        List<Map<String, Object>> recommendations = new ArrayList<>();
        Set<Long> recommendedIds = new HashSet<>();

        for (Map.Entry<String, Double> entry : weakPoints.entrySet()) {
            KnowledgePoint weakPt = nameMap.get(entry.getKey().toLowerCase());
            if (weakPt == null) continue;

            double mastery = entry.getValue();
            double urgency = (100.0 - mastery) / 100.0; // 越弱 urgency 越高

            // 1. 查找前置依赖：需先复习这些才能学薄弱点
            List<KnowledgePointRelation> prereqs = prereqIndex.getOrDefault(weakPt.getId(), Collections.emptyList());
            for (KnowledgePointRelation rel : prereqs) {
                Long prereqId = rel.getSourceId();
                if (recommendedIds.add(prereqId)) {
                    KnowledgePoint prereq = idMap.get(prereqId);
                    if (prereq != null) {
                        double weight = urgency * 1.2; // 前置依赖权重略高
                        recommendations.add(buildEntry(prereq, "前置复习", weight,
                                "学习「" + weakPt.getName() + "」前需先掌握"));
                    }
                }
            }

            // 2. 查找关联知识点：同属一个知识簇
            List<KnowledgePointRelation> related = relatedIndex.getOrDefault(weakPt.getId(), Collections.emptyList());
            for (KnowledgePointRelation rel : related) {
                Long relatedId = rel.getTargetId();
                if (recommendedIds.add(relatedId)) {
                    KnowledgePoint relatedPt = idMap.get(relatedId);
                    if (relatedPt != null) {
                        double weight = urgency * 1.0;
                        recommendations.add(buildEntry(relatedPt, "关联补充", weight,
                                "与「" + weakPt.getName() + "」紧密相关"));
                    }
                }
            }
        }

        // 按权重降序排列
        recommendations.sort((a, b) -> Double.compare(
                (Double) b.get("weight"), (Double) a.get("weight")));
        return recommendations.subList(0, Math.min(limit, recommendations.size()));
    }


    /** 知识点推荐相关操作 */
    @Override
    public List<Map<String, Object>> recommendNextSteps(Long courseId,
                                                         Map<String, Double> masteredPoints,
                                                         int limit) {
        if (masteredPoints == null || masteredPoints.isEmpty()) {
            return Collections.emptyList();
        }

        List<KnowledgePoint> allPoints = loadCoursePoints(courseId);
        if (allPoints.isEmpty()) return Collections.emptyList();

        Map<String, KnowledgePoint> nameMap = allPoints.stream()
                .collect(Collectors.toMap(p -> p.getName().toLowerCase(), p -> p, (a, b) -> a));

        // 构建后继索引：sourceId → [targetId]（即前置 → 后置）
        List<KnowledgePointRelation> allRelations = loadCourseRelations(allPoints);
        Map<Long, List<KnowledgePointRelation>> successorIndex = new HashMap<>();
        for (KnowledgePointRelation rel : allRelations) {
            String type = rel.getRelationType() != null ? rel.getRelationType().toUpperCase() : "";
            if (type.contains("PREREQUISITE") || type.contains("DEPENDS") || type.contains("前驱")) {
                successorIndex.computeIfAbsent(rel.getSourceId(), k -> new ArrayList<>()).add(rel);
            }
        }

        Map<Long, KnowledgePoint> idMap = allPoints.stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, p -> p, (a, b) -> a));

        Set<Long> alreadyMastered = masteredPoints.keySet().stream()
                .map(k -> {
                    KnowledgePoint p = nameMap.get(k.toLowerCase());
                    return p != null ? p.getId() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Map<String, Object>> recommendations = new ArrayList<>();
        Set<Long> recommendedIds = new HashSet<>();

        for (Map.Entry<String, Double> entry : masteredPoints.entrySet()) {
            KnowledgePoint masterPt = nameMap.get(entry.getKey().toLowerCase());
            if (masterPt == null) continue;

            double mastery = entry.getValue();
            if (mastery < 60) continue; // 未充分掌握，不推荐下一步

            List<KnowledgePointRelation> successors = successorIndex.getOrDefault(masterPt.getId(),
                    Collections.emptyList());
            for (KnowledgePointRelation rel : successors) {
                Long nextId = rel.getTargetId();
                if (alreadyMastered.contains(nextId)) continue; // 已掌握，跳过
                if (recommendedIds.add(nextId)) {
                    KnowledgePoint nextPt = idMap.get(nextId);
                    if (nextPt != null) {
                        double confidence = Math.min(mastery / 100.0, 1.0);
                        recommendations.add(buildEntry(nextPt, "下一步学习", confidence,
                                "已掌握「" + masterPt.getName() + "」，可继续学习"));
                    }
                }
            }
        }

        recommendations.sort((a, b) -> Double.compare(
                (Double) b.get("weight"), (Double) a.get("weight")));
        return recommendations.subList(0, Math.min(limit, recommendations.size()));
    }


    /** 知识点推荐相关操作 */
    @Override
    public List<Map<String, Object>> recommendComprehensive(Long courseId,
                                                              Map<String, Double> weakPoints,
                                                              Map<String, Double> masteredPoints,
                                                              int limit) {
        List<Map<String, Object>> result = new ArrayList<>();

        // 1. 薄弱点修复（优先级最高）
        if (weakPoints != null && !weakPoints.isEmpty()) {
            result.addAll(recommendFromWeakPoints(courseId, weakPoints,
                    (int) Math.ceil(limit * 0.6)));
        }

        // 2. 下一步学习
        if (masteredPoints != null && !masteredPoints.isEmpty()) {
            Set<String> existingNames = result.stream()
                    .map(m -> (String) m.get("name"))
                    .collect(Collectors.toSet());
            List<Map<String, Object>> next = recommendNextSteps(courseId, masteredPoints,
                    limit - result.size());
            for (Map<String, Object> item : next) {
                if (!existingNames.contains(item.get("name"))) {
                    result.add(item);
                }
            }
        }

        // 3. 全局排序并截断
        result.sort((a, b) -> Double.compare(
                (Double) b.get("weight"), (Double) a.get("weight")));
        return result.subList(0, Math.min(limit, result.size()));
    }

    // ===================== 工具 =====================

    private List<KnowledgePoint> loadCoursePoints(Long courseId) {
        return knowledgePointMapper.selectList(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId));
    }

    private List<KnowledgePointRelation> loadCourseRelations(List<KnowledgePoint> points) {
        Set<Long> ids = points.stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyList();
        return relationMapper.selectList(
                new LambdaQueryWrapper<KnowledgePointRelation>()
                        .and(w -> w.in(KnowledgePointRelation::getSourceId, ids)
                                .or().in(KnowledgePointRelation::getTargetId, ids)));
    }

    private Map<String, Object> buildEntry(KnowledgePoint point, String recommendType,
                                           double weight, String reason) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", point.getId());
        entry.put("name", point.getName());
        entry.put("description", point.getDescription());
        entry.put("difficultyLevel", point.getDifficultyLevel());
        entry.put("recommendType", recommendType);
        entry.put("weight", Math.round(weight * 1000.0) / 1000.0);
        entry.put("reason", reason);
        return entry;
    }
}
