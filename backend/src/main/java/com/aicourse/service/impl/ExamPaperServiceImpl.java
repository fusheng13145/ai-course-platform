package com.aicourse.service.impl;

import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamPaperMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.QuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ExamPaperServiceImpl extends ServiceImpl<ExamPaperMapper, ExamPaper> implements ExamPaperService {

    private final ExamQuestionService examQuestionService;
    private final QuestionService questionService;

    public ExamPaperServiceImpl(ExamQuestionService examQuestionService, QuestionService questionService) {
        this.examQuestionService = examQuestionService;
        this.questionService = questionService;
    }

    /**
     * 获取试卷完整详情（含题目列表）
     */
    @Override
    public Map<String, Object> getExamPaperDetail(Long paperId) {
        ExamPaper examPaper = this.getById(paperId);
        if (examPaper == null) {
            throw new BusinessException("试卷不存在");
        }

        // 查询试卷关联的题目列表
        LambdaQueryWrapper<ExamQuestion> queryWrapper = new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, paperId)
                .orderByAsc(ExamQuestion::getOrderIndex);
        List<ExamQuestion> examQuestions = examQuestionService.list(queryWrapper);

        // 构建题目详情列表
        List<Map<String, Object>> questionList = new ArrayList<>();
        for (ExamQuestion eq : examQuestions) {
            Question question = questionService.getById(eq.getQuestionId());
            if (question == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", eq.getId());
            item.put("questionId", eq.getQuestionId());
            item.put("orderIndex", eq.getOrderIndex());
            item.put("points", eq.getPoints());
            item.put("type", question.getType());
            item.put("content", question.getContent());
            item.put("options", question.getOptions());
            item.put("difficulty", question.getDifficulty());
            questionList.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("paper", examPaper);
        result.put("questions", questionList);
        result.put("questionCount", questionList.size());
        return result;
    }

    /**
     * 发布试卷
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishExamPaper(Long paperId) {
        ExamPaper examPaper = this.getById(paperId);
        if (examPaper == null) {
            throw new BusinessException("试卷不存在");
        }
        if ("PUBLISHED".equals(examPaper.getStatus())) {
            throw new BusinessException("试卷已发布");
        }
        examPaper.setStatus("PUBLISHED");
        this.updateById(examPaper);
    }

    /**
     * 复制试卷
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamPaper copyExamPaper(Long sourcePaperId, String newTitle) {
        ExamPaper sourcePaper = this.getById(sourcePaperId);
        if (sourcePaper == null) {
            throw new BusinessException("源试卷不存在");
        }

        // 创建新试卷
        ExamPaper newPaper = new ExamPaper();
        newPaper.setTitle(newTitle != null ? newTitle : sourcePaper.getTitle() + "(副本)");
        newPaper.setDescription(sourcePaper.getDescription());
        newPaper.setCourseId(sourcePaper.getCourseId());
        newPaper.setCreatorId(sourcePaper.getCreatorId());
        newPaper.setStatus("DRAFT");
        newPaper.setInstructions(sourcePaper.getInstructions());
        newPaper.setStartTime(sourcePaper.getStartTime());
        newPaper.setEndTime(sourcePaper.getEndTime());
        newPaper.setTotalScore(sourcePaper.getTotalScore());
        newPaper.setDuration(sourcePaper.getDuration());
        newPaper.setShuffleQuestions(sourcePaper.getShuffleQuestions());
        newPaper.setShuffleOptions(sourcePaper.getShuffleOptions());
        newPaper.setShowScore(sourcePaper.getShowScore());
        newPaper.setShowCorrectAnswer(sourcePaper.getShowCorrectAnswer());
        newPaper.setMaxAttempts(sourcePaper.getMaxAttempts());
        newPaper.setTimeLimit(sourcePaper.getTimeLimit());
        newPaper.setAllowReview(sourcePaper.getAllowReview());
        newPaper.setGenerationRules(sourcePaper.getGenerationRules());
        newPaper.setIsActive(true);
        this.save(newPaper);

        // 复制试卷-题目关联关系
        LambdaQueryWrapper<ExamQuestion> queryWrapper = new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, sourcePaperId)
                .orderByAsc(ExamQuestion::getOrderIndex);
        List<ExamQuestion> sourceQuestions = examQuestionService.list(queryWrapper);

        for (ExamQuestion sq : sourceQuestions) {
            ExamQuestion newEq = new ExamQuestion();
            newEq.setExamPaperId(newPaper.getId());
            newEq.setQuestionId(sq.getQuestionId());
            newEq.setOrderIndex(sq.getOrderIndex());
            newEq.setPoints(sq.getPoints());
            examQuestionService.save(newEq);
        }

        // 更新新试卷的题目数量
        newPaper.setQuestionCount(sourceQuestions.size());
        this.updateById(newPaper);

        return newPaper;
    }

    /**
     * 获取试卷统计（题型分布、分值分布）
     */
    @Override
    public Map<String, Object> getPaperStatistics(Long paperId) {
        ExamPaper examPaper = this.getById(paperId);
        if (examPaper == null) {
            throw new BusinessException("试卷不存在");
        }

        // 查询所有关联题目
        LambdaQueryWrapper<ExamQuestion> queryWrapper = new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, paperId);
        List<ExamQuestion> examQuestions = examQuestionService.list(queryWrapper);

        // 统计题型分布
        Map<String, Integer> typeDistribution = new HashMap<>();
        // 统计分值分布
        Map<String, Integer> scoreDistribution = new HashMap<>();
        BigDecimal totalPoints = BigDecimal.ZERO;
        int questionCount = examQuestions.size();

        for (ExamQuestion eq : examQuestions) {
            Question question = questionService.getById(eq.getQuestionId());
            if (question == null) {
                continue;
            }

            // 题型统计
            String type = question.getType();
            typeDistribution.merge(type, 1, Integer::sum);

            // 分值统计（按分数段）
            BigDecimal points = eq.getPoints();
            totalPoints = totalPoints.add(points);
            String scoreRange;
            if (points.compareTo(new BigDecimal("5")) <= 0) {
                scoreRange = "1-5分";
            } else if (points.compareTo(new BigDecimal("10")) <= 0) {
                scoreRange = "6-10分";
            } else if (points.compareTo(new BigDecimal("20")) <= 0) {
                scoreRange = "11-20分";
            } else {
                scoreRange = "20分以上";
            }
            scoreDistribution.merge(scoreRange, 1, Integer::sum);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("paperInfo", Map.of(
                "id", examPaper.getId(),
                "title", examPaper.getTitle(),
                "status", examPaper.getStatus()
        ));
        result.put("questionCount", questionCount);
        result.put("totalPoints", totalPoints);
        result.put("typeDistribution", typeDistribution);
        result.put("scoreDistribution", scoreDistribution);
        return result;
    }
}
