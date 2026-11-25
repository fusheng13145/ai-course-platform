package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.security.AccessSupport;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.UploadMagic;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.DeepGraphRagService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

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
    public ResponseEntity<ApiResponse<JsonNode>> addDocument(
            @RequestParam Long courseId,
            @RequestParam String documentName,
            @RequestBody String content,
            Authentication auth) {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        if (content == null || content.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "文档内容不能为空");
        }
        return envelope(deepGraphRagService.ingest(courseId, documentName, content));
    }

    /**
     * 上传 PDF/DOCX/TXT 原文入库（教师/管理员，且教师须是本课程）。
     * <p>Java 只做课程 ACL 与容器魔数校验，不把二进制文本化；字节以 raw body 转发 8501，
     * 由复制内容 load_single_file 解析。限制：50MB（与 spring.servlet.multipart 对齐）。</p>
     */
    @PostMapping("/documents/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<JsonNode>> uploadDocument(
            @RequestParam Long courseId,
            @RequestParam(required = false) String documentName,
            @RequestParam("file") MultipartFile file,
            Authentication auth) throws IOException {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "上传文件不能为空");
        }
        if (file.getSize() > 50L * 1024 * 1024) {
            throw new BusinessException(ApiCode.PAYLOAD_TOO_LARGE, "上传文件不能超过 50MB");
        }
        String rawName = (documentName == null || documentName.isBlank())
                ? file.getOriginalFilename() : documentName;
        if (rawName == null || rawName.isBlank() || rawName.length() > 255) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "文档名不能为空且不超过 255 字符");
        }
        String safeName = rawName.replace('\\', '/');
        safeName = safeName.substring(safeName.lastIndexOf('/') + 1);
        int dot = safeName.lastIndexOf('.');
        String ext = dot < 0 ? "" : safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!"pdf".equals(ext) && !"docx".equals(ext) && !"txt".equals(ext)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "仅支持 PDF/DOCX/TXT 文档");
        }
        String magic = UploadMagic.detect(UploadMagic.readHeader(file));
        if (!UploadMagic.matchesExtension(ext, magic)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "文件内容与扩展名不一致");
        }
        byte[] content = file.getBytes();
        String contentType = file.getContentType();
        return envelope(deepGraphRagService.ingestFile(courseId, safeName, content, contentType));
    }

    /** 列出 deepgraph 引擎持有的课程文档（教师/管理员；不带 courseId 仅管理员）。 */
    @GetMapping("/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<JsonNode>> listDocuments(@RequestParam(required = false) Long courseId,
                                                               Authentication auth) {
        if (courseId == null) {
            boolean admin = auth != null && AccessSupport.isAdmin(auth);
            if (!admin) {
                throw new BusinessException(ApiCode.FORBIDDEN, "列举全部课程文档仅管理员可用");
            }
            return envelope(deepGraphRagService.listDocuments(null));
        }
        courseAccessControl.assertCanManageCourse(courseId, auth);
        return envelope(deepGraphRagService.listDocuments(courseId));
    }

    /** 删除指定文档及其向量索引/图谱/章节数据（教师/管理员，且教师须是本课程）。 */
    @DeleteMapping("/documents/{documentName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<JsonNode>> deleteDocument(
            @PathVariable String documentName,
            @RequestParam Long courseId,
            Authentication auth) {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        return envelope(deepGraphRagService.deleteDocument(courseId, documentName));
    }

    /** 在指定课程文档语料中检索（读取范围与 /rag/retrieve 同口径：学生限本人课程）。 */
    @PostMapping("/retrieve")
    public ResponseEntity<ApiResponse<JsonNode>> retrieve(
            @RequestParam String query,
            @RequestParam Long courseId,
            @RequestParam(defaultValue = "3") int topK,
            Authentication auth) {
        courseAccessControl.assertCanReadCourse(courseId, auth);
        return envelope(deepGraphRagService.retrieve(query, courseId, topK));
    }

    /** 构建指定文档某一章节的细粒度知识图谱（LLM 三元组抽取 + Neo4j 同步；教师/管理员）。 */
    @PostMapping("/chapter-graph")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<JsonNode>> chapterGraph(
            @RequestParam Long courseId,
            @RequestParam String documentName,
            @RequestParam(required = false) String chapterName,
            @RequestBody(required = false) String chapterText,
            Authentication auth) {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        if (chapterName == null || chapterName.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请指定章节名称（或直接提交章节正文）");
        }
        return envelope(deepGraphRagService.chapterGraph(courseId, documentName, chapterName, chapterText));
    }

    /**
     * 把引擎聚合的课程图谱（LLM 真实抽取的章节三元组 + 层次大纲骨架）幂等落库
     * MySQL knowledge_points/knowledge_point_relations（教师/管理员，且教师须是本课程）。
     * 平台「知识图谱」页（/knowledge/points/graph）渲染本表，同步后页面立即可见。
     */
    @PostMapping("/course-graph/sync")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<JsonNode>> syncCourseGraph(
            @RequestParam Long courseId,
            @RequestParam String documentName,
            Authentication auth) {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        return envelope(deepGraphRagService.syncCourseKnowledgeGraph(courseId, documentName));
    }

    /** 学习路径规划：基于图谱反向追溯目标知识点的前置知识（课程成员可用）。 */
    @PostMapping("/learning-path")
    public ResponseEntity<ApiResponse<JsonNode>> learningPath(
            @RequestParam String target,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "3") int maxDepth,
            @RequestParam(defaultValue = "true") boolean searchAllSources,
            Authentication auth) {
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
        }
        return envelope(deepGraphRagService.learningPath(target, courseId, maxDepth, searchAllSources));
    }

    /**
     * 统一封装：引擎降级（契约 code=503）必须同时反映到 HTTP 状态与 ApiResponse.code，
     * 避免“HTTP 200 + 成功信封 + 内部 503”的伪成功；其它业务码仍按成功信封透出原始 payload。
     */
    private ResponseEntity<ApiResponse<JsonNode>> envelope(String raw) {
        JsonNode node = toJson(raw);
        int contractCode = node == null ? 0 : node.path("code").asInt(0);
        if (contractCode == ApiCode.SERVICE_UNAVAILABLE) {
            String message = node.path("message").asText("文档图谱引擎未启用或不可达");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(ApiCode.SERVICE_UNAVAILABLE, message, node));
        }
        return ResponseEntity.ok(ApiResponse.success(node));
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
