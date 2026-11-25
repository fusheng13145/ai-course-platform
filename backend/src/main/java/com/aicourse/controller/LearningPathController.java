package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.ApiCode;
import com.aicourse.dto.LearningPathNodeSaveDTO;
import com.aicourse.dto.LearningPathSaveDTO;
import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.RoleUtil;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.service.LearningPathService;
import com.aicourse.vo.LearningPathDetailVO;
import com.aicourse.vo.LearningPathVOs;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 学习路线接口控制器
 */
@RestController
@RequestMapping("/learning-paths")
@Tag(name = "学习路径")
public class LearningPathController {

    private static final Logger log = LoggerFactory.getLogger(LearningPathController.class);

    private final LearningPathService learningPathService;
    private final LearningPathNodeService learningPathNodeService;
    private final CourseEnrollmentService enrollmentService;
    private final com.aicourse.application.learning.LearningPathApplicationService learningPathApplicationService;

    public LearningPathController(LearningPathService learningPathService,
                                  LearningPathNodeService learningPathNodeService,
                                  CourseEnrollmentService enrollmentService,
                                  com.aicourse.application.learning.LearningPathApplicationService
                                          learningPathApplicationService) {
        this.learningPathService = learningPathService;
        this.learningPathNodeService = learningPathNodeService;
        this.enrollmentService = enrollmentService;
        this.learningPathApplicationService = learningPathApplicationService;
    }

    /**
     * 查询当前用户的学习路线
     * @param auth 认证信息
     * @return 学习路线列表
     */
    @GetMapping
    public ApiResponse<List<LearningPathVOs.PathVO>> myPaths(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(learningPathApplicationService.myPaths(userId));
    }

    /**
     * 根据ID查询学习路线详情（owner 校验：仅本人或管理员可访问）
     * @param id 路线ID
     * @param auth 认证信息
     * @return 学习路线信息
     */
    @GetMapping("/{id}")
    public ApiResponse<LearningPathVOs.PathVO> getById(@PathVariable Long id, Authentication auth) {
        LearningPath learningPath = assertPathOwner(auth, id);
        return ApiResponse.success(LearningPathVOs.PathVO.from(learningPath));
    }

    /**
     * 新增学习路线（学生/管理员权限）
     * <p>入参只接受路径业务描述字段：{@code studentId} 恒取登录用户，{@code id}／进度状态／
     * {@code completionRate} 等由服务端与专用推进接口维护，客户端传入一律忽略。</p>
     * @param dto 学习路线信息
     * @param auth 认证信息
     * @return 新增路线数据
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ApiResponse<LearningPathVOs.PathVO> create(@Valid @RequestBody LearningPathSaveDTO dto, Authentication auth) {
        LearningPath path = dto.toEntity();
        path.setStudentId((Long) auth.getPrincipal());
        learningPathService.save(path);
        return ApiResponse.success("创建成功", LearningPathVOs.PathVO.from(path));
    }

    /**
     * 修改学习路线（owner 校验：仅本人或管理员可修改，防止篡改他人路径）
     * <p>入参为局部更新语义（{@code null} 表示不改），{@code studentId}／进度状态字段不接受客户端传入，
     * 进度结论只能由 pause/resume/complete/advance 专用端点改写。</p>
     * @param id 路线ID
     * @param dto 待更新数据
     * @param auth 认证信息
     * @return 更新后路线信息
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ApiResponse<LearningPathVOs.PathVO> update(@PathVariable Long id, @Valid @RequestBody LearningPathSaveDTO dto, Authentication auth) {
        assertPathOwner(auth, id);
        LearningPath patch = dto.toEntity();
        patch.setId(id);
        learningPathService.updateById(patch);
        return ApiResponse.success("更新成功", LearningPathVOs.PathVO.from(patch));
    }

    /**
     * 查询路线下所有节点（owner 校验）
     * @param id 路线ID
     * @param auth 认证信息
     * @return 节点列表
     */
    @GetMapping("/{id}/nodes")
    public ApiResponse<List<LearningPathVOs.NodeVO>> getNodes(@PathVariable Long id, Authentication auth) {
        assertPathOwner(auth, id);
        return ApiResponse.success(learningPathApplicationService.nodes(id));
    }

    /**
     * 新增路线节点（owner 校验：只能给自己的路线加节点）
     * <p>入参只接受节点编排字段：{@code learningPathId} 恒取路径 ID，完成状态／得分／实际用时
     * 只能由 {@code LearningPathNodeController} 的 start/complete/skip 端点写入。</p>
     * @param id 路线ID
     * @param dto 节点信息
     * @param auth 认证信息
     * @return 新增节点数据
     */
    @PostMapping("/{id}/nodes")
    public ApiResponse<LearningPathVOs.NodeVO> addNode(@PathVariable Long id, @Valid @RequestBody LearningPathNodeSaveDTO dto, Authentication auth) {
        assertPathOwner(auth, id);
        LearningPathNode node = dto.toEntity();
        node.setLearningPathId(id);
        learningPathNodeService.save(node);
        return ApiResponse.success("添加成功", LearningPathVOs.NodeVO.from(node));
    }

    /**
     * 修改路线节点（owner 校验：仅节点所属路线的主人或管理员可改）
     * <p>局部更新语义；{@code learningPathId}／完成状态不接受客户端传入，防止把节点改挂到他人路径或伪造完成。</p>
     * @param nodeId 节点ID
     * @param dto 待更新数据
     * @param auth 认证信息
     * @return 更新后节点信息
     */
    @PutMapping("/nodes/{nodeId}")
    public ApiResponse<LearningPathVOs.NodeVO> updateNode(@PathVariable Long nodeId, @Valid @RequestBody LearningPathNodeSaveDTO dto, Authentication auth) {
        LearningPathNode existing = learningPathNodeService.getById(nodeId);
        if (existing == null) {
            throw new BusinessException("学习路径节点不存在");
        }
        assertPathOwner(auth, existing.getLearningPathId());
        LearningPathNode patch = dto.toEntity();
        patch.setId(nodeId);
        learningPathNodeService.updateById(patch);
        return ApiResponse.success("更新成功", LearningPathVOs.NodeVO.from(patch));
    }

