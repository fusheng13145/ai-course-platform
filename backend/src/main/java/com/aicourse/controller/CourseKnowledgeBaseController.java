package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.CourseKnowledgeBaseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 课程知识库编排控制器。
 * <p>
 * 与 {@link RAGController} 同域（路径前缀 {@code /rag}）：提供「一键把课程资料灌入知识库」的入口，
 * 供教师/管理员在知识库管理页与课程详情页直接触发。仅做路由与信封转换，编排逻辑在
 * {@link CourseKnowledgeBaseService}。
 * </p>
 * 响应统一为 {@link ApiResponse} 信封；服务层返回的 JSON 文本在此反序列化为结构化数据
 * （与 RAGController 同一约定）。引擎不可用时响应体携带 {@code mode=UNAVAILABLE} / {@code code=503}，
 * 由前端按「失败可见」展示降级提示。
 */
@RestController
// 注意：server.servlet.context-path 已为 /api，故此处只需写 /rag/course。
@RequestMapping("/rag/course")
@Tag(name = "知识库")
public class CourseKnowledgeBaseController {

    private static final Logger log = LoggerFactory.getLogger(CourseKnowledgeBaseController.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CourseKnowledgeBaseService courseKnowledgeBaseService;

    public CourseKnowledgeBaseController(CourseKnowledgeBaseService courseKnowledgeBaseService) {
        this.courseKnowledgeBaseService = courseKnowledgeBaseService;
    }

    /** 从课程结构化资料（简介 / 章节 / 资源）构建课程知识库并重建索引（教师/管理员） */
    @PostMapping("/{courseId}/build")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ApiResponse<JsonNode> buildFromCourse(@PathVariable Long courseId) {
        return ApiResponse.success(toJson(courseKnowledgeBaseService.buildFromCourse(courseId)));
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
            log.warn("课程知识库响应解析为JSON失败，已回退为文本节点: {}", e.getMessage());
            return TextNode.valueOf(raw);
        }
    }
}
