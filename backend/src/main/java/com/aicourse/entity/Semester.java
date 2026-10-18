package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学期实体
 * 表名：semesters
 *
 * <p>角色矩阵「① 创建学期 / ② 归档过期学期」的落点。⚠️ 与 {@code courses} 的绑定刻意未建立：
 * {@code courses.semester} 仍是自由文本列，本表不参与课程归属判定（见 V9 迁移注释）。</p>
 */
@TableName("semesters")
@Data
public class Semester {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学期名称（唯一），如 2026-2027-1 */
    private String name;

    @TableField("start_date")
    private LocalDate startDate;

    @TableField("end_date")
    private LocalDate endDate;

    /** ACTIVE-在册；ARCHIVED-已归档 */
    private String status;

    /** 归档时间（未归档为空） */
    @TableField("archived_at")
    private LocalDateTime archivedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
