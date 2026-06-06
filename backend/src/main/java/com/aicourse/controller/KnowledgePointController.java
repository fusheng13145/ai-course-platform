package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/knowledge")
public class KnowledgePointController {
    private final KnowledgePointService knowledgePointService;
    private final KnowledgePointRelationService relationService;

    public KnowledgePointController(KnowledgePointService knowledgePointService,
                                    KnowledgePointRelationService relationService) {
        this.knowledgePointService = knowledgePointService;
        this.relationService = relationService;
    }

    @GetMapping("/points")
    public ApiResponse<List<KnowledgePoint>> list(@RequestParam(required = false) Long courseId,
                                                   @RequestParam(required = false) String keyword) {
        return ApiResponse.success(knowledgePointService.list(
            new LambdaQueryWrapper<KnowledgePoint>()
                .eq(courseId != null, KnowledgePoint::getCourseId, courseId)
                .like(keyword != null, KnowledgePoint::getName, keyword)));
    }

    @GetMapping("/points/{id}")
    public ApiResponse<KnowledgePoint> getById(@PathVariable Long id) {
        return ApiResponse.success(knowledgePointService.getById(id));
    }

    @PostMapping("/points")
    public ApiResponse<KnowledgePoint> create(@RequestBody KnowledgePoint point) {
        knowledgePointService.save(point);
        return ApiResponse.success("创建成功", point);
    }

    @PutMapping("/points/{id}")
    public ApiResponse<KnowledgePoint> update(@PathVariable Long id, @RequestBody KnowledgePoint point) {
        point.setId(id);
        knowledgePointService.updateById(point);
        return ApiResponse.success("更新成功", knowledgePointService.getById(id));
    }

    @DeleteMapping("/points/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgePointService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/points/{id}/relations")
    public ApiResponse<Map<String, Object>> getRelations(@PathVariable Long id,
                                                          @RequestParam(required = false) String relationType) {
        List<KnowledgePointRelation> relations = relationService.list(
            new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getSourceId, id)
                .or().eq(KnowledgePointRelation::getTargetId, id));
        
        Set<Long> ids = new HashSet<>();
        for (KnowledgePointRelation r : relations) {
            ids.add(r.getSourceId());
            ids.add(r.getTargetId());
        }
        List<KnowledgePoint> nodes = knowledgePointService.listByIds(ids);
        
        Map<String, Object> data = new HashMap<>();
        data.put("nodes", nodes);
        data.put("edges", relations);
        return ApiResponse.success(data);
    }

    @PostMapping("/relations")
    public ApiResponse<Void> createRelation(@RequestBody KnowledgePointRelation relation) {
        relationService.save(relation);
        return ApiResponse.success("创建成功", null);
    }

    @DeleteMapping("/relations/{id}")
    public ApiResponse<Void> deleteRelation(@PathVariable Long id) {
        relationService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(@RequestParam(required = false) Long courseId) {
        List<KnowledgePoint> points = knowledgePointService.list(
            new LambdaQueryWrapper<KnowledgePoint>().eq(courseId != null, KnowledgePoint::getCourseId, courseId));
        Map<String, Object> data = new HashMap<>();
        data.put("totalPoints", points.size());
        data.put("totalRelations", relationService.count());
        return ApiResponse.success(data);
    }
}
