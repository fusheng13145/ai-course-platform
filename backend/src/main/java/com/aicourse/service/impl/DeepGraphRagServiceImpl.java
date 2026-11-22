package com.aicourse.service.impl;

import com.aicourse.integration.ContractPaths;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.integration.deepgraph.DisabledDeepGraphRagProvider;
import com.aicourse.integration.deepgraph.DeepGraphRagContract;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.CourseGraphEdge;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.CourseGraphNode;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.DeepGraphRetrieveRequest;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.DeepGraphRetrievalResult;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.DeepGraphCourseGraphData;
import com.aicourse.integration.deepgraph.HttpDeepGraphRagProvider;
import com.aicourse.service.DeepGraphRagService;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * DeepGraph RAG 服务实现（门面）。
 * <p>
 * 按 {@code deepgraph-rag.enabled} 委派到契约实现：
 * <ul>
 *   <li>{@code true} → {@link HttpDeepGraphRagProvider}：HTTP 对接 Python deepgraph_rag 8501；</li>
 *   <li>{@code false}（默认）→ {@link DisabledDeepGraphRagProvider}：返回 503，问答链静默忽略，
 *       与未集成前的行为完全一致（该服务是<b>增量</b>检索源，不改变既有 8002 主链）。</li>
 * </ul>
 * 读超时默认 180s：章节图谱构建/生成走本地大模型时单次调用可能持续数十秒（Python 侧
 * {@code call_llm} 自身超时 180s），门面读超时必须覆盖之。
 * </p>
 */
@Service
@Slf4j
public class DeepGraphRagServiceImpl implements DeepGraphRagService {

    private static final ObjectMapper OM = new ObjectMapper();

    private final DeepGraphRagContract provider;
    private final KnowledgePointService knowledgePointService;
    private final KnowledgePointRelationService knowledgePointRelationService;

    @Autowired
    public DeepGraphRagServiceImpl(
            @Value("${deepgraph-rag.enabled:false}") boolean enabled,
            @Value("${deepgraph-rag.base-url:http://localhost:8501}") String baseUrl,
            @Value("${deepgraph-rag.connect-timeout:5}") int connectTimeout,
            @Value("${deepgraph-rag.read-timeout:180}") int readTimeout,
            @Value("${deepgraph-rag.max-retries:1}") int maxRetries,
            @Value("${deepgraph-rag.health-path:" + ContractPaths.DEEPGRAPH_HEALTH + "}") String healthPath,
            @Value("${app.internal.service-api-key:}") String internalApiKey,
            KnowledgePointService knowledgePointService,
            KnowledgePointRelationService knowledgePointRelationService) {
        this.provider = enabled
                ? new HttpDeepGraphRagProvider(baseUrl, connectTimeout, readTimeout, maxRetries, healthPath, internalApiKey)
                : new DisabledDeepGraphRagProvider();
        this.knowledgePointService = knowledgePointService;
        this.knowledgePointRelationService = knowledgePointRelationService;
        log.info("[DeepGraphRAG] engine={} baseUrl={}", enabled ? "python(8501)" : "disabled", enabled ? baseUrl : "-");
    }

    @Override
    public boolean healthCheck() {
        return provider.healthCheck().healthy();
    }

    @Override
    public String healthJson() {
        boolean enabled = !(provider instanceof DisabledDeepGraphRagProvider);
        boolean reachable = provider.healthCheck().healthy();
        return "{\"enabled\":" + enabled + ",\"reachable\":" + reachable + "}";
    }

    @Override
    public DeepGraphRetrievalResult retrieveTyped(String query, Long courseId, int topK) {
        return provider.retrieve(new DeepGraphRetrieveRequest(query, courseId, topK));
    }

    @Override
    public String retrieve(String query, Long courseId, int topK) {
        return serialize(provider.retrieve(new DeepGraphRetrieveRequest(query, courseId, topK)));
    }

    @Override
    public String ingest(Long courseId, String documentName, String content) {
        return serialize(provider.ingest(courseId, documentName, content));
    }

    @Override
    public String listDocuments(Long courseId) {
        return serialize(provider.listDocuments(courseId));
    }

    @Override
    public String deleteDocument(Long courseId, String documentName) {
        return serialize(provider.deleteDocument(courseId, documentName));
    }

    @Override
    public String chapterGraph(Long courseId, String documentName, String chapterName, String chapterText) {
        return serialize(provider.chapterGraph(courseId, documentName, chapterName, chapterText));
    }

