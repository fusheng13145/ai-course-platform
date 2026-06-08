package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.service.LearningPathNodeService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 学习路线节点接口控制器
 * 提供节点的查询、开始、完成、跳过等操作API
 */
@RestController
@RequestMapping("/learning-path-nodes")
public class LearningPathNodeController {

    private final LearningPathNodeService nodeService;

    public LearningPathNodeController(LearningPathNodeService nodeService) {
        this.nodeService = nodeService;
    }

    /**
     * 获取学习路径的所有节点（按orderIndex有序排列）
     * @param pathId 学习路径ID
     * @return 有序节点列表
     */
    @GetMapping("/path/{pathId}")
    public ApiResponse<List<LearningPathNode>> getPathNodes(@PathVariable Long pathId) {
        List<LearningPathNode> nodes = nodeService.getPathNodes(pathId);
        return ApiResponse.success(nodes);
    }

    /**
     * 开始学习某个节点（状态切换为IN_PROGRESS）
     * @param id 节点ID
     * @return 操作结果
     */
    @PostMapping("/{id}/start")
    public ApiResponse<Void> startNode(@PathVariable Long id) {
        nodeService.startNode(id);
        return ApiResponse.success("已开始学习", null);
    }

    /**
     * 完成某个节点（更新状态、得分、耗时）
     * @param id 节点ID
     * @param params 包含score和actualTime的请求体
     * @return 操作结果
     */
    @PostMapping("/{id}/complete")
    public ApiResponse<Void> completeNode(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        BigDecimal score = new BigDecimal(params.get("score").toString());
        Integer actualTime = Integer.valueOf(params.get("actualTime").toString());
        nodeService.completeNode(id, score, actualTime);
        return ApiResponse.success("节点已完成", null);
    }

    /**
     * 跳过某个节点（状态切换为SKIPPED）
     * @param id 节点ID
     * @return 操作结果
     */
    @PostMapping("/{id}/skip")
    public ApiResponse<Void> skipNode(@PathVariable Long id) {
        nodeService.skipNode(id);
        return ApiResponse.success("已跳过该节点", null);
    }

    /**
     * 获取当前进行中的节点
     * @param pathId 学习路径ID
     * @return 当前进行中的节点
     */
    @GetMapping("/current/{pathId}")
    public ApiResponse<LearningPathNode> getCurrentNode(@PathVariable Long pathId) {
        LearningPathNode currentNode = nodeService.getCurrentNode(pathId);
        return ApiResponse.success(currentNode);
    }
}
