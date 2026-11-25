package com.aicourse.vo;

import com.aicourse.entity.Question;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 教师侧题库出参（实体镜像，17 字段）。
 *
 * <p>与 {@link QuestionVO} 的区别必须记清：{@code QuestionVO} 是学生答题视图，
 * 把 {@code options} 解析成 List、把 {@code correctAnswers} 改名为 {@code answer}，
 * 并且只在学生分支使用；本类保持实体的原始形状（{@code options}/{@code correctAnswers}
 * 仍是原始字符串），供教师在题库编辑页回填、复制、审核。
 * 两者的字段名不同，**不可互换**，换错会直接打断题库编辑页。
 *
 * <p>答案与解析随本 VO 下发是刻意为之：这些端点全部是 TEACHER 写面/读面
 * （{@code TeacherQuestionController}、{@code QuestionCommandController}），
 * 教师本来就需要看到标准答案与解析。
 */
@Data
public class TeacherQuestionVO {

    private Long id;
    private String type;
    private String content;
    private String options;
    private String correctAnswers;
    private String difficulty;
    private BigDecimal score;
    private String knowledgePoints;
    private String explanation;
    private Integer usageCount;
    private BigDecimal correctRate;
    private Long courseId;
    private Long createdBy;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TeacherQuestionVO from(Question question) {
        if (question == null) {
            return null;
        }
        TeacherQuestionVO vo = new TeacherQuestionVO();
        vo.setId(question.getId());
        vo.setType(question.getType());
        vo.setContent(question.getContent());
        vo.setOptions(question.getOptions());
        vo.setCorrectAnswers(question.getCorrectAnswers());
        vo.setDifficulty(question.getDifficulty());
        vo.setScore(question.getScore());
        vo.setKnowledgePoints(question.getKnowledgePoints());
        vo.setExplanation(question.getExplanation());
        vo.setUsageCount(question.getUsageCount());
        vo.setCorrectRate(question.getCorrectRate());
        vo.setCourseId(question.getCourseId());
        vo.setCreatedBy(question.getCreatedBy());
        vo.setIsActive(question.getIsActive());
        vo.setCreatedAt(question.getCreatedAt());
        vo.setUpdatedAt(question.getUpdatedAt());
        return vo;
    }

    public static List<TeacherQuestionVO> from(List<Question> questions) {
        return questions == null ? List.of() : questions.stream().map(TeacherQuestionVO::from).toList();
    }
}
