package com.aicourse.dto;

import com.aicourse.entity.Question;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 题目创建／更新请求体。
 *
 * <p><b>JSON 列契约（2026-11-05 定型）</b>：</p>
 * <ul>
 *   <li>{@code correctAnswers} 对应 MySQL JSON 列，<b>必须是合法 JSON 文本</b>：单选发 JSON 字符串
 *       {@code "A"}、多选发 JSON 数组 {@code ["A","B"]}、判断发 {@code "true"}；裸 {@code A}
 *       会触发 {@code Invalid JSON text} 并被映射成 400，不再作为可接受入参。</li>
 *   <li>{@code options} 是字符串列，内容为 JSON 数组文本（如 {@code ["A. …","B. …"]}）。</li>
 *   <li>{@code knowledgePoints} 是 JSON 数组文本，{@code []} 表示无标签（与 ㉕ 口径一致）。</li>
 * </ul>
 */
@Schema(description = "题目创建/更新请求体（correctAnswers/options/knowledgePoints 均为 JSON 文本）")
@Data
public class QuestionSaveDTO {

    /** 创建场景校验组；更新时允许省略 courseId，沿用原题目课程。 */
    public interface Create {
    }

    @Schema(description = "所属课程ID；创建时必填，更新时忽略（沿用原题目课程）", example = "4007")
    @NotNull(message = "课程ID不能为空", groups = Create.class)
    private Long courseId;

    @Schema(description = "题干", example = "TCP 三次握手的第二次握手报文携带哪些标志位？",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "题干不能为空", groups = {Default.class, Create.class})
    private String content;

    @Schema(description = "题目类型", example = "single",
            allowableValues = {"single", "multiple", "judge", "short_answer", "essay"})
    private String type;

    @Schema(description = "选项：字符串列，内容必须是 JSON 数组文本",
            example = "[\"A. SYN=1\",\"B. ACK=1\",\"C. FIN=1\"]")
    private String options;

    @Schema(description = "正确答案：MySQL JSON 列，必须是合法 JSON 文本；单选发 JSON 字符串，多选发 JSON 数组",
            example = "\"A\"")
    private String correctAnswers;

    @Schema(description = "难度等级", example = "medium", allowableValues = {"easy", "medium", "hard"})
    private String difficulty;

    @Schema(description = "题目分值", example = "5")
    private BigDecimal score;

    @Schema(description = "知识点：JSON 数组文本，[] 表示无标签", example = "[\"TCP 三次握手\"]")
    private String knowledgePoints;

    @Schema(description = "题目解析", example = "第二次握手为 SYN=1、ACK=1，不携带 FIN。")
    private String explanation;

    @Schema(description = "是否启用；缺省由服务层补 true")
    private Boolean isActive;

    public Question toEntity() {
        Question question = new Question();
        applyTo(question);
        return question;
    }

    public void applyTo(Question question) {
        question.setType(type);
        question.setContent(content);
        question.setOptions(options);
        question.setCorrectAnswers(correctAnswers);
        question.setDifficulty(difficulty);
        question.setScore(score);
        question.setKnowledgePoints(knowledgePoints);
        question.setExplanation(explanation);
        question.setIsActive(isActive);
        question.setCourseId(courseId);
    }
}
