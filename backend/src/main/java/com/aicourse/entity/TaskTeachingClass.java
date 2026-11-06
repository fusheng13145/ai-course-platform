package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务目标教学班（V15 / B-12-P-08 WP6）。
 * 对应数据表：task_teaching_classes；仅在 tasks.visibility=CLASSES 时存在。
 */
@TableName("task_teaching_classes")
@Data
public class TaskTeachingClass {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID，关联 tasks 表
     */
    private Long taskId;

    /**
     * 目标教学班ID，关联 teaching_classes 表
     */
    private Long teachingClassId;

    private LocalDateTime createdAt;
}
