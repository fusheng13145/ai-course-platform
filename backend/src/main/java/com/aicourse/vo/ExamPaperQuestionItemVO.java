package com.aicourse.vo;

import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.Question;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 试卷详情内单题出参（关联记录 + 题目快照）。
 * 字段名与历史嵌套 Map 键一致：id/questionId/orderIndex/points 取自关联记录，
 * type/content/options/difficulty 取自题目实体。
 * 原实现逐项无条件 put 八键，故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class ExamPaperQuestionItemVO {

    private Long id;
    private Long questionId;
    private Integer orderIndex;
    private BigDecimal points;
    private String type;
    private String content;
    private String options;
    private String difficulty;

    public ExamPaperQuestionItemVO() {
    }

    public ExamPaperQuestionItemVO(ExamQuestion eq, Question question) {
        this.id = eq.getId();
        this.questionId = eq.getQuestionId();
        this.orderIndex = eq.getOrderIndex();
        this.points = eq.getPoints();
        this.type = question != null ? question.getType() : null;
        this.content = question != null ? question.getContent() : null;
        this.options = question != null ? question.getOptions() : null;
        this.difficulty = question != null ? question.getDifficulty() : null;
    }
}
