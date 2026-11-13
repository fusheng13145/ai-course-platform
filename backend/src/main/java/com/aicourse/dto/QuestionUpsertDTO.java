package com.aicourse.dto;

import com.aicourse.entity.Question;
import com.aicourse.util.JsonColumnNormalizer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * 创建／修改题目的入参（B-13 ⑩）。
 *
 * <p><b>为什么不再直接绑 {@code Question} 实体</b>：实体上有 {@code createdBy}／{@code usageCount}／
 * {@code correctRate}／{@code isActive}／{@code createdAt} 这些「服务端字段」，实体直绑等于把它们
 * 交给客户端随便写（可把 {@code isActive} 置空、把 {@code usageCount} 改大）。DTO 只暴露业务字段，
 * 服务端字段由控制器与 service 自己填。</p>
 *
 * <p><b>三个 JSON 列的入参契约</b>：{@code options}／{@code correctAnswers}／{@code knowledgePoints}
 * 在库里是 {@code JSON} 类型，既接受已编码的 JSON 文本（老客户端），也接受直接发的结构化值
 * （新客户端），后端统一经 {@link JsonColumnNormalizer} 规范化后再落库。分两类形状：</p>
 * <ul>
 *   <li>{@code options}／{@code knowledgePoints} **必须是数组**（下游按数组遍历），非数组一律 400；</li>
 *   <li>{@code correctAnswers} 允许单值或数组：纯文本 {@code A}、多选简写 {@code A,B} 都会被编码成合法 JSON。</li>
 * </ul>
 */
@Data
@Schema(name = "QuestionUpsertDTO", description = "创建／修改题目的入参；JSON 列同时接受 JSON 文本与结构化值")
public class QuestionUpsertDTO {

    /**
     * 校验分组：新建与修改的必填面不同。
     * <p>新建必须给齐 {@code courseId／type／content}；修改是局部更新，只校验「给了就要合法」，
     * 不能用新建的必填约束把「只改解析」这种请求挡成 400（也会让 ACL 的 403 先变成 400）。</p>
     */
    public interface Create {
    }

    /** 修改分组。 */
    public interface Update {
    }

    private static final String TYPE_PATTERN = "SINGLE_CHOICE|MULTIPLE_CHOICE|TRUE_FALSE|FILL_BLANK|ESSAY";
    private static final String DIFFICULTY_PATTERN = "EASY|MEDIUM|HARD";

    @Schema(description = "所属课程ID（新建必填；修改时以库内归属为准，传了也会被忽略）", example = "1")
    @NotNull(message = "courseId 不能为空", groups = Create.class)
    private Long courseId;

    @Schema(description = "题型：SINGLE_CHOICE／MULTIPLE_CHOICE／TRUE_FALSE／FILL_BLANK／ESSAY",
            example = "SINGLE_CHOICE")
    @NotBlank(message = "type 不能为空", groups = Create.class)
    @Pattern(regexp = TYPE_PATTERN, message = "type 仅支持 " + TYPE_PATTERN,
            groups = {Create.class, Update.class})
    private String type;

    @Schema(description = "题干")
    @NotBlank(message = "content 不能为空", groups = Create.class)
    @Size(max = 5000, message = "content 过长（上限 5000 字符）", groups = {Create.class, Update.class})
    private String content;

    @Schema(description = "选项（JSON 列，**必须是数组**）：直接发数组或数组形态的 JSON 文本；其它形态 400")
    private Object options;

    @Schema(description = "正确答案（JSON 列）：可直接发数组／字符串，或发 JSON 文本；多选支持 \"A,B\" 简写")
    private Object correctAnswers;

    @Schema(description = "关联知识点（JSON 列，**必须是数组**）：直接发数组或数组形态的 JSON 文本；其它形态 400")
    private Object knowledgePoints;

    @Schema(description = "难度：EASY／MEDIUM／HARD；不传则不校验", example = "MEDIUM")
    @Pattern(regexp = DIFFICULTY_PATTERN, message = "difficulty 仅支持 " + DIFFICULTY_PATTERN,
            groups = {Create.class, Update.class})
    private String difficulty;

    @Schema(description = "分值；不传则沿用库内值", example = "5")
    @DecimalMin(value = "0.0", message = "score 不能为负", groups = {Create.class, Update.class})
    @DecimalMax(value = "1000.0", message = "score 过大（上限 1000）", groups = {Create.class, Update.class})
    private BigDecimal score;

    @Schema(description = "解析")
    @Size(max = 5000, message = "explanation 过长（上限 5000 字符）", groups = {Create.class, Update.class})
    private String explanation;

    /**
     * 转成待落库实体：只搬运业务字段，服务端字段（id／createdBy／统计列）一律不从这里来。
     *
     * @return 新实体
     */
    public Question toEntity() {
        String normalizedType = type == null ? null : type.trim().toUpperCase(Locale.ROOT);
        Question question = new Question();
        question.setCourseId(courseId);
        question.setType(normalizedType);
        question.setContent(content == null ? null : content.trim());
        question.setOptions(JsonColumnNormalizer.normalizeArrayColumn(options, "options"));
        question.setCorrectAnswers(JsonColumnNormalizer.normalizeAnswers(correctAnswers, normalizedType));
        question.setKnowledgePoints(JsonColumnNormalizer.normalizeArrayColumn(knowledgePoints, "knowledgePoints"));
        question.setDifficulty(difficulty == null ? null : difficulty.trim().toUpperCase(Locale.ROOT));
        question.setScore(score);
        question.setExplanation(explanation);
        return question;
    }
}
