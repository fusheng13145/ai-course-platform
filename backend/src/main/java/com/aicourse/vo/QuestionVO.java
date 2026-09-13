package com.aicourse.vo;

import com.aicourse.entity.Question;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目详情响应VO（View Object）
 * 用于返回给前端的题目完整信息，包含基础字段和使用统计数据
 */
@Schema(description = "题目详情响应")
public class QuestionVO {

    // ====================== 继承QuestionCreateDTO基础字段 ======================

    @Schema(description = "题目ID", example = "101")
    private Long id;

    @Schema(description = "所属课程ID", example = "1")
    private Long courseId;

    @Schema(description = "题目类型", example = "SINGLE_CHOICE",
            allowableValues = {"SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER", "FILL_BLANK"})
    private String type;

    @Schema(description = "题干内容（支持Markdown）", example = "以下哪个是Java的基本数据类型？")
    private String content;

    @Schema(description = "选项列表", example = "[\"String\", \"Integer\", \"int\", \"Object\"]")
    private List<String> options;

    @Schema(description = "正确答案", example = "C")
    private String answer;

    @Schema(description = "默认分值", example = "10")
    private BigDecimal score;

    @Schema(description = "难度等级(1-5)", example = "2")
    private Integer difficulty;

    @Schema(description = "题目解析", example = "**解析**：Java有8种基本数据类型...")
    private String analysis;

    @Schema(description = "关联知识点ID列表", example = "[101, 102]")
    private List<Long> knowledgePointIds;

    @Schema(description = "标签列表，逗号分隔", example = "基础语法,常考")
    private List<String> tags;

    @Schema(description = "题目来源", example = "2024年期末考试真题")
    private String source;

    // ====================== 额外统计字段 ======================

    @Schema(description = "创建时间", example = "2024-10-01T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "被试卷引用次数", example = "15")
    private Integer usageCount;

    @Schema(description = "历史正确率（小数形式）", example = "0.72")
    private BigDecimal correctRate;

    @Schema(description = "创建者姓名", example = "李教授")
    private String creatorName;

    // ====================== Getter & Setter ======================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getUsageCount() { return usageCount; }
    public void setUsageCount(Integer usageCount) { this.usageCount = usageCount; }
    public BigDecimal getCorrectRate() { return correctRate; }
    public void setCorrectRate(BigDecimal correctRate) { this.correctRate = correctRate; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }

    // ====================== 工厂方法 ======================

    /**
     * 从实体转换为 VO（学生端脱敏版本，隐藏答案与解析）
     * @param q 题目实体
     * @return VO 对象
     */
    public static QuestionVO from(Question q) {
        if (q == null) return null;
        QuestionVO vo = new QuestionVO();
        vo.setId(q.getId());
        vo.setCourseId(q.getCourseId());
        vo.setType(q.getType());
        vo.setContent(q.getContent());
        vo.setScore(q.getScore());
        vo.setCorrectRate(q.getCorrectRate());
        vo.setUsageCount(q.getUsageCount());
        // 难度映射：String → Integer
        String diff = q.getDifficulty();
        if (diff != null) {
            switch (diff.toUpperCase()) {
                case "EASY": vo.setDifficulty(1); break;
                case "MEDIUM": vo.setDifficulty(3); break;
                case "HARD": vo.setDifficulty(5); break;
                default: vo.setDifficulty(3);
            }
        }
        // 选项 JSON 字符串 → List
        String opts = q.getOptions();
        if (opts != null && opts.startsWith("[")) {
            vo.setOptions(java.util.Arrays.stream(opts.replaceAll("[\\[\\]\"]", "")
                .split("\\s*,\\s*")).filter(s -> !s.isEmpty()).toList());
        }
        return vo;
    }
}
