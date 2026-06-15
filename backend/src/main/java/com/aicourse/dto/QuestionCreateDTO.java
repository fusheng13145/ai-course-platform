package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建题目请求DTO
 * 用于接收教师向题库添加新题目时的完整请求数据，支持多种题型
 */
@Schema(description = "创建题目请求")
public class QuestionCreateDTO {

    @NotNull(message = "所属课程ID不能为空")
    @Schema(description = "所属课程ID", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @NotBlank(message = "题目类型不能为空")
    @Pattern(regexp = "^(SINGLE_CHOICE|MULTIPLE_CHOICE|TRUE_FALSE|SHORT_ANSWER|FILL_BLANK)$",
            message = "题目类型不合法")
    @Schema(description = "题目类型", example = "SINGLE_CHOICE",
            allowableValues = {"SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER", "FILL_BLANK"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @NotBlank(message = "题干内容不能为空")
    @Size(max = 5000, message = "题干内容不能超过5000个字符")
    @Schema(description = "题干内容（支持Markdown格式）", example = "以下哪个是Java的基本数据类型？\n\nA. String\nB. Integer\nC. int\nD. Object",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "选项列表（A-H最多8个选项），选择题必填", example = "[\"String\", \"Integer\", \"int\", \"Object\"]")
    private List<String> options;

    @NotBlank(message = "正确答案不能为空")
    @Schema(description = "正确答案\n" +
            "- 单选：如 \"C\"\n" +
            "- 多选：如 \"A,C\"\n" +
            "- 判断：如 \"true\"\n" +
            "- 填空/简答：如具体文本",
            example = "C",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String answer;

    @DecimalMin(value = "0.1", message = "默认分值最小为0.1")
    @DecimalMax(value = "100", message = "默认分值最大为100")
    @Schema(description = "默认分值", example = "10")
    private BigDecimal score = BigDecimal.TEN;

    @Min(value = 1, message = "难度最低为1")
    @Max(value = 5, message = "难度最高为5")
    @Schema(description = "难度等级(1-5)", example = "2")
    private Integer difficulty = 1;

    @Size(max = 2000, message = "解析不能超过2000个字符")
    @Schema(description = "题目解析/答案详解", example = "**解析**：Java有8种基本数据类型...")
    private String analysis;

    @Schema(description = "关联知识点ID列表", example = "[101, 102, 105]")
    private List<Long> knowledgePointIds;

    @Schema(description = "标签列表，逗号分隔", example = "基础语法,数据类型,常考")
    private List<String> tags;

    @Size(max = 100, message = "来源不能超过100个字符")
    @Schema(description = "题目来源", example = "2024年期末考试真题")
    private String source;

    // ====================== Getter & Setter ======================

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
    public List<Long> getKnowledgePointIds() { return knowledgePointIds; }
    public void setKnowledgePointIds(List<Long> knowledgePointIds) { this.knowledgePointIds = knowledgePointIds; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
