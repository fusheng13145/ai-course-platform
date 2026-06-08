package com.aicourse.service.impl;

import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.WrongQuestionMapper;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 错题本服务实现类
 * 实现错题记录、掌握管理、练习、统计、推荐等核心业务逻辑
 */
@Service
public class WrongQuestionServiceImpl extends ServiceImpl<WrongQuestionMapper, WrongQuestion> implements WrongQuestionService {

    private final QuestionService questionService;

    public WrongQuestionServiceImpl(QuestionService questionService) {
        this.questionService = questionService;
    }

    /**
     * 记录错题（考试/练习时自动调用）
     * 查询是否已有该学生+题目的错题记录，有则更新，无则新建
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WrongQuestion recordWrongAnswer(Long studentId, Long questionId, Long courseId, String wrongAnswer, String sourceType) {
        // 查询是否已有该学生+题目的错题记录
        LambdaQueryWrapper<WrongQuestion> queryWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(WrongQuestion::getQuestionId, questionId);

        WrongQuestion existing = this.getOne(queryWrapper);

        if (existing != null) {
            // 已有记录，更新错误答案、练习次数和最后练习时间
            existing.setWrongAnswer(wrongAnswer);
            existing.setPracticeCount((existing.getPracticeCount() != null ? existing.getPracticeCount() : 0) + 1);
            existing.setLastPracticedAt(LocalDateTime.now());
            // 再次答错，取消掌握标记
            existing.setMastered(false);
            this.updateById(existing);
            return existing;
        } else {
            // 新建错题记录
            WrongQuestion wrongQuestion = new WrongQuestion();
            wrongQuestion.setStudentId(studentId);
            wrongQuestion.setQuestionId(questionId);
            wrongQuestion.setCourseId(courseId);
            wrongQuestion.setWrongAnswer(wrongAnswer);
            wrongQuestion.setSourceType(sourceType);
            wrongQuestion.setMastered(false);
            wrongQuestion.setPracticeCount(1);
            wrongQuestion.setLastPracticedAt(LocalDateTime.now());
            this.save(wrongQuestion);
            return wrongQuestion;
        }
    }

    /**
     * 标记已掌握
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markMastered(Long wrongQuestionId) {
        WrongQuestion wrongQuestion = this.getById(wrongQuestionId);
        if (wrongQuestion == null) {
            throw new BusinessException("错题记录不存在");
        }
        wrongQuestion.setMastered(true);
        this.updateById(wrongQuestion);
    }

    /**
     * 取消掌握标记（再次答错时调用）
     * 同时增加练习次数表示再次犯错
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unmarkMastered(Long wrongQuestionId) {
        WrongQuestion wrongQuestion = this.getById(wrongQuestionId);
        if (wrongQuestion == null) {
            throw new BusinessException("错题记录不存在");
        }
        wrongQuestion.setMastered(false);
        // 增加练习次数，表示再次犯错
        wrongQuestion.setPracticeCount((wrongQuestion.getPracticeCount() != null ? wrongQuestion.getPracticeCount() : 0) + 1);
        wrongQuestion.setLastPracticedAt(LocalDateTime.now());
        this.updateById(wrongQuestion);
    }

    /**
     * 更新练习次数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementPracticeCount(Long wrongQuestionId) {
        WrongQuestion wrongQuestion = this.getById(wrongQuestionId);
        if (wrongQuestion == null) {
            throw new BusinessException("错题记录不存在");
        }
        wrongQuestion.setPracticeCount((wrongQuestion.getPracticeCount() != null ? wrongQuestion.getPracticeCount() : 0) + 1);
        wrongQuestion.setLastPracticedAt(LocalDateTime.now());
        this.updateById(wrongQuestion);
    }

    /**
     * 获取用户错题本（分页）
     */
    @Override
    public Page<WrongQuestion> getWrongQuestionBook(Long studentId, Long courseId, int page, int size) {
        LambdaQueryWrapper<WrongQuestion> queryWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(courseId != null, WrongQuestion::getCourseId, courseId)
                .orderByDesc(WrongQuestion::getLastPracticedAt);

        return this.page(Page.of(page, size), queryWrapper);
    }

    /**
     * 获取未掌握的错题
     */
    @Override
    public List<WrongQuestion> getUnmastered(Long studentId, Long courseId) {
        LambdaQueryWrapper<WrongQuestion> queryWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(courseId != null, WrongQuestion::getCourseId, courseId)
                .eq(WrongQuestion::getMastered, false)
                .orderByAsc(WrongQuestion::getLastPracticedAt);  // 优先显示最早未掌握的

        return this.list(queryWrapper);
    }