    @Override
    public String learningPath(String target, Long courseId, int maxDepth, boolean searchAllSources) {
        return serialize(provider.learningPath(target, courseId, maxDepth, searchAllSources));
    }

    @Override
    public String syncCourseKnowledgeGraph(Long courseId, String documentName) {
        DeepGraphCourseGraphData data = provider.getCourseGraph(courseId, documentName);
        if (data == null || !ContractPaths.MODE_ENGINE.equals(data.mode())
                || data.nodes() == null || data.edges() == null) {
            return serialize(data);
        }
        // 幂等口径：实体按 (courseId, name) 查重；关系按 (sourceId, targetId, relationType) 查重
        java.util.Map<String, Long> nameToId = new java.util.LinkedHashMap<>();
        for (KnowledgePoint point : knowledgePointService.list(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId))) {
            nameToId.put(point.getName(), point.getId());
        }
        java.util.Set<String> existingTriples = new java.util.LinkedHashSet<>();
        java.util.Set<Long> pointIds = nameToId.values().isEmpty()
                ? java.util.Set.of() : new java.util.HashSet<>(nameToId.values());
        if (!pointIds.isEmpty()) {
            for (KnowledgePointRelation rel : knowledgePointRelationService.list(
                    new LambdaQueryWrapper<KnowledgePointRelation>()
                            .in(KnowledgePointRelation::getSourceId, pointIds)
                            .in(KnowledgePointRelation::getTargetId, pointIds))) {
                existingTriples.add(rel.getSourceId() + "|" + rel.getTargetId() + "|" + rel.getRelationType());
            }
        }

        java.util.List<KnowledgePoint> newPoints = new java.util.ArrayList<>();
        for (CourseGraphNode node : data.nodes()) {
            String name = node.name();
            if (name == null || name.isBlank() || nameToId.containsKey(name)) {
                continue;
            }
            KnowledgePoint point = new KnowledgePoint();
            point.setCourseId(courseId);
            point.setName(name.length() > 120 ? name.substring(0, 120) : name);
            point.setDescription(node.evidence() == null ? "" : node.evidence());
            point.setPointType("CHAPTER".equals(node.type()) ? "CHAPTER" : "CONCEPT");
            point.setImportance(3);
            newPoints.add(point);
        }
        if (!newPoints.isEmpty()) {
            knowledgePointService.saveBatch(newPoints);
            for (KnowledgePoint point : newPoints) {
                nameToId.put(point.getName(), point.getId());
            }
        }

