package com.aicourse.service;

import com.aicourse.entity.ExamQuestion;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface ExamQuestionService extends IService<ExamQuestion> {

    /**
     * 批量添加题目到试卷
     * @param examPaperId 试卷ID
     * @param questions 题目列表（每项包含questionId和points）
     */
    void batchAddQuestions(Long examPaperId, List<Map<String, Object>> questions);

    /**
     * 调整题目顺序
     * @param examPaperId 试卷ID
     * @param questionIds 按新顺序排列的题目ID列表
     */
    void reorderQuestions(Long examPaperId, List<Long> questionIds);
}
