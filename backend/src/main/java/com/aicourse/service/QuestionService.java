package com.aicourse.service;

import com.aicourse.entity.Question;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;

/**
 * 题库题目服务接口
 * 提供题目创建、AI生成、审核、搜索、统计等核心功能
 */
public interface QuestionService extends com.baomidou.mybatisplus.extension.service.IService<Question> {

    /**
     * 创建题目（含知识点关联处理）
     * @param question 题目信息
     * @return 创建后的题目
     */
    Question createQuestion(Question question);

    /**
     * AI生成题目（对接AIService）
     * @param courseId 课程ID
     * @param type 题目类型
     * @param difficulty 难度等级
     * @param knowledgePointNames 知识点名称
     * @return 生成的题目
     */
    Question generateByAI(Long courseId, String type, String difficulty, String knowledgePointNames);

    /**
     * 题目审核
     * @param questionId 题目ID
     * @param approved 是否通过审核
     */
    void reviewQuestion(Long questionId, boolean approved);

    /**
     * 复制题目（创建变式题）
     * @param sourceQuestionId 源题目ID
     * @return 复制后的新题目
     */
    Question copyQuestion(Long sourceQuestionId);

    /**
     * 智能搜索（全文检索+筛选）
     * @param courseId 课程ID（可选）
     * @param type 题目类型（可选）
     * @param difficulty 难度等级（可选）
     * @param keyword 关键词（模糊匹配题干）
     * @param page 页码
     * @param size 每页大小
     * @return 分页题目列表
     */
    Page<Question> searchQuestions(Long courseId, String type, String difficulty, String keyword, int page, int size);

    /**
     * 获取题目详情（含选项解析后的结构化数据）
     * @param questionId 题目ID
     * @return 题目详情Map
     */
    Map<String, Object> getQuestionDetail(Long questionId);

    /**
     * 随机抽取题目（用于组卷）
     * @param courseId 课程ID
     * @param type 题目类型（可选）
     * @param difficulty 难度等级（可选）
     * @param count 抽取数量
     * @return 题目列表
     */
    List<Question> randomSelect(Long courseId, String type, String difficulty, int count);

    /**
     * 按知识点获取题目
     * @param knowledgePointId 知识点ID
     * @return 题目列表
     */
    List<Question> getByKnowledgePoint(Long knowledgePointId);

    /**
     * 更新正确率（批阅后回调）
     * @param questionId 题目ID
     * @param isCorrect 本次是否正确
     */
    void updateCorrectRate(Long questionId, boolean isCorrect);

    /**
     * 获取题目使用统计
     * @param questionId 题目ID
     * @return 统计信息Map
     */
    Map<String, Object> getQuestionStats(Long questionId);

    /**
     * 获取易错题TOP N
     * @param courseId 课程ID
     * @param limit 返回数量
     * @return 易错题列表
     */
    List<Question> getErrorProneQuestions(Long courseId, int limit);
}
