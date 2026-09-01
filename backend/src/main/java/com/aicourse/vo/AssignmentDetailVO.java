package com.aicourse.vo;

import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 作业学习详情出参（作业任务实体 + 当前学生提交记录）。
 * 字段名与历史 Map 键一致（assignment / submission），保证前端零改动。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssignmentDetailVO {

    private Task assignment;
    private Submission submission;

    public AssignmentDetailVO() {
    }

    public AssignmentDetailVO(Task assignment, Submission submission) {
        this.assignment = assignment;
        this.submission = submission;
    }
}
