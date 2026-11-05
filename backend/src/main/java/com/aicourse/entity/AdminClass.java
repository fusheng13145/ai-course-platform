package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 行政班实体
 * 对应数据表：admin_classes（V12 / B-12-P-08 WP1）
 * 行政班是学生学籍归属与班主任工作对象，不绑定课程。
 */
@TableName("admin_classes")
@Data
public class AdminClass {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属学校ID，关联 schools 表
     */
    private Long schoolId;

    /**
     * 所属学院ID，关联 colleges 表
     */
    private Long collegeId;

    /**
     * 所属专业ID，关联 majors 表
     */
    private Long majorId;

    /**
     * 行政班名称
     */
    private String name;

    /**
     * 行政班编号
     */
    private String code;

    /**
     * 年级，如 2023
     */
    private String gradeYear;

    /**
     * 班主任（教师职责，不是登录角色），关联 users 表
     */
    private Long advisorTeacherId;

    /**
     * 当前学生人数
     */
    private Integer studentCount;

    /**
     * 最大容纳人数
     */
    private Integer maxStudents;

    /**
     * 状态：ACTIVE-启用，ARCHIVED-归档
     */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
