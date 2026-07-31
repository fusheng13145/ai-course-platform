package com.aicourse.controller;

import jakarta.validation.Valid;

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

import java.util.List;
import java.util.Map;
import com.aicourse.common.ApiCode;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 学习路线接口控制器
 */
@RestController
@RequestMapping("/learning-paths")
@Tag(name = "学习路径")
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
    public ApiResponse<LearningPath> create(@Valid @RequestBody LearningPath path, Authentication auth) {
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
    public ApiResponse<LearningPath> update(@PathVariable Long id, @Valid @RequestBody LearningPath path) {
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
    public ApiResponse<LearningPathNode> addNode(@PathVariable Long id, @Valid @RequestBody LearningPathNode node) {
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
    public ApiResponse<LearningPathNode> updateNode(@PathVariable Long nodeId, @Valid @RequestBody LearningPathNode node) {
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
            return ApiResponse.error(ApiCode.BAD_REQUEST, "用户ID不能为空");
        }
        // 掌握度推导、排序规则、节点构建等业务逻辑下沉至 Service
        List<LearningPathNode> nodes = learningPathService.generateLearningPath(userId, courseId);
        return ApiResponse.success(nodes);
    }

    public CourseEnrollmentService getEnrollmentService() {
        return enrollmentService;
    }

    // ==================== 学习路径体系增强API ====================

    /**
     * 获取当前活跃的学习路径
     * @param courseId 课程ID
     * @param auth 认证信息
     * @return 活跃学习路径
     */
    @GetMapping("/active/{courseId}")
    public ApiResponse<LearningPath> getActivePath(@PathVariable Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        LearningPath activePath = learningPathService.getActivePath(userId, courseId);
        return ApiResponse.success(activePath);
    }

    /**
     * 进入下一阶段
     * @param id 路径ID
     * @return 操作结果
     */
    @PostMapping("/{id}/advance")
    public ApiResponse<Void> advanceToNextStage(@PathVariable Long id) {
        learningPathService.advanceToNextStage(id);
        return ApiResponse.success("已进入下一阶段", null);
    }

    /**
     * 完成学习路径
     * @param id 路径ID
     * @return 操作结果
     */
    @PostMapping("/{id}/complete")
    public ApiResponse<Void> completePath(@PathVariable Long id) {
        learningPathService.completePath(id);
        return ApiResponse.success("路径已完成", null);
    }

    /**
     * 暂停学习路径
     * @param id 路径ID
     * @return 操作结果
     */
    @PostMapping("/{id}/pause")
    public ApiResponse<Void> pausePath(@PathVariable Long id) {
        learningPathService.pausePath(id);
        return ApiResponse.success("路径已暂停", null);
    }

    /**
     * 恢复学习路径
     * @param id 路径ID
     * @return 操作结果
     */
    @PostMapping("/{id}/resume")
    public ApiResponse<Void> resumePath(@PathVariable Long id) {
        learningPathService.resumePath(id);
        return ApiResponse.success("路径已恢复", null);
    }

    /**
     * 获取每日任务清单
     * @param id 路径ID
     * @return 每日任务列表
     */
    @GetMapping("/{id}/daily-tasks")
    public ApiResponse<List<Map<String, Object>>> getDailyTasks(@PathVariable Long id) {
        List<Map<String, Object>> dailyTasks = learningPathService.getDailyTasks(id);
        return ApiResponse.success(dailyTasks);
    }

    /**
     * 获取学习路径完整详情（含节点列表及统计信息）
     * @param id 路径ID
     * @return 详情数据（path + nodes + statusStats）
     */
    @GetMapping("/{id}/detail")
    public ApiResponse<Map<String, Object>> getPathDetail(@PathVariable Long id) {
        Map<String, Object> detail = learningPathService.getPathDetail(id);
        return ApiResponse.success(detail);
    }

    /**
     * 获取进度详情（重新计算并返回最新进度）
     * @param id 路径ID
     * @return 进度详情
     */
    @GetMapping("/{id}/progress")
    public ApiResponse<Map<String, Object>> getProgress(@PathVariable Long id) {
        // 先更新进度，再返回详情
        learningPathService.updateProgress(id);
        Map<String, Object> detail = learningPathService.getPathDetail(id);
        return ApiResponse.success(detail);
    }
}