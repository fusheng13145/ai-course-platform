package com.aicourse.application.learning;

import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.service.LearningPathService;
import com.aicourse.vo.LearningPathVOs;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 学习路线读口径应用服务（3E-05E）。
 *
 * <p>从 {@code LearningPathController} 下移 2 处 {@code LambdaQueryWrapper}。</p>
 *
 * <p><b>[登记 DEF · 重复造车]</b> 既有 {@code LearningPathService#getStudentPaths(studentId, courseId)}
 * 与 {@code LearningPathNodeService#getPathNodes(pathId)} 已经存在，但控制器没有复用：</p>
 * <ul>
 *   <li>{@code getStudentPaths} 内部是 {@code .eq(courseId, null)} —— MP 会生成 {@code course_id = null}，
 *       与「不限课程」语义不等价（匹配不到任何行），因此<b>不能</b>用 {@code (userId, null)} 直接替换本端点；</li>
 *   <li>{@code getPathNodes} 额外带 {@code orderByAsc(orderIndex)}，而本端点原口径<b>不带排序</b>；
 *       直接替换会改变响应顺序。</li>
 * </ul>
 * <p>本批只做「原样下移」，两条口径是否统一（排序 + null 分支修正）登记待裁定。</p>
 */
@Service
public class LearningPathApplicationService {

    private final LearningPathService learningPathService;
    private final LearningPathNodeService learningPathNodeService;

    public LearningPathApplicationService(LearningPathService learningPathService,
                                          LearningPathNodeService learningPathNodeService) {
        this.learningPathService = learningPathService;
        this.learningPathNodeService = learningPathNodeService;
    }

    /** 当前用户的学习路线（按学生归属，原实现不带排序）。 */
    public List<LearningPathVOs.PathVO> myPaths(Long userId) {
        return LearningPathVOs.PathVO.from(learningPathService.list(
                new LambdaQueryWrapper<LearningPath>().eq(LearningPath::getStudentId, userId)));
    }

    /** 某路线的节点列表（原实现不带排序）。 */
    public List<LearningPathVOs.NodeVO> nodes(Long pathId) {
        return LearningPathVOs.NodeVO.from(learningPathNodeService.list(
                new LambdaQueryWrapper<LearningPathNode>().eq(LearningPathNode::getLearningPathId, pathId)));
    }
}