    /**
     * 自动生成学习节点
     * @param courseId 课程ID
     * @param auth 认证信息
     * @return 生成的节点列表
     */
    @GetMapping("/generate")
    public ApiResponse<List<LearningPathVOs.NodeVO>> generate(@RequestParam Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (userId == null) {
            return ApiResponse.error(ApiCode.BAD_REQUEST, "用户ID不能为空");
        }
        List<LearningPathNode> nodes = learningPathService.generateLearningPath(userId, courseId);
        return ApiResponse.success(LearningPathVOs.NodeVO.from(nodes));
    }

    public CourseEnrollmentService getEnrollmentService() {
        return enrollmentService;
    }

    // ==================== 学习路径体系增强API ====================

    /**
     * 获取当前活跃的学习路径（owner 校验）
     * @param courseId 课程ID
     * @param auth 认证信息
     * @return 活跃学习路径
     */
    @GetMapping("/active/{courseId}")
    public ApiResponse<LearningPathVOs.PathVO> getActivePath(@PathVariable Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        LearningPath activePath = learningPathService.getActivePath(userId, courseId);
        return ApiResponse.success(LearningPathVOs.PathVO.from(activePath));
    }

    /**
     * 进入下一阶段（owner 校验：仅本人或管理员可推进）
     * @param id 路径ID
     * @param auth 认证信息
     * @return 操作结果
     */
    @PostMapping("/{id}/advance")
    public ApiResponse<Void> advanceToNextStage(@PathVariable Long id, Authentication auth) {
        assertPathOwner(auth, id);
        learningPathService.advanceToNextStage(id);
        return ApiResponse.success("已进入下一阶段", null);
    }

    /**
     * 完成学习路径（owner 校验）
     * @param id 路径ID
     * @param auth 认证信息
     * @return 操作结果
     */
    @PostMapping("/{id}/complete")
    public ApiResponse<Void> completePath(@PathVariable Long id, Authentication auth) {
        assertPathOwner(auth, id);
        learningPathService.completePath(id);
        return ApiResponse.success("路径已完成", null);
    }

    /**
     * 暂停学习路径（owner 校验）
     * @param id 路径ID
     * @param auth 认证信息
     * @return 操作结果
     */
    @PostMapping("/{id}/pause")
    public ApiResponse<Void> pausePath(@PathVariable Long id, Authentication auth) {
        assertPathOwner(auth, id);
        learningPathService.pausePath(id);
        return ApiResponse.success("路径已暂停", null);
    }

    /**
     * 恢复学习路径（owner 校验）
     * @param id 路径ID
     * @param auth 认证信息
     * @return 操作结果
     */
    @PostMapping("/{id}/resume")
    public ApiResponse<Void> resumePath(@PathVariable Long id, Authentication auth) {
        assertPathOwner(auth, id);
        learningPathService.resumePath(id);
        return ApiResponse.success("路径已恢复", null);
    }

    /**
     * 获取每日任务清单（owner 校验）
     * @param id 路径ID
     * @param auth 认证信息
     * @return 每日任务列表
     */
    @GetMapping("/{id}/daily-tasks")
    public ApiResponse<List<Map<String, Object>>> getDailyTasks(@PathVariable Long id, Authentication auth) {
        assertPathOwner(auth, id);
        List<Map<String, Object>> dailyTasks = learningPathService.getDailyTasks(id);
        return ApiResponse.success(dailyTasks);
    }

    /**
     * 获取学习路径完整详情（owner 校验）
     * @param id 路径ID
     * @param auth 认证信息
     * @return 详情数据（path + nodes + statusStats）
     */
    @GetMapping("/{id}/detail")
    public ApiResponse<LearningPathDetailVO> getPathDetail(@PathVariable Long id, Authentication auth) {
        assertPathOwner(auth, id);
        LearningPathDetailVO detail = learningPathService.getPathDetail(id);
        return ApiResponse.success(detail);
    }

    /**
     * 获取进度详情（owner 校验）
     * @param id 路径ID
     * @param auth 认证信息
     * @return 进度详情
     */
    @GetMapping("/{id}/progress")
    public ApiResponse<LearningPathDetailVO> getProgress(@PathVariable Long id, Authentication auth) {
        assertPathOwner(auth, id);
        learningPathService.updateProgress(id);
        LearningPathDetailVO detail = learningPathService.getPathDetail(id);
        return ApiResponse.success(detail);
    }

    // ==================== 访问控制 ====================

    /**
     * 学习路径归属校验：非管理员时，当前登录用户必须是该路径的所属学生，否则拒绝访问。
     * 防御性处理未认证场景（auth 为 null 时直接拒绝），避免 {@code (Long) auth.getPrincipal()} 空指针。
     * @param auth 认证信息
     * @param pathId 学习路径ID
     */
    private LearningPath assertPathOwner(Authentication auth, Long pathId) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new BusinessException("用户未认证");
        }
        if (RoleUtil.isAdmin(auth)) {
            return learningPathService.getById(pathId);
        }
        Long userId = (Long) auth.getPrincipal();
        LearningPath path = learningPathService.getById(pathId);
        if (path == null) {
            throw new BusinessException("学习路径不存在");
        }
        if (!path.getStudentId().equals(userId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权访问该学习路径");
        }
        return path;
    }
}
