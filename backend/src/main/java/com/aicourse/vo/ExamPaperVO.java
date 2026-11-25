package com.aicourse.vo;

import com.aicourse.entity.ExamPaper;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 试卷出参。
 *
 * <p>字段与 {@link ExamPaper} 实体序列化字段一一对应（含非表字段 `courseName`）。
 * 试卷本身**不含题干与标准答案**——题目在 `ExamQuestion` 里，由 `ExamPaperDetailVO`
 * 与考试会话端点按角色裁剪，因此本类不需要做答案屏蔽，只做分层边界。
 */
@Data
public class ExamPaperVO {

    private Long id;
    private String title;
    private String description;
    private Long courseId;
    private Long creatorId;
    private String status;
    private String visibility;
    /** 非表字段：目标教学班 ID 列表。 */
    private List<Long> teachingClassIds;
    private String instructions;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal totalScore;
    private Integer duration;
    private Integer questionCount;
    private Boolean shuffleQuestions;
    private Boolean shuffleOptions;
    private Boolean showScore;
    private Boolean showCorrectAnswer;
    private Integer maxAttempts;
    private Integer timeLimit;
    private Boolean allowReview;
    private String generationRules;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 非表字段：课程名称回填。 */
    private String courseName;

    /** 实体 → 出参。 */
    public static ExamPaperVO from(ExamPaper paper) {
        if (paper == null) {
            return null;
        }
        ExamPaperVO vo = new ExamPaperVO();
        vo.setId(paper.getId());
        vo.setTitle(paper.getTitle());
        vo.setDescription(paper.getDescription());
        vo.setCourseId(paper.getCourseId());
        vo.setCreatorId(paper.getCreatorId());
        vo.setStatus(paper.getStatus());
        vo.setVisibility(paper.getVisibility());
        vo.setTeachingClassIds(paper.getTeachingClassIds());
        vo.setInstructions(paper.getInstructions());
        vo.setStartTime(paper.getStartTime());
        vo.setEndTime(paper.getEndTime());
        vo.setTotalScore(paper.getTotalScore());
        vo.setDuration(paper.getDuration());
        vo.setQuestionCount(paper.getQuestionCount());
        vo.setShuffleQuestions(paper.getShuffleQuestions());
        vo.setShuffleOptions(paper.getShuffleOptions());
        vo.setShowScore(paper.getShowScore());
        vo.setShowCorrectAnswer(paper.getShowCorrectAnswer());
        vo.setMaxAttempts(paper.getMaxAttempts());
        vo.setTimeLimit(paper.getTimeLimit());
        vo.setAllowReview(paper.getAllowReview());
        vo.setGenerationRules(paper.getGenerationRules());
        vo.setIsActive(paper.getIsActive());
        vo.setCreatedAt(paper.getCreatedAt());
        vo.setUpdatedAt(paper.getUpdatedAt());
        vo.setCourseName(paper.getCourseName());
        return vo;
    }

    /** 批量转换。 */
    public static List<ExamPaperVO> from(List<ExamPaper> papers) {
        return papers == null ? List.of() : papers.stream().map(ExamPaperVO::from).toList();
    }

    /**
     * 试卷内题目引用（{@code ExamQuestion} 的实体镜像，6 字段）。
     *
     * <p>刻意与 {@link ExamPaperQuestionVO} 区分：后者是「题目内容视图」，
     * 内嵌完整 {@code Question} 且带题干与选项；本类只描述「哪道题、第几题、多少分」，
     * 用于题目编排接口（替换题目后回显引用列表）。两者形状不同，不可互换。
     */
    @Data
    public static class QuestionRefVO {
        private Long id;
        private Long examPaperId;
        private Long questionId;
        private Integer orderIndex;
        private java.math.BigDecimal points;
        private java.time.LocalDateTime createdAt;

        public static QuestionRefVO from(com.aicourse.entity.ExamQuestion examQuestion) {
            if (examQuestion == null) {
                return null;
            }
            QuestionRefVO vo = new QuestionRefVO();
            vo.setId(examQuestion.getId());
            vo.setExamPaperId(examQuestion.getExamPaperId());
            vo.setQuestionId(examQuestion.getQuestionId());
            vo.setOrderIndex(examQuestion.getOrderIndex());
            vo.setPoints(examQuestion.getPoints());
            vo.setCreatedAt(examQuestion.getCreatedAt());
            return vo;
        }

        public static List<QuestionRefVO> from(List<com.aicourse.entity.ExamQuestion> examQuestions) {
            return examQuestions == null ? List.of() : examQuestions.stream().map(QuestionRefVO::from).toList();
        }
    }
}
