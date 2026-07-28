package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 投票记录实体（每位学生一次投票结果）
 * 对应数据表：vote_records
 */
@Data
@TableName("vote_records")
public class VoteRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联投票ID */
    private Long voteId;

    /** 学生ID */
    private Long studentId;

    /**
     * 选择项 JSON（单选为字符串，多选为数组）
     * 例：单选择 "a"；多选择 ["a","c"]
     */
    private String choice;

    /** 文本回答（TEXT 模式使用） */
    private String textAnswer;

    /** 投票IP */
    private String ip;

    /** 用户代理（UA） */
    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
