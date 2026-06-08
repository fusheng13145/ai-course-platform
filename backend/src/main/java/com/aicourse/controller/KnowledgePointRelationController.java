package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.service.KnowledgePointRelationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 知识点关联关系接口控制器
 * 提供关联关系的创建、查询、删除及图谱可视化数据API
 */
@RestController
@RequestMapping("/knowledge-relations")
public class KnowledgePointRelationController {

    private final KnowledgePointRelationService relationService;

    public KnowledgePointRelationController(KnowledgePointRelationService relationService) {
        this.relationService = relationService;
    }

    /**
     * 创建知识点关联关系（教师/管理员权限）
     * @param params 包含sourceId, targetId, relationType, strength, description的请求体
     * @return 创建的关联关系
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgePointRelation> createRelation(@RequestBody Map<String, Object> params) {
        Long sourceId = Long.valueOf(params.get("sourceId").toString());
        Long targetId = Long.valueOf(params.get("targetId").toString());
        String relationType = (String) params.get("relationType");
        BigDecimal strength = new BigDecimal(params.get("strength").toString());
        String description = (String) params.get("description");

        KnowledgePointRelation relation = relationService.createRelation(sourceId, targetId, relationType,
                strength, description);
        return ApiResponse.success("创建成功", relation);
    }

    /**
     * 获取某知识点的所有关联关系（入向+出向）
     * @param pointId 知识点ID
     * @return 关联关系列表
     */
    @GetMapping("/point/{pointId}")
    public ApiResponse<List<KnowledgePointRelation>> getRelationsByPoint(@PathVariable Long pointId) {
        List<KnowledgePointRelation> relations = relationService.getRelationsByPoint(pointId);
        return ApiResponse.success(relations);
    }

    /**
     * 删除知识点关联关系（教师/管理员权限）
     * @param id 关联记录ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> deleteRelation(@PathVariable Long id) {
        relationService.deleteRelation(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 获取课程知识图谱可视化图数据（节点+边）
     * @param courseId 课程ID
     * @return 图谱可视化数据（nodes + links格式，供ECharts/D3渲染）
     */
    @GetMapping("/graph/{courseId}")
    public ApiResponse<Map<String, Object>> getGraphData(@PathVariable Long courseId) {
        Map<String, Object> graphData = relationService.getGraphData(courseId);
        return ApiResponse.success(graphData);
    }
}
