package com.aicourse.vo;

import com.aicourse.entity.Task;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务（作业/测验/项目）出参。
 *
 * <p>字段与 {@link Task} 实体当前序列化的全部字段一一对应，含非表字段
 * `teachingClassIds`（目标教学班回填项），不含任何敏感信息，因此不做裁剪——
 * 目的是切断「HTTP 出参 = 表结构」耦合，不是脱敏。
 */
@Data
public class TaskVO {

    private Long id;
    private String title;
    private String description;
    private String taskType;
    private Long courseId;
    private Long creatorId;
    private Long chapterId;
    private LocalDateTime dueDate;
    private BigDecimal maxScore;
    private BigDecimal latePenaltyPerDay;
    private Integer maxSubmissions;
    private String status;
    private String visibility;
    /** 非表字段：目标教学班 ID 列表。 */
    private List<Long> teachingClassIds;
    private Boolean isPublished;
    private String submissionFormat;
    private String requirements;
    private String attachmentUrl;
    private LocalDateTime startDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 实体 → 出参。 */
    public static TaskVO from(Task task) {
        if (task == null) {
            return null;
        }
        TaskVO vo = new TaskVO();
        vo.setId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setTaskType(task.getTaskType());
        vo.setCourseId(task.getCourseId());
        vo.setCreatorId(task.getCreatorId());
        vo.setChapterId(task.getChapterId());
        vo.setDueDate(task.getDueDate());
        vo.setMaxScore(task.getMaxScore());
        vo.setLatePenaltyPerDay(task.getLatePenaltyPerDay());
        vo.setMaxSubmissions(task.getMaxSubmissions());
        vo.setStatus(task.getStatus());
        vo.setVisibility(task.getVisibility());
        vo.setTeachingClassIds(task.getTeachingClassIds());
        vo.setIsPublished(task.getIsPublished());
        vo.setSubmissionFormat(task.getSubmissionFormat());
        vo.setRequirements(task.getRequirements());
        vo.setAttachmentUrl(task.getAttachmentUrl());
        vo.setStartDate(task.getStartDate());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    /** 批量转换。 */
    public static List<TaskVO> from(List<Task> tasks) {
        return tasks == null ? List.of() : tasks.stream().map(TaskVO::from).toList();
    }
}
