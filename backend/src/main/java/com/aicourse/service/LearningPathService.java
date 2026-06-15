package com.aicourse.service;

import com.aicourse.entity.LearningPath;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 学习路线服务接口
 * 提供学习路径的进度管理、阶段控制、任务解析及详情查询等功能
 */
public interface LearningPathService extends IService<LearningPath> {

    /**
     * 获取学生的活跃学习路径（指定课程下状态为ACTIVE的路径）
     * @param studentId 学生ID
     * @param courseId 课程ID
     * @return 活跃的学习路径，无则返回null
     */
    LearningPath getActivePath(Long studentId, Long courseId);

    /**
     * 更新整体进度（根据节点完成情况重算completionRate）
     * @param pathId 学习路径ID
     */
    void updateProgress(Long pathId);

    /**
     * 进入下一阶段
     * @param pathId 学习路径ID
     */
    void advanceToNextStage(Long pathId);

    /**
     * 完成学习路径
     * @param pathId 学习路径ID
     */
    void completePath(Long pathId);

    /**
     * 暂停学习路径
     * @param pathId 学习路径ID
     */
    void pausePath(Long pathId);

    /**
     * 恢复学习路径
     * @param pathId 学习路径ID
     */
    void resumePath(Long pathId);

    /**
     * 获取每日任务清单（从dailyTasks JSON解析）
     * @param pathId 学习路径ID
     * @return 每日任务列表
     */
    List<Map<String, Object>> getDailyTasks(Long pathId);

    /**
     * 获取学习路径完整详情（含节点列表及统计信息）
     * @param pathId 学习路径ID
     * @return 详情Map（包含path信息、nodes、统计数据等）
     */
    Map<String, Object> getPathDetail(Long pathId);

    /**
     * 获取学生的学习路径列表（指定课程下所有路径）
     * @param studentId 学生ID
     * @param courseId 课程ID
     * @return 学习路径列表
     */
    List<LearningPath> getStudentPaths(Long studentId, Long courseId);

    /**
     * 根据实际速度预估完成日期（基于已完成节点的平均耗时推算）
     * @param pathId 学习路径ID
     */
    void estimateCompletionDate(Long pathId);

    /**
     * 生成学习路径（根据课程知识点自动生成，初始化stages和dailyTasks的JSON结构）
     * @param studentId 学生ID
     * @param courseId 课程ID
     * @param pathName 路线名称
     * @param learningGoal 学习目标
     * @return 新创建的学习路径
     */
    LearningPath generatePath(Long studentId, Long courseId, String pathName, String learningGoal);
}
