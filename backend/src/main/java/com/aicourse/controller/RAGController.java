package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.AdminScope;
import com.aicourse.common.RoleConstants;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.RAGService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RAG知识库控制器
 * 提供基于知识库的智能问答接口
 * 响应统一为 {@link ApiResponse} 信封；服务层返回的 JSON 文本在此反序列化为结构化数据，
 * 前端无需再做「字符串 → 对象」的特例解析。
 */
@RestController
// 注意：server.servlet.context-path 已为 /api，故此处只需写 /rag
// （若写成 /api/rag 会变成 /api/api/rag，导致前端调用的 /api/rag/* 全部 404/401）
@RequestMapping("/rag")
@Tag(name = "知识库")
public class RAGController {

    private static final Logger log = LoggerFactory.getLogger(RAGController.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RAGService ragService;
    private final CourseAccessControl courseAccessControl;

    public RAGController(RAGService ragService, CourseAccessControl courseAccessControl) {
        this.ragService = ragService;
        this.courseAccessControl = courseAccessControl;
    }

    /** ROLE_ 前缀剥离后的角色名（与 DiscussionController 同语义，此处只用于管理员判定）。 */
    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    /** 院系管理员：ADMIN + SCOPE_ADMIN_COLLEGE；没有该作用域的存量 ADMIN 仍按校级处理。 */
    private boolean isCollegeAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(AdminScope.AUTHORITY_COLLEGE::equals);
    }

    private boolean isSchoolAdmin(Authentication auth) {
        return isAdmin(auth) && !isCollegeAdmin(auth);
    }

