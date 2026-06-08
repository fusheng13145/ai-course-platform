package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.service.LearningPathService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 学习路线接口控制器
 */
@RestController
@RequestMapping("/learning-paths")
public class LearningPathController {

    private final LearningPathService learningPathService;
    private final LearningPathNodeService learningPathNodeService;
    private final CourseEnrollmentService enrollmentService;

    public LearningPathController(LearningPathService learningPathService,
                                  LearningPathNodeService learningPathNodeService,
                                  CourseEnrollmentService enrollmentService) {
        this.learningPathService = learningPathService;
        this.learningPathNodeService = learningPathNodeService;
        this.enrollmentService = enrollmentService;
    }

    /**
     * 查询当前用户的学习路线
     * @param auth 认证信息
     * @return 学习路线列表
     */
    @GetMapping
    public ApiResponse<List<LearningPath>> myPaths(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<LearningPath> queryWrapper = new LambdaQueryWrapper<LearningPath>()
                .eq(LearningPath::getStudentId, userId);
        List<LearningPath> pathList = learningPathService.list(queryWrapper);
        return ApiResponse.success(pathList);
    }

    /**
     * 根据ID查询学习路线详情
     * @param id 路线ID
     * @return 学习路线信息
     */
    @GetMapping("/{id}")
    public ApiResponse<LearningPath> getById(@PathVariable Long id) {
        LearningPath learningPath = learningPathService.getById(id);
        return ApiResponse.success(learningPath);
    }

    /**
     * 新增学习路线（学生/管理员权限）
     * @param path 学习路线信息
     * @param auth 认证信息
     * @return 新增路线数据
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ApiResponse<LearningPath> create(@RequestBody LearningPath path, Authentication auth) {
        path.setStudentId((Long) auth.getPrincipal());
        learningPathService.save(path);
        return ApiResponse.success("创建成功", path);
    }

    /**
     * 修改学习路线（学生/管理员权限）
     * @param id 路线ID
     * @param path 待更新数据
     * @return 更新后路线信息
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ApiResponse<LearningPath> update(@PathVariable Long id, @RequestBody LearningPath path) {
        path.setId(id);
        learningPathService.updateById(path);
        LearningPath updatedPath = learningPathService.getById(id);
        return ApiResponse.success("更新成功", updatedPath);
    }

    /**
     * 查询路线下所有节点
     * @param id 路线ID
     * @return 节点列表
     */
    @GetMapping("/{id}/nodes")
    public ApiResponse<List<LearningPathNode>> getNodes(@PathVariable Long id) {
        LambdaQueryWrapper<LearningPathNode> queryWrapper = new LambdaQueryWrapper<LearningPathNode>()
                .eq(LearningPathNode::getLearningPathId, id);
        List<LearningPathNode> nodeList = learningPathNodeService.list(queryWrapper);
        return ApiResponse.success(nodeList);
    }

    /**
     * 新增路线节点
     * @param id 路线ID
     * @param node 节点信息
     * @return 新增节点数据
     */
    @PostMapping("/{id}/nodes")
    public ApiResponse<LearningPathNode> addNode(@PathVariable Long id, @RequestBody LearningPathNode node) {
        node.setLearningPathId(id);
        learningPathNodeService.save(node);
        return ApiResponse.success("添加成功", node);
    }

    /**
     * 修改路线节点
     * @param nodeId 节点ID
     * @param node 待更新数据
     * @return 更新后节点信息
     */
    @PutMapping("/nodes/{nodeId}")
    public ApiResponse<LearningPathNode> updateNode(@PathVariable Long nodeId, @RequestBody LearningPathNode node) {
        node.setId(nodeId);
        learningPathNodeService.updateById(node);
        LearningPathNode updatedNode = learningPathNodeService.getById(nodeId);
        return ApiResponse.success("更新成功", updatedNode);
    }

    /**
     * 自动生成学习节点
     * @param courseId 课程ID
     * @param auth 认证信息
     * @return 生成的节点列表
     */
    @GetMapping("/generate")
    public ApiResponse<List<LearningPathNode>> generate(@RequestParam Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (userId == null) {
            return ApiResponse.error(400, "用户ID不能为空");
        }

        List<LearningPathNode> nodes = new ArrayList<>();
        String[] topics = {"基础知识学习", "核心概念掌握", "实践应用练习", "综合测试评估"};

        for (int i = 0; i < topics.length; i++) {
            LearningPathNode node = new LearningPathNode();
            node.setTitle(topics[i]);
            node.setNodeType("study");
            node.setStatus("PENDING");
            node.setOrderIndex(i);
            node.setEstimatedTime(60);
            nodes.add(node);
        }
        return ApiResponse.success(nodes);
    }

    public CourseEnrollmentService getEnrollmentService() {
        return enrollmentService;
    }
}