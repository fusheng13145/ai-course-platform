package com.aicourse.dto;

import com.aicourse.entity.Task;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务（作业/测验/项目）创建／更新入参（入参收实体加固）。
 *
 * <p><b>为什么不直接绑 {@code Task} 实体</b>：{@code id}／{@code creatorId}／{@code status}／
 * {@code isPublished} 是服务端字段——实体直绑等于允许教师在创建时就写 `isPublished=true`／
 * `status=PUBLISHED` 绕过发布端点（发布动作带通知与状态机），更新时还能改写创建人归属；
 * {@code createdAt}／{@code updatedAt} 亦不应由客户端指定。</p>
 *
 * <p>{@code teachingClassIds} 是表单态关联字段（落库到 task_teaching_classes），保留在入参。</p>
 */
@Schema(name = "TaskSaveDTO", description = "任务创建／更新入参；创建人、状态、发布标记与时间戳不接受客户端传入")
@Data
public class TaskSaveDTO {

    @Schema(description = "作业标题", example = "第一次编程作业")
    private String title;

    @Schema(description = "作业描述")
    private String description;

    @Schema(description = "任务类型", example = "HOMEWORK")
    private String taskType;

    @Schema(description = "所属课程ID", example = "100")
    private Long courseId;

    @Schema(description = "关联章节ID（章节任务点绑定）", example = "12")
    private Long chapterId;

    @Schema(description = "截止时间", example = "2026-12-31T23:59:59")
    private LocalDateTime dueDate;

    @Schema(description = "满分分值", example = "100")
    private BigDecimal maxScore;

    @Schema(description = "逾期每天扣分比例（百分比）；空表示使用默认 10%/天", example = "10")
    private BigDecimal latePenaltyPerDay;

    @Schema(description = "最大提交次数；0 或空表示不限", example = "3")
    private Integer maxSubmissions;

    @Schema(description = "可见范围：COURSE-全部教学班（默认），CLASSES-仅目标教学班",
            allowableValues = {"COURSE", "CLASSES"})
    private String visibility;

    @Schema(description = "目标教学班ID列表（visibility=CLASSES 时必填）")
    private List<Long> teachingClassIds;

    @Schema(description = "提交格式要求", example = "PDF")
    private String submissionFormat;

    @Schema(description = "作业要求说明")
    private String requirements;

    @Schema(description = "附件地址")
    private String attachmentUrl;

    @Schema(description = "开始时间", example = "2026-11-25T00:00:00")
    private LocalDateTime startDate;

    public Task toEntity() {
        Task task = new Task();
        applyTo(task);
        return task;
    }

    public void applyTo(Task task) {
        task.setTitle(title);
        task.setDescription(description);
        task.setTaskType(taskType);
        task.setCourseId(courseId);
        task.setChapterId(chapterId);
        task.setDueDate(dueDate);
        task.setMaxScore(maxScore);
        task.setLatePenaltyPerDay(latePenaltyPerDay);
        task.setMaxSubmissions(maxSubmissions);
        task.setVisibility(visibility);
        task.setTeachingClassIds(teachingClassIds);
        task.setSubmissionFormat(submissionFormat);
        task.setRequirements(requirements);
        task.setAttachmentUrl(attachmentUrl);
        task.setStartDate(startDate);
    }
}
