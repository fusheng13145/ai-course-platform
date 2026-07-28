package com.aicourse.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 自动组卷请求参数
 */
@Data
public class AutoComposeDTO {

    private Long courseId;
    private String title;
    private String description;
    private BigDecimal totalScore;
    private Integer duration;
    private String status;
    private Integer questionCount;
    private Boolean shuffleQuestions;
    private Boolean shuffleOptions;
    private Boolean showScore;
    private Boolean showCorrectAnswer;
    private Integer maxAttempts;
    private Integer timeLimit;
    private Boolean allowReview;

    /**
     * 组卷规则: {"SINGLE_CHOICE": 10, "MULTIPLE_CHOICE": 5, "TRUE_FALSE": 5, "SHORT_ANSWER": 3}
     */
    private Map<String, Integer> typeRules;

    /**
     * 难度分布: {"EASY": 30, "MEDIUM": 50, "HARD": 20} 百分比
     */
    private Map<String, Integer> difficultyRatio;

    /**
     * 分值规则: {"SINGLE_CHOICE": 2, "MULTIPLE_CHOICE": 3, "TRUE_FALSE": 1, "SHORT_ANSWER": 10}
     */
    private Map<String, BigDecimal> scoreRules;

    /**
     * 指定覆盖的知识点名称列表（可选）。
     * 不传则从题库中随机选题；传入后优先选择标记为这些知识点的题目，确保试卷知识点覆盖度。
     */
    private List<String> knowledgePointNames;

    // Getters and Setters

}
