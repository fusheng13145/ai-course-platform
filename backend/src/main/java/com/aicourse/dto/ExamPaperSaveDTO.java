package com.aicourse.dto;

import com.aicourse.entity.ExamPaper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 试卷创建／更新入参（入参收实体加固）。
 *
 * <p><b>为什么不绑 {@code ExamPaper} 实体</b>：{@code creatorId} 是身份字段；{@code status}（审核流转）、
 * {@code isActive}（上下架）只能经 {@code /publish /approve /reject} 流转，实体直绑等于允许请求体自批自审、
 * 自己上架；{@code courseName} 是展示快照。这些列在服务层虽然会被白名单剔除，
 * 但那是「实现记得剔除」而非「结构上不可能传入」。</p>
 *
 * <p>更新是<b>局部语义</b>：字段为 {@code null} 表示不改 —— 这正是更新路径判断
 * 「是否触碰了可见范围」的依据（{@code visibility}/{@code teachingClassIds} 任一非空即视为触碰），
 * 因此这里必须保持 {@code null} 语义，不能给默认值。</p>
 */
@Schema(name = "ExamPaperSaveDTO", description = "试卷创建／更新入参；审核与上架字段不接受客户端传入")
@Data
public class ExamPaperSaveDTO {

    @Schema(description = "试卷标题", example = "期中测验")
    @NotBlank(message = "试卷标题不能为空")
    private String title;

    @Schema(description = "试卷说明")
    private String description;

    @Schema(description = "所属课程ID", example = "12")
    private Long courseId;

    @Schema(description = "考生须知")
    private String instructions;

    @Schema(description = "开放时间")
    private LocalDateTime startTime;

    @Schema(description = "截止时间")
    private LocalDateTime endTime;

    @Schema(description = "总分；显式传入时须与题目分值合计一致", example = "100")
    private BigDecimal totalScore;

    @Schema(description = "考试时长（分钟）", example = "90")
    private Integer duration;

    @Schema(description = "题目数量", example = "20")
    private Integer questionCount;

    @Schema(description = "是否打乱题目顺序")
    private Boolean shuffleQuestions;

    @Schema(description = "是否打乱选项顺序")
    private Boolean shuffleOptions;

    @Schema(description = "是否公布分数")
    private Boolean showScore;

    @Schema(description = "是否公布正确答案")
    private Boolean showCorrectAnswer;

    @Schema(description = "最大作答次数", example = "1")
    private Integer maxAttempts;

    @Schema(description = "限时（秒）")
    private Integer timeLimit;

    @Schema(description = "是否允许回看")
    private Boolean allowReview;

    @Schema(description = "组卷规则（JSON 字符串）")
    private String generationRules;

    @Schema(description = "可见范围：ALL / CLASSES / PRIVATE", example = "ALL")
    private String visibility;

    @Schema(description = "目标教学班ID列表（仅 visibility=CLASSES 时有效，且管理员不得使用）")
    private List<Long> teachingClassIds;

    /**
     * 组装实体（仅白名单字段）。
     *
     * <p>刻意不设置 {@code id}/{@code creatorId}/{@code status}/{@code isActive}/{@code courseName}：
     * 前四者由服务端或流转端点写入，最后一个是查询期填充的展示快照。</p>
     */
    public ExamPaper toEntity() {
        ExamPaper paper = new ExamPaper();
        paper.setTitle(title);
        paper.setDescription(description);
        paper.setCourseId(courseId);
        paper.setInstructions(instructions);
        paper.setStartTime(startTime);
        paper.setEndTime(endTime);
        paper.setTotalScore(totalScore);
        paper.setDuration(duration);
        paper.setQuestionCount(questionCount);
        paper.setShuffleQuestions(shuffleQuestions);
        paper.setShuffleOptions(shuffleOptions);
        paper.setShowScore(showScore);
        paper.setShowCorrectAnswer(showCorrectAnswer);
        paper.setMaxAttempts(maxAttempts);
        paper.setTimeLimit(timeLimit);
        paper.setAllowReview(allowReview);
        paper.setGenerationRules(generationRules);
        paper.setVisibility(visibility);
        paper.setTeachingClassIds(teachingClassIds);
        return paper;
    }
}
