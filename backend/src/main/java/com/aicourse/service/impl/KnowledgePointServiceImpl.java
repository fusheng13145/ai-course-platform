package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.component.learning.KnowledgeMasteryComponent;
import com.aicourse.vo.KnowledgeTreeNodeVO;
import com.aicourse.vo.MasteryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import com.aicourse.common.ApiCode;
import com.aicourse.common.JsonUtils;
import com.aicourse.domain.knowledge.PointType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 知识点服务实现类
 * 提供知识树构建、父子关系查询、前置依赖、掌握度计算等功能实现
 */
@Service
public class KnowledgePointServiceImpl extends ServiceImpl<KnowledgePointMapper, KnowledgePoint> implements KnowledgePointService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgePointServiceImpl.class);

    private static final String CACHE_NAME = "knowledgePoints";

    private final KnowledgePointRelationService relationService;
    private final KnowledgeMasteryComponent masteryComponent;

    public KnowledgePointServiceImpl(@Lazy KnowledgePointRelationService relationService,
                                     KnowledgeMasteryComponent masteryComponent) {
        this.relationService = relationService;
        this.masteryComponent = masteryComponent;
    }

    /**
     * 获取课程的完整知识树（递归构建层级结构）
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'tree:' + #courseId")
    public List<KnowledgeTreeNodeVO> getKnowledgeTree(Long courseId) {
        // 查询课程下所有知识点
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId)
                .orderByAsc(KnowledgePoint::getOrderIndex);
        List<KnowledgePoint> allPoints = list(wrapper);

        // 按ID建立Map便于查找
        Map<Long, KnowledgePoint> pointMap = allPoints.stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, p -> p, (a, b) -> a));

        // 找出根节点（parentId为null）
        List<KnowledgePoint> rootPoints = allPoints.stream()
                .filter(p -> p.getParentId() == null)
                .collect(Collectors.toList());

        // 递归构建树
        List<KnowledgeTreeNodeVO> tree = new ArrayList<>();
        for (KnowledgePoint root : rootPoints) {
            tree.add(buildTreeNode(root, pointMap));
        }
        return tree;
    }

    /**
     * 递归构建树形节点
     */
    private KnowledgeTreeNodeVO buildTreeNode(KnowledgePoint point, Map<Long, KnowledgePoint> pointMap) {
        List<KnowledgeTreeNodeVO> children = new ArrayList<>();
        for (KnowledgePoint p : pointMap.values()) {
            if (point.getId().equals(p.getParentId())) {
                children.add(buildTreeNode(p, pointMap));
            }
        }
        return new KnowledgeTreeNodeVO(
                point.getId(), point.getName(), point.getDescription(),
                point.getDifficultyLevel(), point.getImportance(),
                point.getEstimatedTime(), point.getPointType(),
                point.getOrderIndex(), children);
    }

    /**
     * 获取某知识点的直接子节点（按orderIndex排序）
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'children:' + #parentId")
    public List<KnowledgePoint> getChildPoints(Long parentId) {
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getParentId, parentId)
                .orderByAsc(KnowledgePoint::getOrderIndex);
        return list(wrapper);
    }

    /**
     * 获取知识点的前置依赖（通过关联关系中relationType=PREREQUISITE查询）
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'prerequisites:' + #pointId")
    public List<KnowledgePoint> getPrerequisites(Long pointId) {
        // 查询以当前知识点为target且类型为PREREQUISITE的关联关系
        // 修复（2026-10-30，B-13 ⑪）：改为大小写不敏感比较。存量里可能存在小写
        // "prerequisite" 的行（历史写入未归一化），精确匹配会让这些边查不回来。
        LambdaQueryWrapper<KnowledgePointRelation> wrapper = new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getTargetId, pointId)
                .apply("UPPER(relation_type) = {0}", "PREREQUISITE");
        List<KnowledgePointRelation> relations = relationService.list(wrapper);

        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查出前置知识点
        List<Long> sourceIds = relations.stream()
                .map(KnowledgePointRelation::getSourceId)
                .collect(Collectors.toList());
        return listByIds(sourceIds);
    }

    /**
     * 获取知识点的后续知识点（被谁作为前置依赖）
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'subsequent:' + #pointId")
    public List<KnowledgePoint> getSubsequentPoints(Long pointId) {
        // 查询以当前知识点为source且类型为PREREQUISITE的关联关系
        // 修复（2026-10-30，B-13 ⑪）：同 getPrerequisites，大小写不敏感。
        LambdaQueryWrapper<KnowledgePointRelation> wrapper = new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getSourceId, pointId)
                .apply("UPPER(relation_type) = {0}", "PREREQUISITE");
        List<KnowledgePointRelation> relations = relationService.list(wrapper);

        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查出后续知识点
        List<Long> targetIds = relations.stream()
                .map(KnowledgePointRelation::getTargetId)
                .collect(Collectors.toList());
        return listByIds(targetIds);
    }

    /**
     * 创建知识点（校验difficultyLevel和importance的取值范围）
     * difficultyLevel: EASY/MEDIUM/HARD
     * importance: 1-5
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public KnowledgePoint createWithValidation(KnowledgePoint knowledgePoint) {
        log.info("创建知识点点");

        // 校验必填字段
        if (knowledgePoint.getName() == null || knowledgePoint.getName().trim().isEmpty()) {
            throw new BusinessException("知识点名称不能为空");
        }
        if (knowledgePoint.getCourseId() == null) {
            throw new BusinessException("必须指定所属课程");
        }

        // 校验难度等级（如果提供了）
        if (knowledgePoint.getDifficultyLevel() != null && !knowledgePoint.getDifficultyLevel().isEmpty()) {
            String level = knowledgePoint.getDifficultyLevel().toUpperCase();
            if (!"EASY".equals(level) && !"MEDIUM".equals(level) && !"HARD".equals(level)) {
                throw new BusinessException("难度等级必须是EASY、MEDIUM或HARD");
            }
            knowledgePoint.setDifficultyLevel(level);
        }

        // 校验重要度（1-5）
        if (knowledgePoint.getImportance() != null) {
            int importance = knowledgePoint.getImportance();
            if (importance < 1 || importance > 5) {
                throw new BusinessException("重要度必须在1-5之间");
            }
        } else {
            // 默认设置为3
            knowledgePoint.setImportance(3);
        }

        this.save(knowledgePoint);
        return knowledgePoint;
    }

    /**
     * 获取知识点列表（支持按难度等级和重要度排序/筛选）
     * getTree()时将importance映射到节点大小
     */
    @Override
    public List<KnowledgePoint> getListWithFilters(Long courseId, String difficultyLevel,
                                                     Integer minImportance, Integer maxImportance,
                                                     String sortBy, boolean isAsc) {
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId);

        // 按难度等级筛选
        if (difficultyLevel != null && !difficultyLevel.isEmpty()) {
            wrapper.eq(KnowledgePoint::getDifficultyLevel, difficultyLevel.toUpperCase());
        }

        // 按重要度范围筛选
        if (minImportance != null) {
            wrapper.ge(KnowledgePoint::getImportance, minImportance);
        }
        if (maxImportance != null) {
            wrapper.le(KnowledgePoint::getImportance, maxImportance);
        }

        // 排序
        if ("importance".equals(sortBy)) {
            if (isAsc) {
                wrapper.orderByAsc(KnowledgePoint::getImportance);
            } else {
                wrapper.orderByDesc(KnowledgePoint::getImportance);
            }
        } else if ("difficultyLevel".equals(sortBy)) {
            if (isAsc) {
                wrapper.orderByAsc(KnowledgePoint::getDifficultyLevel);
            } else {
                wrapper.orderByDesc(KnowledgePoint::getDifficultyLevel);
            }
        } else {
            // 默认按orderIndex排序
            wrapper.orderByAsc(KnowledgePoint::getOrderIndex);
        }

        return list(wrapper);
    }

    /**
     * 计算知识点掌握度（内部兼容入口，返回百分比；无真实作答证据时返回 null）。
     * <p>业务与接口应优先使用 {@link #calculateMasteryDetail(Long, Long)}，以便前端区分
     * “0 分”和“未评估”。</p>
     */
    @Override
    public BigDecimal calculateMastery(Long pointId, Long studentId) {
        MasteryVO detail = calculateMasteryDetail(pointId, studentId);
        return detail == null ? null : detail.getScore();
    }

    /**
     * 计算知识点掌握度明细（2026-11-16 P0-4）：按课程题库 + 最近考试得分 + 错题本
     * 的真实证据计算，不再“无错题=100”，也不再跨课程统计同名题目。
     */
    @Override
    public MasteryVO calculateMasteryDetail(Long pointId, Long studentId) {
        KnowledgePoint point = this.getById(pointId);
        if (point == null) {
            return MasteryVO.unknown(0, "知识点不存在");
        }
        return masteryComponent.evaluate(point, studentId);
    }

    /**
     * 获取课程的根级知识点（parentId为null）
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'roots:' + #courseId")
    public List<KnowledgePoint> getRootPoints(Long courseId) {
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId)
                .isNull(KnowledgePoint::getParentId)
                .orderByAsc(KnowledgePoint::getOrderIndex);
        return list(wrapper);
    }

    // ===== IService 写操作覆写，添加缓存失效 =====

    @Override
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public boolean save(KnowledgePoint entity) {
        log.info("保存知识点点");

        return super.save(entity);
    }


    /** 更新知识点点 */
    @Override
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public boolean updateById(KnowledgePoint entity) {
        log.info("更新知识点点");

        return super.updateById(entity);
    }


    /** 移除知识点点 */
    @Override
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public boolean removeById(Serializable id) {
        log.info("移除知识点点");

        return super.removeById(id);
    }

    @Override
    public long countByCourse(Long courseId) {
        if (courseId == null) {
            return 0L;
        }
        return count(new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId));
    }

    @Override
    public List<KnowledgePoint> listByCourse(Long courseId) {
        if (courseId == null) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId)
                .orderByAsc(KnowledgePoint::getOrderIndex));
    }

    // ===== AI 抽取落库与掌握度快照（2026-11-14 B3） =====

    private static final ObjectMapper EXTRACT_OM = new ObjectMapper();

    /** 抽取落库的知识点类型白名单：由 PointType 统一维护，未知值回落 CONCEPT。 */
    private static final Set<String> KP_TYPES = Set.of(
            PointType.CONCEPT.code(), PointType.PRINCIPLE.code(),
            PointType.METHOD.code(), PointType.FACT.code());

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public Map<String, Object> extractAndSaveGraph(Long courseId, String rawJson) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<KnowledgePoint> created = new ArrayList<>();
        Map<String, Long> nameToId = new HashMap<>();
        // 既有知识点按名建索引：同名跳过（幂等），抽取结果挂到既有节点上
        for (KnowledgePoint ep : list(new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId))) {
            if (ep.getName() != null) {
                nameToId.put(ep.getName().trim().toLowerCase(), ep.getId());
            }
        }

        String raw = JsonUtils.extractJsonObject(rawJson);
        if (raw == null) {
            // 2026-11-19 错误处理收口（DR-74）：AI 调用失败必须如实报错，不得以 parseError 假 200；
            // 抛 503 业务异常由事务整体回滚，保证不落任何半成品节点
            throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE, "AI 未返回有效结构，请稍后重试");
        }

        List<Map<String, Object>> createdRelations = new ArrayList<>();
        try {
            JsonNode root = EXTRACT_OM.readTree(raw);
            if (root.has("nodes") && root.get("nodes").isArray()) {
                for (JsonNode n : root.get("nodes")) {
                    String name = n.has("name") ? n.get("name").asText() : null;
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    String key = name.trim().toLowerCase();
                    if (nameToId.containsKey(key)) {
                        continue;
                    }
                    String content = n.has("content") ? n.get("content").asText() : "";
                    String type = n.has("type") ? PointType.canonicalCode(n.get("type").asText()) : "CONCEPT";
                    if (!KP_TYPES.contains(type)) {
                        type = PointType.CONCEPT.code();
                    }
                    KnowledgePoint kp = new KnowledgePoint();
                    kp.setCourseId(courseId);
                    kp.setName(name.trim());
                    kp.setDescription(content);
                    kp.setPointType(type);
                    save(kp);
                    created.add(kp);
                    nameToId.put(key, kp.getId());
                }
            }
            if (root.has("relations") && root.get("relations").isArray()) {
                for (JsonNode r : root.get("relations")) {
                    String sName = r.has("source") ? r.get("source").asText() : null;
                    String tName = r.has("target") ? r.get("target").asText() : null;
                    String rel = r.has("relationType") ? r.get("relationType").asText().toUpperCase() : "RELATED_TO";
                    Long sId = sName != null ? nameToId.get(sName.trim().toLowerCase()) : null;
                    Long tId = tName != null ? nameToId.get(tName.trim().toLowerCase()) : null;
                    if (sId != null && tId != null && !sId.equals(tId)) {
                        try {
                            KnowledgePointRelation relEntity = relationService.createRelation(sId, tId, rel, null, null);
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id", relEntity.getId());
                            m.put("source", sName);
                            m.put("target", tName);
                            m.put("relationType", rel);
                            createdRelations.add(m);
                        } catch (Exception ex) {
                            log.debug("跳过关系 {} -> {}: {}", sName, tName, ex.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 2026-11-19 错误处理收口（DR-74）：解析失败抛 503 业务异常，事务整体回滚，
            // 不再出现「部分节点已写 + parseError 假 200」的半成品图谱
            throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE, "AI 返回结构解析失败，请稍后重试", e);
        }

        result.put("nodeCount", created.size());
        result.put("nodes", created);
        result.put("relationCount", createdRelations.size());
        result.put("relations", createdRelations);
        return result;
    }

    @Override
    public List<Map<String, Object>> masterySnapshot(Long studentId, List<Long> courseIds, int limit) {
        if (courseIds == null || courseIds.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<KnowledgePoint> points = list(new LambdaQueryWrapper<KnowledgePoint>()
                .in(KnowledgePoint::getCourseId, courseIds)
                .orderByDesc(KnowledgePoint::getImportance));
        if (points.isEmpty()) {
            return List.of();
        }

        // 2026-11-16 P0-4：与单点掌握度共用同一套证据口径；未作答/无证据的点不进入
        // “薄弱点”列表，避免旧实现把“无错题”当成 100 或把缺失数据当成 0 的假信号。
        Map<Long, MasteryVO> evaluated = masteryComponent.evaluate(points, studentId);
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (KnowledgePoint point : points) {
            MasteryVO vo = evaluated.get(point.getId());
            if (vo == null || !vo.isAnswered() || vo.getScore() == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("courseId", point.getCourseId());
            row.put("kpId", point.getId());
            row.put("name", point.getName());
            row.put("mastery", vo.getScore());
            row.put("answeredQuestions", vo.getAnsweredQuestions());
            row.put("totalQuestions", vo.getTotalQuestions());
            snapshot.add(row);
        }
        snapshot.sort(Comparator.comparingDouble(m -> ((Number) m.get("mastery")).doubleValue()));
        return snapshot.subList(0, Math.min(limit, snapshot.size()));
    }
}
