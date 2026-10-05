package com.aicourse.service;

import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.vo.WrongQuestionGroupVO;
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
     * 记录错题（独立事务版本，修复 D3 事务传染）。
     *
     * <p>与 {@link #recordWrongAnswer} 行为完全一致，但以 {@code Propagation.REQUIRES_NEW} 开启独立事务。
     * 专供「测验判分」等外层已持有事务、且要求「错题收录失败绝不能连累主流程提交」的调用方使用：
     * 收录若抛异常只会回滚该独立事务，外层事务保持可提交，避免内层 {@code REQUIRED} 传播把共享事务标记为
     * rollback-only 后、外层 catch 吞异常继续 commit 触发 {@code UnexpectedRollbackException}。</p>
     *
     * @param studentId 学生ID
     * @param questionId 题目ID
     * @param courseId 课程ID
     * @param wrongAnswer 错误答案
     * @param sourceType 来源类型（EXAM/PRACTICE/HOMEWORK/QUIZ）
     * @return 错题记录
     */
    WrongQuestion recordWrongAnswerInNewTx(Long studentId, Long questionId, Long courseId, String wrongAnswer, String sourceType);

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
     * 按知识点分类的错题。
     *
     * <p>修复（2026-09-23，P2-8）：原返回 {@code Map<String, List<WrongQuestion>>}，键为
     * {@code question.knowledgePoints} 的原始 JSON 文本，与前端期望的
     * {@code Array<{knowledgePointId, knowledgePointName, questions}>} 不符，
     * 使前端增强逻辑静默失效。现改为结构化分组数组。</p>
     *
     * @param studentId 学生ID
     * @param courseId 课程ID（可选）
     * @return 按知识点分组的错题列表（按题目数量倒序）
     */
    List<WrongQuestionGroupVO> getByKnowledgePoints(Long studentId, Long courseId);

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
