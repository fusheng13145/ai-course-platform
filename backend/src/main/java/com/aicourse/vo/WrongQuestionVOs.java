package com.aicourse.vo;

import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 错题本与练习题出参。
 *
 * <p>字段与实体一一对应、零删减。`WrongQuestion` 的 `correctAnswer` / `questionAnalysis`
 * 是**学生自己的错题回顾**内容，本就该给本人看；`Question` 的 `correctAnswers` / `explanation`
 * 在练习/推荐端点里也沿用改动前的可见范围。
 *
 * <p><b>遗留风险（本批未改，单列缺陷）</b>：`GET /wrong-questions/practice` 与
 * `/wrong-questions/{id}/related` 返回整条 `Question`（含 `correctAnswers` 与 `explanation`）。
 * 若前端是把这些题当“待作答”下发，则等于把答案一起给了学生。当前行为未变，
 * 需先确认前端是否在学生作答前渲染这两列，再决定是否拆「作答版」出参。
 */
public final class WrongQuestionVOs {

    private WrongQuestionVOs() {
    }

    /** 错题出参。 */
    @Data
    public static class WrongQuestionVO {
        private Long id;
        private Long studentId;
        private Long questionId;
        private Long courseId;
        private String wrongAnswer;
        private String reason;
        private String aiAnalysis;
        private String questionContent;
        private String questionOptions;
        private String correctAnswer;
        private String questionAnalysis;
        private String knowledgePointName;
        private Boolean mastered;
        private String sourceType;
        private Integer practiceCount;
        private LocalDateTime lastPracticedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static WrongQuestionVO from(WrongQuestion wq) {
            if (wq == null) {
                return null;
            }
            WrongQuestionVO vo = new WrongQuestionVO();
            vo.setId(wq.getId());
            vo.setStudentId(wq.getStudentId());
            vo.setQuestionId(wq.getQuestionId());
            vo.setCourseId(wq.getCourseId());
            vo.setWrongAnswer(wq.getWrongAnswer());
            vo.setReason(wq.getReason());
            vo.setAiAnalysis(wq.getAiAnalysis());
            vo.setQuestionContent(wq.getQuestionContent());
            vo.setQuestionOptions(wq.getQuestionOptions());
            vo.setCorrectAnswer(wq.getCorrectAnswer());
            vo.setQuestionAnalysis(wq.getQuestionAnalysis());
            vo.setKnowledgePointName(wq.getKnowledgePointName());
            vo.setMastered(wq.getMastered());
            vo.setSourceType(wq.getSourceType());
            vo.setPracticeCount(wq.getPracticeCount());
            vo.setLastPracticedAt(wq.getLastPracticedAt());
            vo.setCreatedAt(wq.getCreatedAt());
            vo.setUpdatedAt(wq.getUpdatedAt());
            return vo;
        }

        public static List<WrongQuestionVO> from(List<WrongQuestion> list) {
            return list == null ? List.of() : list.stream().map(WrongQuestionVO::from).toList();
        }
    }

    /** 题目出参（练习/推荐端点用）。 */
    @Data
    public static class PracticeQuestionVO {
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

        public static PracticeQuestionVO from(Question question) {
            if (question == null) {
                return null;
            }
            PracticeQuestionVO vo = new PracticeQuestionVO();
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

        public static List<PracticeQuestionVO> from(List<Question> questions) {
            return questions == null ? List.of() : questions.stream().map(PracticeQuestionVO::from).toList();
        }
    }
}

