package com.aicourse.vo;

import com.aicourse.entity.ExamPaper;
import lombok.Data;

import java.util.List;

/**
 * 试卷完整详情出参（试卷实体 + 题目列表 + 题目数）。
 * 字段名与历史 Map 键一致（paper / questions / questionCount）。
 * questions 为结构化 {@link ExamPaperQuestionItemVO} 列表（原 {@code List<Map<String,Object>>} 嵌套一并收敛）。
 * 原实现无条件 put 此三键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class ExamPaperDetailVO {

    private ExamPaper paper;
    private List<ExamPaperQuestionItemVO> questions;
    private Integer questionCount;

    public ExamPaperDetailVO() {
    }

    public ExamPaperDetailVO(ExamPaper paper, List<ExamPaperQuestionItemVO> questions, Integer questionCount) {
        this.paper = paper;
        this.questions = questions;
        this.questionCount = questionCount;
    }
}
