package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.RAGService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    public RAGController(RAGService ragService) {
        this.ragService = ragService;
    }

    /** 基于知识库对指定问题进行智能问答 */
    @PostMapping("/ask")
    public ApiResponse<JsonNode> ask(
            @RequestParam String question,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "3") int topK) {
        return ApiResponse.success(toJson(ragService.ask(question, courseId, topK)));
    }

    /** 向指定课程的知识库新增一篇文档（教师/管理员） */
    @PostMapping("/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> addDocument(
            @RequestParam Long courseId,
            @RequestParam String documentName,
            @Valid @RequestBody String content) {
        return ApiResponse.success(toJson(ragService.addDocument(courseId, documentName, content)));
    }

    /** 列出课程知识库中的文档（可按课程筛选） */
    @GetMapping("/documents")
    public ApiResponse<JsonNode> listDocuments(@RequestParam(required = false) Long courseId) {
        return ApiResponse.success(toJson(ragService.listDocuments(courseId)));
    }

    /** 更新知识库中指定文档的内容（教师/管理员） */
    @PutMapping("/documents/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> updateDocument(
            @PathVariable Long documentId,
            @Valid @RequestBody String content) {
        return ApiResponse.success(toJson(ragService.updateDocument(documentId, content)));
    }

    /** 删除知识库中指定文档（教师/管理员） */
    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> deleteDocument(@PathVariable Long documentId) {
        return ApiResponse.success(toJson(ragService.deleteDocument(documentId)));
    }

    /** 为指定课程构建/重建向量索引（教师/管理员） */
    @PostMapping("/index/build/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> buildIndex(@PathVariable Long courseId) {
        return ApiResponse.success(toJson(ragService.buildIndex(courseId)));
    }

    /** 清空指定课程的向量索引（教师/管理员） */
    @DeleteMapping("/index/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> clearIndex(@PathVariable Long courseId) {
        return ApiResponse.success(toJson(ragService.clearIndex(courseId)));
    }

    /** 从知识库检索与查询最相关的文本片段 */
    @PostMapping("/retrieve")
    public ApiResponse<JsonNode> retrieve(
            @RequestParam String query,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "5") int topK) {
        return ApiResponse.success(toJson(ragService.retrieve(query, courseId, topK)));
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
