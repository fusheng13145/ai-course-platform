package com.aicourse.vo;

import com.aicourse.entity.Question;
import com.aicourse.util.JsonColumnNormalizer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学生题库练习题目投影（P0-A1）。
 * 刻意不包含 correctAnswers / explanation：学生提交答案后由判分接口返回，
 * 避免列表或详情接口一打开就泄露答案。
 */
@Schema(description = "学生题库练习题目（不含答案与解析）")
public class QuestionPracticeVO {

    @Schema(description = "题目ID", example = "101")
    private Long id;

    @Schema(description = "所属课程ID", example = "1")
    private Long courseId;

    @Schema(description = "题目类型", example = "SINGLE_CHOICE")
    private String type;

    @Schema(description = "题干内容", example = "以下哪个是Java的基本数据类型？")
    private String content;

    @Schema(description = "选项列表", example = "[\"String\", \"Integer\", \"int\", \"Object\"]")
    private List<String> options;

    @Schema(description = "默认分值", example = "10")
    private BigDecimal score;

    @Schema(description = "难度等级", example = "MEDIUM")
    private String difficulty;

    @Schema(description = "关联知识点名称列表", example = "[\"函数\",\"闭包\"]")
    private List<String> knowledgePoints;

    public static QuestionPracticeVO from(Question question) {
        if (question == null) {
            return null;
        }
        QuestionPracticeVO vo = new QuestionPracticeVO();
        vo.setId(question.getId());
        vo.setCourseId(question.getCourseId());
        vo.setType(question.getType());
        vo.setContent(question.getContent());
        vo.setOptions(JsonColumnNormalizer.toStringList(question.getOptions()));
        vo.setScore(question.getScore());
        vo.setDifficulty(question.getDifficulty());
        vo.setKnowledgePoints(JsonColumnNormalizer.toStringList(question.getKnowledgePoints()));
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public List<String> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(List<String> knowledgePoints) {
        this.knowledgePoints = knowledgePoints;
    }
}
