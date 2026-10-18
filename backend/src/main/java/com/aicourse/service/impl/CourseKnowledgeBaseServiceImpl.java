package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.Resource;
import com.aicourse.exception.BusinessException;
import com.aicourse.integration.ContractPaths;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseKnowledgeBaseService;
import com.aicourse.service.CourseService;
import com.aicourse.service.RAGService;
import com.aicourse.service.ResourceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 课程知识库编排服务实现（消费侧）。
 * <p>
 * 枚举课程结构化资料并幂等灌入 RAG 知识库：
 * <ol>
 *   <li><b>课程简介</b>：{@code course.description} → 文档名 {@code [课程简介] <课程名>}；</li>
 *   <li><b>章节</b>：按 {@code orderIndex} 排序，文档名 {@code [章节] <标题>}，内容为标题 + 正文；</li>
 *   <li><b>资源</b>：仅取审核状态为正常（{@link Resource#MODERATION_NORMAL}）的，文档名 {@code [资源] <文件名>}。</li>
 * </ol>
 * 幂等：先以 {@link RAGService#listDocuments(Long)} 取回已有文档名集合，命中即跳过（重跑不产生重复语料）。
 * </p>
 * <p>
 * <b>失败可见</b>（不静默、不误导）：
 * <ul>
 *   <li>{@code rag.enabled=false}（默认）→ {@code mode=LOCAL}，只枚举不写入，
 *       {@code note} 如实说明「引擎未接入、枚举了 N 条、接入后重试即写入」；</li>
 *   <li>已启用但引擎不可用/写入中途降级 → {@code mode=UNAVAILABLE}，附 {@code error} 与 {@code code=503}；</li>
 *   <li>课程不存在 → 抛 {@code BusinessException(404)}，由全局异常处理器返回错误信封。</li>
 * </ul>
 * </p>
 */
@Service
@Slf4j
public class CourseKnowledgeBaseServiceImpl implements CourseKnowledgeBaseService {

    private static final ObjectMapper OM = new ObjectMapper();

    /** 文档来源分类（写入文档名前缀，并回传前端以便展示）。 */
    private static final String SRC_INTRO = "课程简介";
    private static final String SRC_CHAPTER = "章节";
    private static final String SRC_RESOURCE = "资源";

    /** 单个文档内容的长度上限，避免超长章节把 rag 请求体撑爆（引擎侧另有切分）。 */
    private static final int MAX_CONTENT_LENGTH = 8000;

    private final boolean ragEnabled;
    private final RAGService ragService;
    private final CourseService courseService;
    private final CourseChapterService courseChapterService;
    private final ResourceService resourceService;

    public CourseKnowledgeBaseServiceImpl(
            @Value("${rag.enabled:false}") boolean ragEnabled,
            RAGService ragService,
            CourseService courseService,
            CourseChapterService courseChapterService,
            ResourceService resourceService) {
        this.ragEnabled = ragEnabled;
        this.ragService = ragService;
        this.courseService = courseService;
        this.courseChapterService = courseChapterService;
        this.resourceService = resourceService;
    }

    @Override
    public String buildFromCourse(Long courseId) {
        Course course = courseId == null ? null : courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在或已被删除");
        }

        List<KbDoc> candidates = collectDocuments(course);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", course.getId());
        result.put("courseName", course.getName());
        result.put("total", candidates.size());
        result.put("documents", documentViews(candidates));
        // 默认值先落位：LOCAL / UNAVAILABLE 分支均未真正写入，added/skipped 恒为 0，
        // 保证前端拿到的字段形状一致（不必对缺字段做特例判断）。
        result.put("added", 0);
        result.put("skipped", 0);

        if (candidates.isEmpty()) {
            result.put("mode", ContractPaths.MODE_LOCAL);
            result.put("note", "课程暂无可入库资料（简介、章节、资源均为空），未写入任何文档");
            return write(result);
        }

        if (!ragEnabled) {
            result.put("mode", ContractPaths.MODE_LOCAL);
            result.put("note", "RAG 引擎未接入（rag.enabled=false）：已在消费侧枚举 " + candidates.size()
                    + " 条课程资料，但未真正写入引擎；引擎接入后重新执行即可写入并重建索引");
            return write(result);
        }

        Set<String> existingNames = loadExistingNames(courseId);
        if (existingNames == null) {
            return unavailable(result, "RAG 引擎不可用：未能读取已有文档列表，本次未写入任何文档");
        }

        int added = 0;
        int skipped = 0;
        int failed = 0;
        for (KbDoc doc : candidates) {
            if (existingNames.contains(doc.name())) {
                skipped++;
                continue;
            }
            if (isEngineUnavailable(readJson(ragService.addDocument(courseId, doc.name(), doc.content())))) {
                failed++;
                continue;
            }
            added++;
        }

        Integer indexedChunks = null;
        boolean indexFailed = false;
        if (added > 0) {
            JsonNode indexNode = readJson(ragService.buildIndex(courseId));
            if (isEngineUnavailable(indexNode)) {
                indexFailed = true;
            } else if (indexNode != null && indexNode.has("indexedChunks") && !indexNode.get("indexedChunks").isNull()) {
                indexedChunks = indexNode.get("indexedChunks").asInt();
            }
        }

        result.put("added", added);
        result.put("skipped", skipped);
        if (indexedChunks != null) {
            result.put("indexedChunks", indexedChunks);
        }

        if (failed > 0 || indexFailed) {
            String detail = indexFailed
                    ? "RAG 引擎在重建索引阶段不可用，已写入 " + added + " 篇但索引未重建"
                    : "RAG 引擎不可用，有 " + failed + " 篇课程资料未能写入";
            return unavailable(result, detail);
        }

        result.put("mode", ContractPaths.MODE_ENGINE);
        result.put("note", "已写入 " + added + " 篇、跳过 " + skipped + " 篇（知识库中已存在），并重建课程索引");
        return write(result);
    }

    // ===================== 资料枚举 =====================

    /** 枚举课程可入库资料（简介 → 章节 → 资源，顺序稳定便于前端展示）。 */
    private List<KbDoc> collectDocuments(Course course) {
        List<KbDoc> docs = new ArrayList<>();

        if (notBlank(course.getDescription())) {
            docs.add(new KbDoc("[课程简介] " + safeName(course.getName()),
                    SRC_INTRO, truncate(safeName(course.getName()) + "\n" + course.getDescription())));
        }

        List<CourseChapter> chapters = courseChapterService.list(
                new LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, course.getId())
                        .orderByAsc(CourseChapter::getOrderIndex, CourseChapter::getId));
        for (CourseChapter chapter : chapters) {
            if (!notBlank(chapter.getTitle())) {
                continue;
            }
            String content = notBlank(chapter.getContent())
                    ? chapter.getTitle() + "\n" + chapter.getContent()
                    : chapter.getTitle();
            docs.add(new KbDoc("[章节] " + chapter.getTitle(), SRC_CHAPTER, truncate(content)));
        }

        List<Resource> resources = resourceService.list(
                new LambdaQueryWrapper<Resource>()
                        .eq(Resource::getCourseId, course.getId())
                        .eq(Resource::getModerationStatus, Resource.MODERATION_NORMAL)
                        .orderByAsc(Resource::getId));
        for (Resource resource : resources) {
            if (!notBlank(resource.getFileName())) {
                continue;
            }
            String content = notBlank(resource.getDescription())
                    ? resource.getFileName() + "\n" + resource.getDescription()
                    : resource.getFileName();
            docs.add(new KbDoc("[资源] " + resource.getFileName(), SRC_RESOURCE, truncate(content)));
        }

        return docs;
    }

    /** 取知识库已有文档名集合；引擎不可用（列表请求返回 503）或解析失败时返回 null。 */
    private Set<String> loadExistingNames(Long courseId) {
        JsonNode root = readJson(ragService.listDocuments(courseId));
        if (isEngineUnavailable(root)) {
            return null;
        }
        Set<String> names = new HashSet<>();
        JsonNode arr = root == null ? null : root.get("documents");
        if (arr != null && arr.isArray()) {
            for (JsonNode d : arr) {
                String name = textOrNull(d, "documentName");
                if (name == null) {
                    name = textOrNull(d, "name");
                }
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    // ===================== 序列化与解析 =====================

    private List<Map<String, Object>> documentViews(List<KbDoc> docs) {
        List<Map<String, Object>> views = new ArrayList<>(docs.size());
        for (KbDoc d : docs) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("name", d.name());
            view.put("source", d.source());
            views.add(view);
        }
        return views;
    }

    /** 构造「引擎不可用」结果：补 mode=UNAVAILABLE / error / code=503（与 RAG 契约语义一致）。 */
    private String unavailable(Map<String, Object> result, String detail) {
        result.put("mode", ContractPaths.MODE_UNAVAILABLE);
        result.put("error", ContractPaths.MSG_RAG_UNAVAILABLE);
        result.put("code", ContractPaths.CODE_UNAVAILABLE);
        result.put("note", detail);
        return write(result);
    }

    private String write(Map<String, Object> payload) {
        try {
            return OM.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("课程知识库构建结果序列化失败：{}", e.getMessage());
            return "{\"error\":\"" + ContractPaths.MSG_RAG_UNAVAILABLE + "\",\"code\":"
                    + ContractPaths.CODE_UNAVAILABLE + "}";
        }
    }

    private JsonNode readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OM.readTree(raw);
        } catch (Exception e) {
            log.debug("RAG 响应解析失败：{}", e.getMessage());
            return null;
        }
    }

    /** 判定调用结果是否代表「引擎不可用」：响应缺失/非对象，或 code=503，或 mode=UNAVAILABLE。 */
    private boolean isEngineUnavailable(JsonNode node) {
        if (node == null || !node.isObject()) {
            return true;
        }
        if (node.has(ContractPaths.R_CODE)
                && node.get(ContractPaths.R_CODE).asInt() == ContractPaths.CODE_UNAVAILABLE) {
            return true;
        }
        return ContractPaths.MODE_UNAVAILABLE.equals(textOrNull(node, ContractPaths.R_MODE));
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String safeName(String s) {
        return notBlank(s) ? s : "未命名";
    }

    private static String truncate(String s) {
        return s.length() > MAX_CONTENT_LENGTH ? s.substring(0, MAX_CONTENT_LENGTH) : s;
    }

    /** 待入库文档（名称用于幂等去重，来源用于前端展示）。 */
    private record KbDoc(String name, String source, String content) {
    }
}
