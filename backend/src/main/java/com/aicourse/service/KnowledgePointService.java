package com.aicourse.service;

import com.aicourse.entity.KnowledgePoint;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 知识点服务接口
 * 提供知识树构建、父子关系查询、前置依赖、掌握度计算等功能
 */
public interface KnowledgePointService extends IService<KnowledgePoint> {

    /**
     * 获取课程的完整知识树（层级结构，递归父子关系）
     * @param courseId 课程ID
     * @return 树形结构的知识点列表
     */
    List<Map<String, Object>> getKnowledgeTree(Long courseId);

    /**
     * 获取某知识点的直接子节点
     * @param parentId 父知识点ID
     * @return 子知识点列表（按orderIndex排序）
     */
    List<KnowledgePoint> getChildPoints(Long parentId);

    /**
     * 获取知识点的前置依赖（relationType=PREREQUISITE）
     * @param pointId 知识点ID
     * @return 前置依赖知识点列表
     */
    List<KnowledgePoint> getPrerequisites(Long pointId);

    /**
     * 获取知识点的后续知识点（被谁依赖）
     * @param pointId 知识点ID
     * @return 后续知识点列表
     */
    List<KnowledgePoint> getSubsequentPoints(Long pointId);

    /**
     * 计算知识点掌握度（基于错题本和考试成绩）
     * @param pointId 知识点ID
     * @param studentId 学生ID
     * @return 掌握度百分比（0-100）
     */
    BigDecimal calculateMastery(Long pointId, Long studentId);

    /**
     * 获取课程的根级知识点（parentId为null的）
     * @param courseId 课程ID
     * @return 根级知识点列表
     */
    List<KnowledgePoint> getRootPoints(Long courseId);

    /**
     * 创建知识点（校验difficultyLevel和importance的取值范围）
     * @param knowledgePoint 知识点实体（需包含courseId、name等必填字段）
     * @return 创建后的知识点
     */
    KnowledgePoint createWithValidation(KnowledgePoint knowledgePoint);

    /**
     * 获取知识点列表（支持按难度等级和重要度排序/筛选）
     * @param courseId 课程ID
     * @param difficultyLevel 难度等级筛选（可选，如"EASY"/"MEDIUM"/"HARD"）
     * @param minImportance 最小重要度（可选）
     * @param maxImportance 最大重要度（可选）
     * @param sortBy 排序字段（可选：importance/difficultyLevel/orderIndex）
     * @param isAsc 是否升序
     * @return 筛选排序后的知识点列表
     */
    List<KnowledgePoint> getListWithFilters(Long courseId, String difficultyLevel,
                                              Integer minImportance, Integer maxImportance,
                                              String sortBy, boolean isAsc);
}
