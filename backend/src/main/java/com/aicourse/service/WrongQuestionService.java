package com.aicourse.service;

import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.vo.WrongQuestionStatsVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;

/**
 * 错题本服务接口
 * 提供错题记录、掌握标记、练习、统计、推荐等核心功能
 */
public interface WrongQuestionService extends com.baomidou.mybatisplus.extension.service.IService<WrongQuestion> {

    /**
     * 记录错题（考试/练习时自动调用）
     * 查询是否已有该学生+题目的错题记录，有则更新，无则新建
     * @param studentId 学生ID
     * @param questionId 题目ID
     * @param courseId 课程ID
     * @param wrongAnswer 错误答案
     * @param sourceType 来源类型（EXAM/PRACTICE/HOMEWORK）
     * @return 错题记录
     */
    WrongQuestion recordWrongAnswer(Long studentId, Long questionId, Long courseId, String wrongAnswer, String sourceType);

    /**
     * 标记已掌握
     * @param wrongQuestionId 错题ID
     */
    void markMastered(Long wrongQuestionId);

    /**
     * 标记已掌握（带归属校验，修复 P1-2 IDOR：错题必须属于该学生）
     * @param wrongQuestionId 错题ID
     * @param studentId 当前登录学生ID
     */
    void markMasteredForStudent(Long wrongQuestionId, Long studentId);

    /**
     * 删除错题（带归属校验，修复 P1-2 IDOR：错题必须属于该学生）
     * @param wrongQuestionId 错题ID
     * @param studentId 当前登录学生ID
     */
    void deleteForStudent(Long wrongQuestionId, Long studentId);

    /**
     * 取消掌握标记（再次答错时调用）
     * @param wrongQuestionId 错题ID
     */
    void unmarkMastered(Long wrongQuestionId);

    /**
     * 更新练习次数
     * @param wrongQuestionId 错题ID
     */
    void incrementPracticeCount(Long wrongQuestionId);

    /**
     * 获取用户错题本（分页）
     * @param studentId 学生ID
     * @param courseId 课程ID（可选）
     * @param page 页码
     * @param size 每页大小
     * @return 分页错题列表
     */
    Page<WrongQuestion> getWrongQuestionBook(Long studentId, Long courseId, int page, int size);

    /**
     * 获取未掌握的错题
     * @param studentId 学生ID
     * @param courseId 课程ID（可选）
     * @return 未掌握的错题列表
     */
    List<WrongQuestion> getUnmastered(Long studentId, Long courseId);

    /**
     * 按知识点分类的错题
     * @param studentId 学生ID
     * @param courseId 课程ID（可选）
     * @return 按知识点分组的错题Map
     */
    Map<String, List<WrongQuestion>> getByKnowledgePoints(Long studentId, Long courseId);

    /**
     * 错题统计
     * @param studentId 学生ID
     * @param courseId 课程ID（可选）
     * @return 统计信息
     */
    WrongQuestionStatsVO getWrongQuestionStats(Long studentId, Long courseId);

    /**
     * 生成错题练习卷（随机选取N道未掌握错题对应的题目）
     * @param studentId 学生ID
     * @param courseId 课程ID（可选）
     * @param count 练习题数量
     * @return 题目列表
     */
    List<Question> generatePracticePaper(Long studentId, Long courseId, int count);

    /**
     * 推荐相关题目（基于同一知识点的其他题目）
     * @param wrongQuestionId 错题ID
     * @return 推荐的相关题目列表
     */
    List<Question> recommendRelatedQuestions(Long wrongQuestionId);

    /**
     * 添加错题（支持可选填写错因分析）
     * @param studentId 学生ID
     * @param questionId 题目ID
     * @param courseId 课程ID
     * @param wrongAnswer 错误答案
     * @param sourceType 来源类型
     * @param reason 错因分析（可选）
     * @return 错题记录
     */
    WrongQuestion addWrongQuestion(Long studentId, Long questionId, Long courseId,
                                    String wrongAnswer, String sourceType, String reason);
}
