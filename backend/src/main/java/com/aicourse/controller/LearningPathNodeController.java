package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import com.aicourse.dto.LearningPathNodeCompleteDTO;
import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.RoleUtil;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.service.LearningPathService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学习路线节点接口控制器
 * 提供节点的查询、开始、完成、跳过等操作API
 */
@RestController
@RequestMapping("/learning-path-nodes")
@Tag(name = "学习路径节点")
public class LearningPathNodeController {

    private static final Logger log = LoggerFactory.getLogger(LearningPathNodeController.class);

    private final LearningPathNodeService nodeService;
    private final LearningPathService learningPathService;

    public LearningPathNodeController(LearningPathNodeService nodeService, LearningPathService learningPathService) {
        this.nodeService = nodeService;
        this.learningPathService = learningPathService;
    }

    /**
     * 获取学习路径的所有节点（按orderIndex有序排列，owner 校验）
     * @param pathId 学习路径ID
     * @param auth 认证信息
     * @return 有序节点列表
     */
    @GetMapping("/path/{pathId}")
    public ApiResponse<List<LearningPathNode>> getPathNodes(@PathVariable Long pathId, Authentication auth) {
        assertPathOwner(auth, pathId);
        List<LearningPathNode> nodes = nodeService.getPathNodes(pathId);
        return ApiResponse.success(nodes);
    }

    /**
     * 开始学习某个节点（状态切换为IN_PROGRESS，owner 校验）
     * @param id 节点ID
     * @param auth 认证信息
     * @return 操作结果
     */
    @PostMapping("/{id}/start")
    public ApiResponse<Void> startNode(@PathVariable Long id, Authentication auth) {
        assertNodeOwner(auth, id);
        nodeService.startNode(id);
        return ApiResponse.success("已开始学习", null);
    }

    /**
     * 完成某个节点（更新状态、得分、耗时，owner 校验）
     * @param id 节点ID
     * @param params 包含score和actualTime的请求体
     * @param auth 认证信息
     * @return 操作结果
     */
    @PostMapping("/{id}/complete")
    public ApiResponse<Void> completeNode(@PathVariable Long id,
                                          @Valid @RequestBody LearningPathNodeCompleteDTO params,
                                          Authentication auth) {
        assertNodeOwner(auth, id);
        nodeService.completeNode(id, params.getScore(), params.getActualTime());
        return ApiResponse.success("节点已完成", null);
    }

    /**
     * 跳过某个节点（状态切换为SKIPPED，owner 校验）
     * @param id 节点ID
     * @param auth 认证信息
     * @return 操作结果
     */
    @PostMapping("/{id}/skip")
    public ApiResponse<Void> skipNode(@PathVariable Long id, Authentication auth) {
        assertNodeOwner(auth, id);
        nodeService.skipNode(id);
        return ApiResponse.success("已跳过该节点", null);
    }

    /**
     * 获取当前进行中的节点（owner 校验）
     * @param pathId 学习路径ID
     * @param auth 认证信息
     * @return 当前进行中的节点
     */
    @GetMapping("/current/{pathId}")
    public ApiResponse<LearningPathNode> getCurrentNode(@PathVariable Long pathId, Authentication auth) {
        assertPathOwner(auth, pathId);
        LearningPathNode currentNode = nodeService.getCurrentNode(pathId);
        return ApiResponse.success(currentNode);
    }

    // ==================== 访问控制 ====================

    /**
     * 学习路径归属校验：非管理员时，当前登录用户必须是该路径的所属学生。
     * 防御性处理未认证场景，避免空指针。
     * @param auth 认证信息
     * @param pathId 学习路径ID
     */
    private void assertPathOwner(Authentication auth, Long pathId) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new BusinessException("用户未认证");
        }
        if (RoleUtil.isAdmin(auth)) {
            return;
        }
        Long userId = (Long) auth.getPrincipal();
        LearningPath path = learningPathService.getById(pathId);
        if (path == null) {
            throw new BusinessException("学习路径不存在");
        }
        if (!path.getStudentId().equals(userId)) {
            throw new BusinessException("无权访问该学习路径");
        }
    }

    /**
     * 节点归属校验：先定位节点所属路径，再校验该路径归属。
     * 防御性处理未认证场景，避免空指针。
     * @param auth 认证信息
     * @param nodeId 节点ID
     */
    private void assertNodeOwner(Authentication auth, Long nodeId) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new BusinessException("用户未认证");
        }
        LearningPathNode node = nodeService.getById(nodeId);
        if (node == null) {
            throw new BusinessException("学习路径节点不存在");
        }
        assertPathOwner(auth, node.getLearningPathId());
    }
}
