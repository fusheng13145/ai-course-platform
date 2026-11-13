package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 课程实体类
 * 对应数据表：courses
 */
@TableName("courses")
@Data
public class Course {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 课程名称
     */
    private String name;

    /**
     * 课程编号
     */
    private String code;

    /**
     * 课程描述
     */
    private String description;

    /**
     * 学分
     */
    private BigDecimal credits;

    /**
     * 授课教师ID
     */
    private Long teacherId;

    /**
     * 所属学院ID（V13 起由课程负责人学院回填，未回填时回退教师学院）
     */
    private Long collegeId;

    /**
     * 学期
     */
    private String semester;

    /**
     * 学年
     */
    private Integer year;

    /**
     * 课程状态
     */
    private String status;

    /**
     * 审核状态
     */
    private String approvalStatus;

    /**
     * 选课类型
     */
    private String enrollmentType;

    /**
     * 最大选课人数
     */
    private Integer maxStudents;

    /**
     * 是否启用
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * 课程分类
     */
    private String category;

    /**
     * 难度等级
     */
    private String difficulty;

    /**
     * 课程价格
     */
    private BigDecimal price;

    /**
     * 课程标签
     */
    private String tags;

    /**
     * 开课时间
     */
    private LocalDateTime startDate;

    /**
     * 结课时间
     */
    private LocalDateTime endDate;

    /**
     * 封面图片地址
     */
    private String coverImage;

    /**
     * 审核时间
     */
    private LocalDateTime approvalTime;

    /**
     * 审核人ID
     */
    private Long approvedBy;

    /**
     * 审核意见
     */
    private String approvalComment;

    /**
     * 课程大纲链接
     */
    private String syllabusUrl;

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

    /**
     * 所属学期（4A，V17）：课程编号在学期内唯一的前提字段。
     * 创建时由 {@code CourseServiceImpl} 解析为当前 ACTIVE 学期；V17 回填后收紧 NOT NULL。
     */
    private Long semesterId;

    public void setIsActive(Boolean active) {
        isActive = active;
    }

}