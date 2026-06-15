package com.aicourse.controller;

import com.aicourse.service.KnowledgeGraphService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 知识图谱控制器
 * 提供知识图谱相关的REST API接口
 */
@RestController
@RequestMapping("/api/knowledge-graph")
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;

    public KnowledgeGraphController(KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    @GetMapping("/course/{courseId}")
    public String getGraphByCourse(@PathVariable Long courseId) {
        return knowledgeGraphService.getGraph(courseId, null);
    }

    @GetMapping("/nodes/{nodeId}")
    public String getNodeDetail(@PathVariable Long nodeId) {
        return knowledgeGraphService.getGraph(null, nodeId);
    }

    @GetMapping("/nodes/{nodeId}/relations")
    public String getNodeRelations(
            @PathVariable Long nodeId,
            @RequestParam(defaultValue = "2") int depth) {
        return knowledgeGraphService.getNodeRelations(nodeId, depth);
    }

    @PostMapping("/nodes")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String addNode(
            @RequestParam Long courseId,
            @RequestParam String name,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String type) {
        return knowledgeGraphService.addNode(courseId, name, content, type);
    }

    @PostMapping("/relations")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String addRelation(
            @RequestParam Long sourceId,
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "RELATED_TO") String relationType) {
        return knowledgeGraphService.addRelation(sourceId, targetId, relationType);
    }

    @DeleteMapping("/nodes/{nodeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String deleteNode(@PathVariable Long nodeId) {
        return knowledgeGraphService.deleteNode(nodeId);
    }

    @DeleteMapping("/relations/{relationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String deleteRelation(@PathVariable Long relationId) {
        return knowledgeGraphService.deleteRelation(relationId);
    }
}