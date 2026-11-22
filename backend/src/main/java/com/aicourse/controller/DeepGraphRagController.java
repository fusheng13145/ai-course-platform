package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.DeepGraphRagService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DeepGraph RAG（文档图谱检索增强）控制器。
 * <p>
 * 对接 {@code python-services/deepgraph_rag}（8501，已验收 DeepSeek-RAG-Chatbot 原样复制 +
 * 契约适配器 {@code api_server.py}）。与 {@link RAGController}（8002 知识点语料库）互补：
 * 本控制器治理<b>整篇课程文档</b>的入库、检索、章节图谱与学习路径。
 * 问答主链（{@code /ai/qa/*}）已自动合并本引擎召回，无需前端改造；
 * 本控制器是治理与调试入口。引擎未启用时服务层返回 503 降级 JSON，按既有语义如实透出。
 * </p>
 */
@RestController
// server.servlet.context-path 已为 /api，故此处只写 /rag/deepgraph
@RequestMapping("/rag/deepgraph")
@Tag(name = "文档图谱 RAG")
public class DeepGraphRagController {

    private static final Logger log = LoggerFactory.getLogger(DeepGraphRagController.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DeepGraphRagService deepGraphRagService;
    private final CourseAccessControl courseAccessControl;

    public DeepGraphRagController(DeepGraphRagService deepGraphRagService,
                                  CourseAccessControl courseAccessControl) {
        this.deepGraphRagService = deepGraphRagService;
        this.courseAccessControl = courseAccessControl;
    }

    /** 引擎健康探测（enabled + 8501 可达性，运维与前端降级提示用）。 */
    @GetMapping("/health")
    public ResponseEntity<JsonNode> health() {
        // 不套 ApiResponse：健康语义对编排探针与告警最友好的形态就是裸 JSON
        return ResponseEntity.ok(toJson(deepGraphRagService.healthJson()));
    }

    /** 向指定课程入库一篇整篇文档（教师/管理员，且教师须是本课程）。 */
    @PostMapping("/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> addDocument(
            @RequestParam Long courseId,
            @RequestParam String documentName,
            @RequestBody String content,
            Authentication auth) {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        if (content == null || content.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "文档内容不能为空");
        }
        return ApiResponse.success(toJson(deepGraphRagService.ingest(courseId, documentName, content)));
    }

    /** 列出 deepgraph 引擎持有的课程文档（教师/管理员；不带 courseId 仅管理员）。 */
    @GetMapping("/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> listDocuments(@RequestParam(required = false) Long courseId,
                                               Authentication auth) {
        if (courseId == null) {
            boolean admin = auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!admin) {
                throw new BusinessException(ApiCode.FORBIDDEN, "列举全部课程文档仅管理员可用");
            }
            return ApiResponse.success(toJson(deepGraphRagService.listDocuments(null)));
        }
        courseAccessControl.assertCanManageCourse(courseId, auth);
        return ApiResponse.success(toJson(deepGraphRagService.listDocuments(courseId)));
    }

    /** 删除指定文档及其向量索引/图谱/章节数据（教师/管理员，且教师须是本课程）。 */
    @DeleteMapping("/documents/{documentName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> deleteDocument(
            @PathVariable String documentName,
            @RequestParam Long courseId,
            Authentication auth) {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        return ApiResponse.success(toJson(deepGraphRagService.deleteDocument(courseId, documentName)));
    }

    /** 在指定课程文档语料中检索（读取范围与 /rag/retrieve 同口径：学生限本人课程）。 */
    @PostMapping("/retrieve")
    public ApiResponse<JsonNode> retrieve(
            @RequestParam String query,
            @RequestParam Long courseId,
            @RequestParam(defaultValue = "3") int topK,
            Authentication auth) {
        courseAccessControl.assertCanReadCourse(courseId, auth);
        return ApiResponse.success(toJson(deepGraphRagService.retrieve(query, courseId, topK)));
    }

    /** 构建指定文档某一章节的细粒度知识图谱（LLM 三元组抽取 + Neo4j 同步；教师/管理员）。 */
    @PostMapping("/chapter-graph")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> chapterGraph(
            @RequestParam Long courseId,
            @RequestParam String documentName,
            @RequestParam(required = false) String chapterName,
            @RequestBody(required = false) String chapterText,
            Authentication auth) {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        if (chapterName == null || chapterName.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请指定章节名称（或直接提交章节正文）");
        }
        return ApiResponse.success(toJson(
                deepGraphRagService.chapterGraph(courseId, documentName, chapterName, chapterText)));
    }

    /**
     * 把引擎聚合的课程图谱（LLM 真实抽取的章节三元组 + 层次大纲骨架）幂等落库
     * MySQL knowledge_points/knowledge_point_relations（教师/管理员，且教师须是本课程）。
     * 平台「知识图谱」页（/knowledge/points/graph）渲染本表，同步后页面立即可见。
     */
    @PostMapping("/course-graph/sync")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> syncCourseGraph(
            @RequestParam Long courseId,
            @RequestParam String documentName,
            Authentication auth) {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        return ApiResponse.success(toJson(deepGraphRagService.syncCourseKnowledgeGraph(courseId, documentName)));
    }

    /** 学习路径规划：基于图谱反向追溯目标知识点的前置知识（课程成员可用）。 */
    @PostMapping("/learning-path")
    public ApiResponse<JsonNode> learningPath(
            @RequestParam String target,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "3") int maxDepth,
            @RequestParam(defaultValue = "true") boolean searchAllSources,
            Authentication auth) {
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
        }
        return ApiResponse.success(toJson(
                deepGraphRagService.learningPath(target, courseId, maxDepth, searchAllSources)));
    }

    /**
     * 将服务层返回的 JSON 文本安全反序列化为 JsonNode；
     * 解析失败时回退为文本节点，保证接口永不因格式问题抛 500。
     */
    private JsonNode toJson(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(raw);
        } catch (Exception e) {
            log.warn("DeepGraph RAG 响应解析为JSON失败，已回退为文本节点: {}", e.getMessage());
            return TextNode.valueOf(raw);
        }
    }
}
