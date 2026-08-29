package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.stream.Collectors;


/**
 * 考试题目服务实现：相关业务逻辑入口，提供增删改查与编排能力。
 */
@Service
public class ExamQuestionServiceImpl extends ServiceImpl<ExamQuestionMapper, ExamQuestion> implements ExamQuestionService {

    private static final Logger log = LoggerFactory.getLogger(ExamQuestionServiceImpl.class);

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

        // 一次取回全部待排序题目并校验归属（避免逐条 count/getById 的 N+1），
        // 复用取回对象直接更新序号，事务内保证原子性
        List<ExamQuestion> existing = this.list(new LambdaQueryWrapper<ExamQuestion>()
                .in(ExamQuestion::getId, questionIds)
                .eq(ExamQuestion::getExamPaperId, examPaperId));
        Map<Long, ExamQuestion> byId = existing.stream()
                .collect(Collectors.toMap(ExamQuestion::getId, e -> e, (a, b) -> a));

        // 更新顺序
        for (int i = 0; i < questionIds.size(); i++) {
            ExamQuestion examQuestion = byId.get(questionIds.get(i));
            if (examQuestion == null) {
                throw new BusinessException("题目ID " + questionIds.get(i) + " 不属于该试卷");
            }
            examQuestion.setOrderIndex(i + 1);
            this.updateById(examQuestion);
        }
    }
}