    /**
     * 按知识点分类的错题
     * 通过关联题目获取知识点信息进行分组
     */
    @Override
    public Map<String, List<WrongQuestion>> getByKnowledgePoints(Long studentId, Long courseId) {
        // 查询该学生的所有错题
        LambdaQueryWrapper<WrongQuestion> queryWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(courseId != null, WrongQuestion::getCourseId, courseId)
                .orderByDesc(WrongQuestion::getLastPracticedAt);

        List<WrongQuestion> wrongQuestions = this.list(queryWrapper);
        Map<String, List<WrongQuestion>> result = new LinkedHashMap<>();

        // 按题目知识点分组
        for (WrongQuestion wq : wrongQuestions) {
            Question question = questionService.getById(wq.getQuestionId());
            String kpKey = "未知知识点";
            if (question != null && question.getKnowledgePoints() != null && !question.getKnowledgePoints().isEmpty()) {
                kpKey = question.getKnowledgePoints();
            }
            result.computeIfAbsent(kpKey, k -> new ArrayList<>()).add(wq);
        }

        return result;
    }

    /**
     * 错题统计
     * 返回总数、已掌握数、未掌握数、按来源分类等统计数据
     */
    @Override
    public Map<String, Object> getWrongQuestionStats(Long studentId, Long courseId) {
        Map<String, Object> stats = new HashMap<>();

        // 总错题数
        LambdaQueryWrapper<WrongQuestion> totalWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(courseId != null, WrongQuestion::getCourseId, courseId);
        long total = this.count(totalWrapper);
        stats.put("total", total);

        // 已掌握数
        LambdaQueryWrapper<WrongQuestion> masteredWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(courseId != null, WrongQuestion::getCourseId, courseId)
                .eq(WrongQuestion::getMastered, true);
        long mastered = this.count(masteredWrapper);
        stats.put("mastered", mastered);

        // 未掌握数
        stats.put("unmastered", total - mastered);

        // 掌握率
        double masteryRate = total > 0 ? (double) mastered / total * 100 : 0;
        stats.put("masteryRate", Math.round(masteryRate * 100.0) / 100.0);

        // 按来源类型分组统计
        List<WrongQuestion> allWrongQuestions = this.list(totalWrapper);
        Map<String, Long> sourceTypeStats = allWrongQuestions.stream()
                .collect(Collectors.groupingBy(
                        wq -> wq.getSourceType() != null ? wq.getSourceType() : "OTHER",
                        Collectors.counting()
                ));
        stats.put("sourceTypeStats", sourceTypeStats);

        return stats;
    }

    /**
     * 生成错题练习卷（随机选取N道未掌握错题对应的题目）
     */
    @Override
    public List<Question> generatePracticePaper(Long studentId, Long courseId, int count) {
        // 查询未掌握的错题
        List<WrongQuestion> unmasteredList = getUnmastered(studentId, courseId);

        // 限制数量
        if (unmasteredList.size() > count) {
            // 随机打乱并取前N个
            Collections.shuffle(unmasteredList);
            unmasteredList = unmasteredList.subList(0, count);
        }

        // 提取questionId列表
        List<Long> questionIds = unmasteredList.stream()
                .map(WrongQuestion::getQuestionId)
                .collect(Collectors.toList());

        if (questionIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量查询题目
        LambdaQueryWrapper<Question> questionWrapper = new LambdaQueryWrapper<Question>()
                .in(Question::getId, questionIds)
                .eq(Question::getIsActive, true);

        List<Question> questions = questionService.list(questionWrapper);

        // 打乱顺序返回
        Collections.shuffle(questions);
        return questions;
    }

    /**
     * 推荐相关题目（基于同一知识点的其他题目）
     * 查询错题对应的Question → 获取其knowledgePoints → 查询相同知识点的其他题目 → 排除已掌握的
     */
    @Override
    public List<Question> recommendRelatedQuestions(Long wrongQuestionId) {
        // 获取错题记录
        WrongQuestion wrongQuestion = this.getById(wrongQuestionId);
        if (wrongQuestion == null) {
            throw new BusinessException("错题记录不存在");
        }

        // 获取错题对应的题目
        Question question = questionService.getById(wrongQuestion.getQuestionId());
        if (question == null || question.getKnowledgePoints() == null || question.getKnowledgePoints().isEmpty()) {
            return new ArrayList<>();
        }

        // 获取该学生已掌握的错题对应的所有题目ID
        LambdaQueryWrapper<WrongQuestion> masteredWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, wrongQuestion.getStudentId())
                .eq(WrongQuestion::getMastered, true);
        List<WrongQuestion> masteredList = this.list(masteredWrapper);
        Set<Long> masteredQuestionIds = masteredList.stream()
                .map(WrongQuestion::getQuestionId)
                .collect(Collectors.toSet());

        // 排除当前错题本身
        masteredQuestionIds.add(wrongQuestion.getQuestionId());

        // 查询相同知识点的其他题目（排除已掌握的和当前题目）
        LambdaQueryWrapper<Question> questionWrapper = new LambdaQueryWrapper<Question>()
                .like(Question::getKnowledgePoints, question.getKnowledgePoints())
                .notIn(Question::getId, masteredQuestionIds)
                .eq(Question::getIsActive, true)
                .last("LIMIT 10");

        return questionService.list(questionWrapper);
    }
}
