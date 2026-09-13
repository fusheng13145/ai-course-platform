package com.aicourse.service.impl;

import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.mapper.LearningPathMapper;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.service.LearningPathService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.service.component.learning.LearningPathGenerateComponent;
import com.aicourse.service.component.learning.LearningPathMasteryComponent;
import com.aicourse.service.component.learning.LearningPathQueryComponent;
import com.aicourse.service.component.learning.LearningPathStateComponent;
import com.aicourse.vo.LearningPathDetailVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 学习路径服务门面：掌握度推导/生成/状态管理/查询职责委托给 service.component 包下协作者组件。
 * <p>拆分自原 757 行单类（2026-08-29）。保留 extends ServiceImpl 以承载 IService 能力，
 * 事务注解保留在门面委托方法上。</p>
 */
@Service
public class LearningPathServiceImpl extends ServiceImpl<LearningPathMapper, LearningPath> implements LearningPathService {

    private final LearningPathMasteryComponent masteryComponent;
    private final LearningPathGenerateComponent generateComponent;
    private final LearningPathStateComponent stateComponent;
    private final LearningPathQueryComponent queryComponent;

    @Autowired
    public LearningPathServiceImpl(LearningPathMasteryComponent masteryComponent,
                                   LearningPathGenerateComponent generateComponent,
                                   LearningPathStateComponent stateComponent,
                                   LearningPathQueryComponent queryComponent) {
        this.masteryComponent = masteryComponent;
        this.generateComponent = generateComponent;
        this.stateComponent = stateComponent;
        this.queryComponent = queryComponent;
    }

    /**
     * 兼容构造器（临时）：组装真实组件 + 调用方注入的底层依赖。
     * <p>供旧单测沿用（原 3 参数签名），组件单测重构完成后移除。
     * 注意：组件持有的 LearningPathMapper 需由调用方另行注入（测试经 ReflectionTestUtils 同步 baseMapper）。</p>
     */
    @Deprecated
    public LearningPathServiceImpl(LearningPathNodeService nodeService,
                                   KnowledgePointService knowledgePointService,
                                   WrongQuestionService wrongQuestionService) {
        ObjectMapper objectMapper = new ObjectMapper();
        this.masteryComponent = new LearningPathMasteryComponent(knowledgePointService, wrongQuestionService);
        this.stateComponent = new LearningPathStateComponent(nodeService, null);
        this.queryComponent = new LearningPathQueryComponent(nodeService, null, objectMapper);
        this.generateComponent = new LearningPathGenerateComponent(knowledgePointService, nodeService, null,
                this.masteryComponent, this.stateComponent, objectMapper);
    }

    // ===================== 掌握度推导 =====================

    @Override
    @Transactional(readOnly = true)
    public Map<String, Double> deriveMastery(Long studentId, Long courseId) {
        return masteryComponent.deriveMasteryFromWrongQuestions(studentId, courseId);
    }

    // ===================== 生成 =====================

    @Override
    @Transactional(readOnly = true)
    public List<LearningPathNode> generateLearningPath(Long studentId, Long courseId) {
        return generateComponent.generateLearningPath(studentId, courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningPath generatePath(Long studentId, Long courseId, String pathName, String learningGoal) {
        return generateComponent.generatePath(studentId, courseId, pathName, learningGoal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningPath generatePathPersonalized(Long studentId, Long courseId, String pathName,
                                                  String learningGoal, Map<String, Double> masteryData) {
        return generateComponent.generatePathPersonalized(studentId, courseId, pathName, learningGoal, masteryData);
    }

    // ===================== 状态管理 =====================

    @Override
    @Transactional(readOnly = true)
    public LearningPath getActivePath(Long studentId, Long courseId) {
        return stateComponent.getActivePath(studentId, courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(Long pathId) {
        stateComponent.updateProgress(pathId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void advanceToNextStage(Long pathId) {
        stateComponent.advanceToNextStage(pathId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completePath(Long pathId) {
        stateComponent.completePath(pathId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pausePath(Long pathId) {
        stateComponent.pausePath(pathId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumePath(Long pathId) {
        stateComponent.resumePath(pathId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void estimateCompletionDate(Long pathId) {
        stateComponent.estimateCompletionDate(pathId);
    }

    // ===================== 查询 =====================

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDailyTasks(Long pathId) {
        return queryComponent.getDailyTasks(pathId);
    }

    @Override
    @Transactional(readOnly = true)
    public LearningPathDetailVO getPathDetail(Long pathId) {
        return queryComponent.getPathDetail(pathId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningPath> getStudentPaths(Long studentId, Long courseId) {
        return queryComponent.getStudentPaths(studentId, courseId);
    }
}
