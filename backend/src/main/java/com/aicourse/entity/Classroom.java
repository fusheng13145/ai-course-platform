package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 班级实体类
 * 对应数据表：classrooms
 */
@TableName("classrooms")
@Data
public class Classroom {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 班级名称
     */
    private String name;

    /**
     * 班级编号
     */
    private String code;

    /**
     * 班级描述
     */
    private String description;

    /**
     * 年级
     */
    private String gradeLevel;

    /**
     * 专业
     */
    private String major;

    /**
     * 辅导员ID
     */
    private Long advisorId;

    /**
     * 当前学生人数
     */
    private Integer studentCount;

    /**
     * 最大容纳人数
     */
    private Integer maxStudents;

    /**
     * 是否启用
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public void setIsActive(Boolean active) {
        isActive = active;
    }

}