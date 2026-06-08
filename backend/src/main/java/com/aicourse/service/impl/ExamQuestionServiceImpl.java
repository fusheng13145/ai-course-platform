package com.aicourse.service.impl;

import com.aicourse.entity.ExamQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamQuestionMapper;
import com.aicourse.service.ExamQuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class ExamQuestionServiceImpl extends ServiceImpl<ExamQuestionMapper, ExamQuestion> implements ExamQuestionService {

    /**
     * 批量添加题目到试卷
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAddQuestions(Long examPaperId, List<Map<String, Object>> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException("题目列表不能为空");
        }

        // 查询当前最大序号
        LambdaQueryWrapper<ExamQuestion> maxOrderWrapper = new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, examPaperId)
                .orderByDesc(ExamQuestion::getOrderIndex)
                .last("LIMIT 1");
        ExamQuestion lastQuestion = this.getOne(maxOrderWrapper);
        int startOrder = (lastQuestion != null && lastQuestion.getOrderIndex() != null) ?
                lastQuestion.getOrderIndex() : 0;

        // 批量添加题目
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = questions.get(i);
            Long questionId = Long.valueOf(q.get("questionId").toString());
            BigDecimal points = new BigDecimal(q.get("points").toString());

            ExamQuestion examQuestion = new ExamQuestion();
            examQuestion.setExamPaperId(examPaperId);
            examQuestion.setQuestionId(questionId);
            examQuestion.setOrderIndex(startOrder + i + 1);
            examQuestion.setPoints(points);
            this.save(examQuestion);
        }
    }

    /**
     * 调整题目顺序
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderQuestions(Long examPaperId, List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            throw new BusinessException("题目ID列表不能为空");
        }

        // 验证所有题目都属于该试卷
        for (Long questionId : questionIds) {
            LambdaQueryWrapper<ExamQuestion> wrapper = new LambdaQueryWrapper<ExamQuestion>()
                    .eq(ExamQuestion::getId, questionId)
                    .eq(ExamQuestion::getExamPaperId, examPaperId);
            long count = this.count(wrapper);
            if (count == 0) {
                throw new BusinessException("题目ID " + questionId + " 不属于该试卷");
            }
        }

        // 更新顺序
        for (int i = 0; i < questionIds.size(); i++) {
            ExamQuestion examQuestion = this.getById(questionIds.get(i));
            if (examQuestion != null) {
                examQuestion.setOrderIndex(i + 1);
                this.updateById(examQuestion);
            }
        }
    }
}
