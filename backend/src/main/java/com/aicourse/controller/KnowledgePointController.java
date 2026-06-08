package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识点及关联关系接口控制器
 */
@RestController
@RequestMapping("/knowledge-points")
public class KnowledgePointController {

    private final KnowledgePointService knowledgePointService;
    private final KnowledgePointRelationService relationService;

    public KnowledgePointController(KnowledgePointService knowledgePointService,
                                    KnowledgePointRelationService relationService) {
        this.knowledgePointService = knowledgePointService;
        this.relationService = relationService;
    }

    /**
     * 分页/条件查询知识点列表
     * @param courseId 课程ID
     * @param keyword 搜索关键词
     * @return 知识点集合
     */
    @GetMapping
    public ApiResponse<List<KnowledgePoint>> list(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<KnowledgePoint> queryWrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(courseId != null, KnowledgePoint::getCourseId, courseId)
                .like(keyword != null, KnowledgePoint::getName, keyword);
        List<KnowledgePoint> pointList = knowledgePointService.list(queryWrapper);
        return ApiResponse.success(pointList);
    }

    /**
     * 根据ID查询知识点详情
     * @param id 知识点ID
     * @return 知识点信息
     */
    @GetMapping("/{id}")
    public ApiResponse<KnowledgePoint> getById(@PathVariable Long id) {
        KnowledgePoint knowledgePoint = knowledgePointService.getById(id);
        return ApiResponse.success(knowledgePoint);
    }

    /**
     * 新增知识点（教师/管理员权限）
     * @param point 知识点信息
     * @return 新增知识点
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgePoint> create(@RequestBody KnowledgePoint point) {
        knowledgePointService.save(point);
        return ApiResponse.success("创建成功", point);
    }

    /**
     * 修改知识点（教师/管理员权限）
     * @param id 知识点ID
     * @param point 待更新数据
     * @return 更新后知识点
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgePoint> update(@PathVariable Long id, @RequestBody KnowledgePoint point) {
        point.setId(id);
        knowledgePointService.updateById(point);
        KnowledgePoint updatedPoint = knowledgePointService.getById(id);
        return ApiResponse.success("更新成功", updatedPoint);
    }

    /**
     * 删除知识点（教师/管理员权限）
     * @param id 知识点ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgePointService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 查询当前知识点的所有关联关系
     * @param id 知识点ID
     * @param relationType 关联类型
     * @return 节点与关联边数据
     */
    @GetMapping("/{id}/relations")
    public ApiResponse<Map<String, Object>> getRelations(
            @PathVariable Long id,
            @RequestParam(required = false) String relationType) {
        LambdaQueryWrapper<KnowledgePointRelation> queryWrapper = new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getSourceId, id)
                .or()
                .eq(KnowledgePointRelation::getTargetId, id);
        List<KnowledgePointRelation> relationList = relationService.list(queryWrapper);

        Set<Long> pointIdSet = new HashSet<>();
        for (KnowledgePointRelation relation : relationList) {
            pointIdSet.add(relation.getSourceId());
            pointIdSet.add(relation.getTargetId());
        }
        List<KnowledgePoint> nodeList = knowledgePointService.listByIds(pointIdSet);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("nodes", nodeList);
        resultMap.put("edges", relationList);
        return ApiResponse.success(resultMap);
    }

    /**
     * 新增知识点关联关系（教师/管理员权限）
     * @param relation 关联信息
     * @return 操作结果
     */
    @PostMapping("/relations")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> createRelation(@RequestBody KnowledgePointRelation relation) {
        relationService.save(relation);
        return ApiResponse.success("创建成功", null);
    }

    /**
     * 删除知识点关联关系（教师/管理员权限）
     * @param id 关联记录ID
     * @return 操作结果
     */
    @DeleteMapping("/relations/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> deleteRelation(@PathVariable Long id) {
        relationService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 获取课程知识点图谱全量数据
     * @param courseId 课程ID
     * @return 图谱节点、边及统计数据
     */
    @GetMapping("/graph")
    public ApiResponse<Map<String, Object>> graph(@RequestParam Long courseId) {
        LambdaQueryWrapper<KnowledgePoint> pointWrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId);
        List<KnowledgePoint> pointList = knowledgePointService.list(pointWrapper);
        List<KnowledgePointRelation> relationList = relationService.list();

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("nodes", pointList);
        dataMap.put("edges", relationList);
        dataMap.put("totalPoints", pointList.size());
        dataMap.put("totalRelations", relationList.size());
        return ApiResponse.success(dataMap);
    }

    // ==================== 知识图谱体系增强API ====================

    /**
     * 获取课程的完整知识树（层级结构）
     * @param courseId 课程ID
     * @return 树形结构知识点数据
     */
    @GetMapping("/tree/{courseId}")
    public ApiResponse<List<Map<String, Object>>> getKnowledgeTree(@PathVariable Long courseId) {
        List<Map<String, Object>> tree = knowledgePointService.getKnowledgeTree(courseId);
        return ApiResponse.success(tree);
    }

    /**
     * 获取某知识点的直接子节点
     * @param id 父知识点ID
     * @return 子节点列表
     */
    @GetMapping("/{id}/children")
    public ApiResponse<List<KnowledgePoint>> getChildPoints(@PathVariable Long id) {
        List<KnowledgePoint> children = knowledgePointService.getChildPoints(id);
        return ApiResponse.success(children);
    }

    /**
     * 获取知识点的前置依赖
     * @param id 知识点ID
     * @return 前置依赖知识点列表
     */
    @GetMapping("/{id}/prerequisites")
    public ApiResponse<List<KnowledgePoint>> getPrerequisites(@PathVariable Long id) {
        List<KnowledgePoint> prerequisites = knowledgePointService.getPrerequisites(id);
        return ApiResponse.success(prerequisites);
    }

    /**
     * 获取知识点的后续知识点（被谁依赖）
     * @param id 知识点ID
     * @return 后续知识点列表
     */
    @GetMapping("/{id}/subsequent")
    public ApiResponse<List<KnowledgePoint>> getSubsequentPoints(@PathVariable Long id) {
        List<KnowledgePoint> subsequent = knowledgePointService.getSubsequentPoints(id);
        return ApiResponse.success(subsequent);
    }

    /**
     * 计算学生对某知识点的掌握度
     * @param id 知识点ID
     * @param userId 学生ID
     * @return 掌握度百分比
     */
    @GetMapping("/{id}/mastery/{userId}")
    public ApiResponse<BigDecimal> calculateMastery(@PathVariable Long id, @PathVariable Long userId) {
        BigDecimal mastery = knowledgePointService.calculateMastery(id, userId);
        return ApiResponse.success(mastery);
    }

    /**
     * 获取课程的根级知识点
     * @param courseId 课程ID
     * @return 根级知识点列表
     */
    @GetMapping("/root/{courseId}")
    public ApiResponse<List<KnowledgePoint>> getRootPoints(@PathVariable Long courseId) {
        List<KnowledgePoint> rootPoints = knowledgePointService.getRootPoints(courseId);
        return ApiResponse.success(rootPoints);
    }
}