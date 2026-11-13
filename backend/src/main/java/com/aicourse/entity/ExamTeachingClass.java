package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试卷目标教学班（V15 / B-12-P-08 WP6）。
 * 对应数据表：exam_teaching_classes；仅在 exam_papers.visibility=CLASSES 时存在。
 */
@TableName("exam_teaching_classes")
@Data
public class ExamTeachingClass {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 试卷ID，关联 exam_papers 表
     */
    private Long examPaperId;

    /**
     * 目标教学班ID，关联 teaching_classes 表
     */
    private Long teachingClassId;

    private LocalDateTime createdAt;
}
