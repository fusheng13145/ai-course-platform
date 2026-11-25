package com.aicourse.service.component.ai;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.integration.ContractPaths;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.DeepGraphRetrievalResult;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.DeepGraphSource;
import com.aicourse.integration.rag.RagContract.RagRetrievalResult;
import com.aicourse.integration.rag.RagContract.RagSource;
import com.aicourse.service.DeepGraphRagService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.RAGService;
import com.aicourse.vo.QaSourceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 课程证据组件：把「课程资料召回 + 知识图谱知识点」这一层证据获取与渲染逻辑从问答组件中抽出，
 * 供问答、出题、批改等需要「依据课程内容」的场景复用，避免各处重复实现同一套匹配与截断口径。
 * <p>证据口径与 {@code AiRagComponent} 原有实现逐字一致：只有 {@code mode=ENGINE} 的检索结果才算
 * 真实引擎证据；知识点匹配要求名称至少 2 字且整体出现在归一化后的问题中；描述压缩后截断
 * {@value #MAX_DESCRIPTION_LEN} 字。所有外部查询失败一律降级为空证据，绝不阻断上层主流程。</p>
 * <p><b>双引擎合并（2026-11-21 DeepGraph RAG 接入）</b>：知识点语料 RAG（原 8002，现与
 * DeepGraph 同进程）为主引擎，DeepGraph 文档语料为整篇文档的<b>增量</b>第二检索源；
 * {@link #retrieveWithDocuments} / {@link #retrieveMergedTyped} 把两路 ENGINE 证据去重合并。
 * 第二引擎未启用/不可达/异常时静默跳过，只回落到知识点语料单引擎——与未集成前行为完全一致。</p>
 */
@Slf4j
@Component
public class CourseEvidenceComponent {

    /** 无可用课程证据时对外的如实答复措辞，各调用方共用同一句话。 */
    public static final String NO_COURSE_KB_ANSWER = "无法基于课程知识库回答";

    /** 问题命中的知识点数量上限。 */
    private static final int MAX_MATCHED_POINTS = 5;
    /** 课程范围知识点数量上限。 */
    private static final int MAX_COURSE_POINTS = 6;
    /** 单个知识点的前置知识数量上限。 */
    private static final int MAX_PREREQUISITES = 3;
    /** 注入 prompt 的知识点描述最大字符数。 */
    private static final int MAX_DESCRIPTION_LEN = 200;
    /** 第二引擎（deepgraph_rag）单次合并进证据的条数上限，防止喧宾夺主。 */
    private static final int MAX_DEEPGRAPH_SOURCES = 5;

    private final RAGService ragService;
    private final KnowledgePointService knowledgePointService;
    private final DeepGraphRagService deepGraphRagService;

    /** Spring 注入口（含第二检索源）。 */
    @Autowired
    public CourseEvidenceComponent(RAGService ragService, KnowledgePointService knowledgePointService,
                                   DeepGraphRagService deepGraphRagService) {
        this.ragService = ragService;
        this.knowledgePointService = knowledgePointService;
        this.deepGraphRagService = deepGraphRagService;
    }

    /** 兼容旧签名（既有单测直构）：无第二检索源，行为与未集成 DeepGraph 前一致。 */
    public CourseEvidenceComponent(RAGService ragService, KnowledgePointService knowledgePointService) {
        this(ragService, knowledgePointService, null);
    }

    /**
     * 调引擎检索课程资料：只有 {@code mode=ENGINE}（且未被降级）的应答才算证据。
     * <p>未指定课程、问题为空、引擎不可用、召回为 null 或调用异常一律返回空列表，由调用方降级。</p>
     */
    public List<RagSource> retrieve(String query, Long courseId, int topK) {
        if (courseId == null || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            RagRetrievalResult result = ragService.retrieveTyped(query, courseId, topK);
            if (result == null || !result.engineReady()) {
                return List.of();
            }
            List<RagSource> results = result.results();
            return results == null ? List.of() : results;
        } catch (Exception e) {
            log.warn("RAG 检索失败，降级为知识点接地问答: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 双引擎合并检索（问答主链入口）：知识点语料（原 8002）+ DeepGraph 整篇文档语料。
     * <p>两路各自的 {@code mode=ENGINE} 结果按片段归一化去重后拼接（知识点语料在前，deepgraph 补充其后，
     * 条数上限 {@value #MAX_DEEPGRAPH_SOURCES}）。deepgraph 未启用/不可达/异常时静默跳过。</p>
     */
    public List<RagSource> retrieveWithDocuments(String query, Long courseId, int topK) {
        List<RagSource> primary = retrieve(query, courseId, topK);
        List<RagSource> deepgraph = deepgraphSources(query, courseId, topK, primary);
        if (deepgraph.isEmpty()) {
            return primary;
        }
        List<RagSource> merged = new ArrayList<>(primary.size() + deepgraph.size());
        merged.addAll(primary);
        merged.addAll(deepgraph);
        return merged;
    }

    /**
     * 双引擎合并检索的类型化结果（流式问答用）：供 {@code AiCoreComponent.toQaSources} 判定
     * {@code engineReady()}。两路均无 ENGINE 证据时原样透传主引擎应答（保持既有
     * UNAVAILABLE/PLACEHOLDER 降级语义不被掩盖）；任一路有证据则返回 {@code mode=ENGINE} 的合并结果。
     */
    public RagRetrievalResult retrieveMergedTyped(String query, Long courseId, int topK) {
        RagRetrievalResult primary;
        try {
            primary = courseId == null || query == null || query.isBlank()
                    ? null : ragService.retrieveTyped(query, courseId, topK);
        } catch (Exception e) {
            log.warn("RAG 检索失败（流式主引擎），继续尝试 DeepGraph 检索: {}", e.getMessage());
            primary = null;
        }
        List<RagSource> primaryResults = primary != null && primary.engineReady() && primary.results() != null
                ? primary.results() : List.of();
        List<RagSource> deepgraph = deepgraphSources(query, courseId, topK, primaryResults);
        if (primaryResults.isEmpty() && deepgraph.isEmpty()) {
            // 主引擎应答原样透传（可能为 null/UNAVAILABLE/ENGINE 空命中），不得伪造 ENGINE
            return primary != null ? primary : new RagRetrievalResult(null,
                    ContractPaths.MODE_UNAVAILABLE, ContractPaths.MSG_RAG_UNAVAILABLE,
                    ContractPaths.CODE_UNAVAILABLE);
        }
        List<RagSource> merged = new ArrayList<>(primaryResults.size() + deepgraph.size());
        merged.addAll(primaryResults);
        merged.addAll(deepgraph);
        return new RagRetrievalResult(merged, ContractPaths.MODE_ENGINE, null, null);
    }

    /** 调 8501 deepgraph_rag 检索并转成 {@link RagSource}（含去重与上限），异常/未启用一律空列表。 */
    private List<RagSource> deepgraphSources(String query, Long courseId, int topK,
                                             List<RagSource> existing) {
        if (deepGraphRagService == null || courseId == null
                || query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        try {
            DeepGraphRetrievalResult result = deepGraphRagService.retrieveTyped(query, courseId, topK);
            if (result == null || !result.engineReady() || result.results() == null) {
                return List.of();
            }
            Set<String> seen = new LinkedHashSet<>();
            if (existing != null) {
                for (RagSource source : existing) {
                    if (source != null && source.snippet() != null) {
                        seen.add(normalize(source.snippet()));
                    }
                }
            }
            List<RagSource> converted = new ArrayList<>();
            for (DeepGraphSource source : result.results()) {
                if (source == null || source.snippet() == null || source.snippet().isBlank()) {
                    continue;
                }
                String key = normalize(source.snippet());
                if (seen.contains(key)) {
                    continue;
                }
                seen.add(key);
                converted.add(new RagSource(source.documentId(), source.documentName(),
                        source.relevanceScore(), source.snippet()));
                if (converted.size() >= Math.min(topK, MAX_DEEPGRAPH_SOURCES)) {
                    break;
                }
            }
            return converted;
        } catch (Exception e) {
            log.warn("DeepGraph RAG 检索失败，忽略第二检索源: {}", e.getMessage());
            return List.of();
        }
    }

    /** 读取课程知识点；读取失败不阻断上层流程，降级为无知识库。 */
    public List<KnowledgePoint> loadPoints(Long courseId) {
        if (courseId == null) {
            return List.of();
        }
        try {
            List<KnowledgePoint> points = knowledgePointService.listByCourse(courseId);
            return points == null ? List.of() : points;
        } catch (Exception e) {
            log.warn("读取课程知识点失败，降级为无知识库问答: {}", e.getMessage());
            return List.of();
        }
    }

    /** 取问题命中的知识点（名称至少 2 字且整体出现在问题中），按重要度排序后限量。 */
    public List<KnowledgePoint> matchPoints(List<KnowledgePoint> points, String query) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        String normalizedQuestion = normalize(query);
        return points.stream()
                .filter(p -> p.getName() != null && !p.getName().isBlank())
                .filter(p -> matchesQuestion(normalizedQuestion, p.getName()))
                .sorted(pointPriority())
                .limit(MAX_MATCHED_POINTS)
                .toList();
    }

    /** 把知识点（含图谱前置关系）渲染为 prompt 证据段。 */
    public String renderPoints(List<KnowledgePoint> matched) {
        StringBuilder context = new StringBuilder();
        for (KnowledgePoint point : matched) {
            context.append("- ").append(point.getName());
            String description = cleanDescription(point.getDescription());
            if (!description.isEmpty()) {
                context.append("：").append(description);
            }
            context.append('\n');
            List<KnowledgePoint> prerequisites = prerequisites(point.getId());
            if (!prerequisites.isEmpty()) {
                context.append("  前置知识：")
                        .append(prerequisites.stream().map(KnowledgePoint::getName)
                                .filter(n -> n != null && !n.isBlank())
                                .distinct()
                                .reduce((a, b) -> a + "、" + b)
                                .orElse(""))
                        .append('\n');
            }
        }
        return context.toString();
    }

    /** 把引擎召回资料逐条渲染为 prompt 证据段（{@code [序号] 文档名：片段}）。 */
    public String renderSources(List<RagSource> sources) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            RagSource source = sources.get(i);
            context.append('[').append(i + 1).append("] ")
                    .append(AiCoreComponent.orDefault(source.documentName(), "课程资料"))
                    .append('：')
                    .append(AiCoreComponent.orDefault(source.snippet(), ""))
                    .append('\n');
        }
        return context.toString();
    }

    /** 课程知识点范围（按重要度取前 {@value #MAX_COURSE_POINTS} 个名称，去重后以顿号连接）。 */
    public String scopeText(List<KnowledgePoint> points) {
        if (points == null || points.isEmpty()) {
            return "";
        }
        return points.stream()
                .sorted(pointPriority())
                .limit(MAX_COURSE_POINTS)
                .map(KnowledgePoint::getName)
                .distinct()
                .reduce((a, b) -> a + "、" + b)
                .orElse("");
    }

    /** 知识点证据转问答出处，供界面溯源展示。 */
    public List<QaSourceVO> pointSources(List<KnowledgePoint> matched) {
        List<QaSourceVO> sources = new ArrayList<>();
        for (KnowledgePoint point : matched) {
            String description = cleanDescription(point.getDescription());
            sources.add(new QaSourceVO(point.getId(), "课程知识图谱 · " + point.getName(), 0.9,
                    description.isEmpty() ? "课程知识点：" + point.getName() : description));
        }
        return sources;
    }

    /**
     * 本次回答所依据的证据名列表（引擎召回文档名在前、命中知识点名在后，去重保序），
     * 供批改等场景写入 {@code aiEvidence} 字段。
     */
    public List<String> evidenceNames(List<RagSource> sources, List<KnowledgePoint> points) {
        Set<String> names = new LinkedHashSet<>();
        if (sources != null) {
            for (RagSource source : sources) {
                if (source == null) {
                    continue;
                }
                names.add(AiCoreComponent.orDefault(source.documentName(), "课程资料"));
            }
        }
        if (points != null) {
            for (KnowledgePoint point : points) {
                if (point == null || point.getName() == null || point.getName().isBlank()) {
                    continue;
                }
                names.add(point.getName());
            }
        }
        return new ArrayList<>(names);
    }

    /** 问题与知识点名称的轻量匹配：名称至少 2 字且整体出现在问题中（忽略大小写与空白）。 */
    private boolean matchesQuestion(String normalizedQuestion, String pointName) {
        String normalizedName = normalize(pointName);
        return normalizedName.length() >= 2 && normalizedQuestion.contains(normalizedName);
    }

    private Comparator<KnowledgePoint> pointPriority() {
        return Comparator.comparingInt((KnowledgePoint p) -> p.getImportance() == null ? 0 : -p.getImportance())
                .thenComparingLong(p -> p.getId() == null ? Long.MAX_VALUE : p.getId());
    }

    /** 图谱前置关系查询失败不应阻断上层流程，降级为无前置知识。 */
    private List<KnowledgePoint> prerequisites(Long pointId) {
        if (pointId == null) {
            return List.of();
        }
        try {
            List<KnowledgePoint> relations = knowledgePointService.getPrerequisites(pointId);
            return relations == null ? List.of() : relations.stream().limit(MAX_PREREQUISITES).toList();
        } catch (Exception e) {
            log.warn("读取知识点前置关系失败，跳过前置知识: {}", e.getMessage());
            return List.of();
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    /** 描述可能来自教师富文本，注入前压缩换行并截断，避免超长 prompt。 */
    private String cleanDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String compact = description.replaceAll("\\s+", " ").trim();
        return compact.length() > MAX_DESCRIPTION_LEN ? compact.substring(0, MAX_DESCRIPTION_LEN) + "…" : compact;
    }
}