    /**
     * 基于知识库对指定问题进行智能问答。
     * <p>2026-11-06 验收批：带 courseId 时必须先过课程成员关系——否则任意学生可用
     * 他人课程的 courseId 把该课知识库正文取进答案（越权取数 + 违反红线③的接地范围）。
     */
    @PostMapping("/ask")
    public ApiResponse<JsonNode> ask(
            @RequestParam String question,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "3") int topK,
            Authentication auth) {
        assertRagCourseScope(courseId, auth);
        return ApiResponse.success(toJson(ragService.ask(question, courseId, topK)));
    }

    /** 向指定课程的知识库新增一篇文档（教师/管理员，且教师须是本课程） */
    @PostMapping("/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> addDocument(
            @RequestParam Long courseId,
            @RequestParam String documentName,
            @Valid @RequestBody String content,
            Authentication auth) {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        return ApiResponse.success(toJson(ragService.addDocument(courseId, documentName, content)));
    }

    /**
     * 列出知识库文档（可按课程筛选）。
     * <p>2026-11-06 验收批：原先任何人（含学生）可列举任意课程、甚至不带 courseId 拿全量。
     * 现在：带 courseId 限教师本人课程或管理员；不带 courseId 仅管理员。
     */
    @GetMapping("/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> listDocuments(@RequestParam(required = false) Long courseId,
                                               Authentication auth) {
        if (courseId == null) {
            if (!isAdmin(auth)) {
                throw new BusinessException(ApiCode.FORBIDDEN, "列举全部课程文档仅管理员可用");
            }
            JsonNode all = toJson(ragService.listDocuments(null));
            if (isCollegeAdmin(auth)) {
                // 院系管理员不得查看他院课程文档；RAG 文档无本地表，只能按引擎返回的 course_id 过滤。
                List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
                return ApiResponse.success(filterDocumentsByCourseIds(all, readableCourseIds));
            }
            return ApiResponse.success(all);
        }
        courseAccessControl.assertCanManageCourse(courseId, auth);
        return ApiResponse.success(toJson(ragService.listDocuments(courseId)));
    }

    /**
     * 更新知识库中指定文档的内容（教师/管理员）。
     * <p>对象级收口（2026-11-13）：原先仅 hasRole('TEACHER')，甲课教师可改乙课文档。
     * 与 addDocument 同款：先反查文档所属课程，再套 {@link CourseAccessControl#assertCanManageCourse}。</p>
     */
    @PutMapping("/documents/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> updateDocument(
            @PathVariable Long documentId,
            @Valid @RequestBody String content,
            Authentication auth) {
        assertCanManageDocument(documentId, auth);
        return ApiResponse.success(toJson(ragService.updateDocument(documentId, content)));
    }

    /**
     * 删除知识库中指定文档（教师/管理员）。
     * <p>对象级收口（2026-11-13）：同 updateDocument，教师仅可删除本人课程下的文档。</p>
     */
    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> deleteDocument(@PathVariable Long documentId, Authentication auth) {
        assertCanManageDocument(documentId, auth);
        return ApiResponse.success(toJson(ragService.deleteDocument(documentId)));
    }

    /**
     * 文档级归属校验：文档ID是 RAG 引擎侧资源，本端无本地表，先经 listDocuments 反查其
     * 所属课程，再套用与 addDocument 相同的 {@code assertCanManageCourse} 课程管理权判定。
     * 反查不到（引擎不可用/文档不存在/未挂课程）时：有课程归属的一律走课程 ACL；
     * 未挂课程的全局文档仅校级管理员可治理，院系管理员与教师均 fail-closed 403。
     */
    private void assertCanManageDocument(Long documentId, Authentication auth) {
        Long courseId = resolveCourseIdOfDocument(documentId);
        if (courseId != null) {
            courseAccessControl.assertCanManageCourse(courseId, auth);
            return;
        }
        if (isSchoolAdmin(auth)) {
            return;
        }
        throw new BusinessException(ApiCode.FORBIDDEN, "无法确认该文档所属课程，禁止操作");
    }

    /** 经 RAG 引擎全量文档列表反查 documentId → courseId；列表为空/解析失败返回 null。 */
    private Long resolveCourseIdOfDocument(Long documentId) {
        String raw = ragService.listDocuments(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            JsonNode arr = root.isArray() ? root : (root.has("documents") ? root.get("documents") : null);
            if (arr == null || !arr.isArray()) {
                return null;
            }
            for (JsonNode doc : arr) {
                if (!doc.isObject()) {
                    continue;
                }
                long id = doc.has("id") ? doc.get("id").asLong()
                        : (doc.has("document_id") ? doc.get("document_id").asLong() : -1L);
                if (id == documentId
                        && doc.has("course_id") && !doc.get("course_id").isNull()) {
                    return doc.get("course_id").asLong();
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("反查知识库文档归属课程失败: {}", e.getMessage());
            return null;
        }
    }

    /** 为指定课程构建/重建向量索引（教师/管理员，且教师须是本课程） */
    @PostMapping("/index/build/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> buildIndex(@PathVariable Long courseId, Authentication auth) {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        return ApiResponse.success(toJson(ragService.buildIndex(courseId)));
    }

    /** 清空指定课程的向量索引（教师/管理员，且教师须是本课程） */
    @DeleteMapping("/index/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> clearIndex(@PathVariable Long courseId, Authentication auth) {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        return ApiResponse.success(toJson(ragService.clearIndex(courseId)));
    }

    /** 从知识库检索与查询最相关的文本片段 */
    @PostMapping("/retrieve")
    public ApiResponse<JsonNode> retrieve(
            @RequestParam String query,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "5") int topK,
            Authentication auth) {
        assertRagCourseScope(courseId, auth);
        return ApiResponse.success(toJson(ragService.retrieve(query, courseId, topK)));
    }

    /**
     * RAG 取数边界：学生/教师必须显式指定课程；管理员可显式全局。
     * <p>Q-03 裁定：禁止学生无 {@code courseId} 的 RAG 全局检索；教师限本人课程。
     * 原先 {@code courseId == null} 直接落到引擎全库检索，任意学生都能取到全部课程语料。</p>
     */
    private void assertRagCourseScope(Long courseId, Authentication auth) {
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
            return;
        }
        if (!isAdmin(auth)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请指定课程ID后再使用课程知识库检索");
        }
    }

    /**
     * 按院系可读课程集合过滤 RAG 文档。兼容引擎返回裸数组与 {@code {documents: []}} 两种形态；
     * 无法识别 course_id 的文档一律不返回，避免院系管理员看到他院/全局文档。
     */
    private JsonNode filterDocumentsByCourseIds(JsonNode root, List<Long> allowedCourseIds) {
        if (root == null || allowedCourseIds == null) {
            return root;
        }
        if (root.isArray()) {
            return filterDocumentArray(root, allowedCourseIds);
        }
        if (root.isObject() && root.get("documents") != null && root.get("documents").isArray()) {
            ObjectNode copy = root.deepCopy();
            copy.set("documents", filterDocumentArray(root.get("documents"), allowedCourseIds));
            return copy;
        }
        return root;
    }

    private JsonNode filterDocumentArray(JsonNode documents, List<Long> allowedCourseIds) {
        var filtered = OBJECT_MAPPER.createArrayNode();
        for (JsonNode doc : documents) {
            if (!doc.isObject()) {
                continue;
            }
            Long docCourseId = documentCourseId(doc);
            if (docCourseId != null && allowedCourseIds.contains(docCourseId)) {
                filtered.add(doc);
            }
        }
        return filtered;
    }

    private Long documentCourseId(JsonNode doc) {
        JsonNode courseNode = doc.has("course_id") ? doc.get("course_id") : doc.get("courseId");
        return courseNode == null || courseNode.isNull() ? null : courseNode.asLong();
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
            log.warn("RAG响应解析为JSON失败，已回退为文本节点: {}", e.getMessage());
            return TextNode.valueOf(raw);
        }
    }
}
