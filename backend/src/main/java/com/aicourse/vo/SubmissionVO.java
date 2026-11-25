package com.aicourse.vo;

import com.aicourse.entity.Submission;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交记录出参。
 *
 * <p><b>为什么这里不做两套白名单</b>：`Submission` 的 24 个字段里没有任何「学生不可见」的列——
 * `content` / `fileUrls` / `submissionData` 是学生自己的作答，`feedback` / `aiFeedback` 是已下发的评语，
 * `graderId` 是用户主键引用。真正的可见性差异是<b>行级</b>（谁能看哪几条提交），
 * 已由 `SubmissionAccessControl` 的 fail-closed 查询（`wrapper.apply("1 = 0")`）承担，
 * 与序列化层无关。因此本类零删减镜像实体，只切断「HTTP 出参 = 表结构」耦合。
 *
 * <p>若后续产品决定「学生分不能看到 aiFeedback / graderId」，那时再拆学生端出参，
 * 按字段白名单单独实现，不要在这里加条件分支。
 */
@Data
public class SubmissionVO {

    private Long id;
    private Long taskId;
    private Long studentId;
    private String content;
    private String fileUrls;
    private String submissionData;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime gradedAt;
    private LocalDateTime returnedAt;
    private BigDecimal score;
    private BigDecimal maxScore;
    private BigDecimal percentage;
    private String feedback;
    private String aiFeedback;
    private Long graderId;
    private Integer attemptNumber;
    private Boolean isLate;
    private BigDecimal latePenalty;
    private Integer timeSpent;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long teachingClassId;

    /** 实体 → 出参。 */
    public static SubmissionVO from(Submission submission) {
        if (submission == null) {
            return null;
        }
        SubmissionVO vo = new SubmissionVO();
        vo.setId(submission.getId());
        vo.setTaskId(submission.getTaskId());
        vo.setStudentId(submission.getStudentId());
        vo.setContent(submission.getContent());
        vo.setFileUrls(submission.getFileUrls());
        vo.setSubmissionData(submission.getSubmissionData());
        vo.setStatus(submission.getStatus());
        vo.setSubmittedAt(submission.getSubmittedAt());
        vo.setGradedAt(submission.getGradedAt());
        vo.setReturnedAt(submission.getReturnedAt());
        vo.setScore(submission.getScore());
        vo.setMaxScore(submission.getMaxScore());
        vo.setPercentage(submission.getPercentage());
        vo.setFeedback(submission.getFeedback());
        vo.setAiFeedback(submission.getAiFeedback());
        vo.setGraderId(submission.getGraderId());
        vo.setAttemptNumber(submission.getAttemptNumber());
        vo.setIsLate(submission.getIsLate());
        vo.setLatePenalty(submission.getLatePenalty());
        vo.setTimeSpent(submission.getTimeSpent());
        vo.setViewCount(submission.getViewCount());
        vo.setCreatedAt(submission.getCreatedAt());
        vo.setUpdatedAt(submission.getUpdatedAt());
        vo.setTeachingClassId(submission.getTeachingClassId());
        return vo;
    }

    /** 批量转换。 */
    public static List<SubmissionVO> from(List<Submission> submissions) {
        return submissions == null ? List.of() : submissions.stream().map(SubmissionVO::from).toList();
    }
}
