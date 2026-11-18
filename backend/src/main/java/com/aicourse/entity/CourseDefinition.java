package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程库定义（Course 层，V28）。
 *
 * <p>只保存跨学期稳定的课程身份：编号、名称、学分、学时、性质、学院、
 * 先修要求与展示信息；不绑定学期，也不绑定授课教师。</p>
 */
@TableName("course_definitions")
@Data
public class CourseDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程编号：全库唯一，跨学期稳定 */
    private String code;

    private String name;

    private String description;

    private BigDecimal credits;

    /** 学时 */
    private Integer hours;

    /** 课程性质：必修/选修/公选 */
    private String nature;

    private Long collegeId;

    private String category;

    private String difficulty;

    /** 先修要求 */
    private String prerequisite;

    private String coverImage;

    private String syllabusUrl;

    /** JSON 数组文本，MyBatis 按字符串读写 */
    private String tags;

    /** ACTIVE-可用，INACTIVE-停用 */
    private String status;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
