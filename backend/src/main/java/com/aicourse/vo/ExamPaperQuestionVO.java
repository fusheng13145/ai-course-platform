package com.aicourse.vo;

import com.aicourse.entity.Question;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 试卷关联题目出参（GET /exam-papers/{id}/questions）。
 * 字段名与历史 Map 键一致（id / orderIndex / points / question）。
 * points 为 BigDecimal（与 ExamQuestion.getPoints 类型一致，JSON 序列化为数字）。
 * question 为题目实体（键集天然固定，直接承载实体，同 ChapterResourceTreeVO.resources 模式）。
 * 原实现无条件 put 此四键（恒含），故默认 INCLUDE.ALWAYS，精确复刻。
 */
@Data
public class ExamPaperQuestionVO {

    private Long id;
    private Integer orderIndex;
    private BigDecimal points;
    private Question question;

    public ExamPaperQuestionVO() {
    }

    public ExamPaperQuestionVO(Long id, Integer orderIndex, BigDecimal points, Question question) {
        this.id = id;
        this.orderIndex = orderIndex;
        this.points = points;
        this.question = question;
    }
}