        java.util.List<KnowledgePointRelation> newRelations = new java.util.ArrayList<>();
        int skippedRelations = 0;
        for (CourseGraphEdge edge : data.edges()) {
            Long sourceId = nameToId.get(edge.source());
            Long targetId = nameToId.get(edge.target());
            if (sourceId == null || targetId == null || sourceId.equals(targetId)) {
                skippedRelations++;
                continue;
            }
            String relationType = edge.relation() == null || edge.relation().isBlank()
                    ? "相关" : edge.relation();
            String triple = sourceId + "|" + targetId + "|" + relationType;
            if (existingTriples.contains(triple)) {
                skippedRelations++;
                continue;
            }
            existingTriples.add(triple);
            KnowledgePointRelation rel = new KnowledgePointRelation();
            rel.setSourceId(sourceId);
            rel.setTargetId(targetId);
            rel.setRelationType(relationType);
            rel.setDescription(edge.evidence() == null ? "" : edge.evidence());
            rel.setStrength(java.math.BigDecimal.ONE);
            newRelations.add(rel);
        }
        // 归属边：实体 → 所属章节（edge.chapter 为抽取首见章节名，归一化匹配 CHAPTER 节点，
        // 匹配不上则按章节名新建 CHAPTER 点）。层级是平台图谱页分层径向布局的依据。
        java.util.Map<String, Long> chapterIdByName = new java.util.LinkedHashMap<>();
        java.util.Map<String, Long> chapterIdByNormalized = new java.util.LinkedHashMap<>();
        for (KnowledgePoint point : knowledgePointService.list(
                new LambdaQueryWrapper<KnowledgePoint>()
                        .eq(KnowledgePoint::getCourseId, courseId)
                        .eq(KnowledgePoint::getPointType, "CHAPTER"))) {
            chapterIdByName.put(point.getName(), point.getId());
            chapterIdByNormalized.put(normalizeChapterName(point.getName()), point.getId());
        }
        int addedMembership = 0;
        for (CourseGraphEdge edge : data.edges()) {
            String chapterKey = edge.chapter();
            if (chapterKey == null || chapterKey.isBlank()) {
                continue;
            }
            // 章节骨架边（包含章节）的 chapter 字段是树根名，不是实体归属——跳过，
            // 否则全部章节子节点会被错误挂到「文档标题」这类占位根下
            if ("包含章节".equals(edge.relation())) {
                continue;
            }
            // 实体自环（章节键与实体同名等噪声）不参与归属
            if (edge.source() != null && edge.source().equals(chapterKey.trim())) {
                continue;
            }
            String trimmed = chapterKey.trim();
            Long chapterId = chapterIdByName.get(trimmed);
            if (chapterId == null) {
                chapterId = chapterIdByNormalized.get(normalizeChapterName(trimmed));
            }
            if (chapterId == null) {
                // 章号兜底：归档章节键（如「第1章1-10 分组交换时延…」）按章号挂到
                // 层次大纲里的同名号章节（「第1章 概述」），避免碎片键膨胀成孤立根章节
                java.util.regex.Matcher numbered =
                        java.util.regex.Pattern.compile("第\\s*(\\d+)\\s*章").matcher(trimmed);
                if (numbered.find()) {
                    String num = numbered.group(1);
                    for (java.util.Map.Entry<String, Long> entry : chapterIdByNormalized.entrySet()) {
                        java.util.regex.Matcher candidate =
                                java.util.regex.Pattern.compile("^第\\s*(\\d+)\\s*章").matcher(entry.getKey());
                        if (candidate.find() && candidate.group(1).equals(num)) {
                            chapterId = entry.getValue();
                            break;
                        }
                    }
                }
            }
            if (chapterId == null) {
                KnowledgePoint chapterPoint = new KnowledgePoint();
                chapterPoint.setCourseId(courseId);
                chapterPoint.setName(trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed);
                chapterPoint.setDescription("");
                chapterPoint.setPointType("CHAPTER");
                chapterPoint.setImportance(3);
                knowledgePointService.save(chapterPoint);
                chapterId = chapterPoint.getId();
                chapterIdByName.put(chapterPoint.getName(), chapterId);
                chapterIdByNormalized.put(normalizeChapterName(chapterPoint.getName()), chapterId);
                nameToId.put(chapterPoint.getName(), chapterId);
            }
            // 双端归属：三元组的主语与宾语实体都挂到本章——只挂主语会让仅作为宾语
            // 出现的实体没有归属边，最终全部堆在全景图最外环（2026-11-22 实测）
            for (String entityName : new String[] { edge.source(), edge.target() }) {
                if (entityName == null || entityName.isBlank()
                        || entityName.equals(chapterKey.trim())) {
                    continue;
                }
                Long entityId = nameToId.get(entityName);
                if (entityId == null || entityId.equals(chapterId)) {
                    continue;
                }
                String triple = entityId + "|" + chapterId + "|属于";
                if (existingTriples.contains(triple)) {
                    continue;
                }
                existingTriples.add(triple);
                KnowledgePointRelation rel = new KnowledgePointRelation();
                rel.setSourceId(entityId);
                rel.setTargetId(chapterId);
                rel.setRelationType("属于");
                rel.setDescription("");
                rel.setStrength(java.math.BigDecimal.ONE);
                newRelations.add(rel);
                addedMembership++;
            }
        }
        if (!newRelations.isEmpty()) {
            knowledgePointRelationService.saveBatch(newRelations);
        }
        return serialize(new SyncSummary(courseId, documentName,
                data.chapters() == null ? 0 : data.chapters().size(),
                data.nodes().size(), data.edges().size(),
                newPoints.size(), newRelations.size(), skippedRelations, addedMembership));
    }

    /** 同步摘要（camelCase 序列化，供前端/运维读取）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SyncSummary(Long courseId, String documentName, int chapters, int engineNodes,
                       int engineEdges, int addedPoints, int addedRelations, int skippedRelations,
                       int addedMembership) {
    }

    private static String normalizeChapterName(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "");
    }

    private String serialize(Object obj) {
        try {
            return OM.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("DeepGraph RAG 结果序列化失败：{}", e.getMessage());
            return "{\"error\":\"" + ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE
                    + "\",\"code\":" + ContractPaths.CODE_UNAVAILABLE + "}";
        }
    }
}
