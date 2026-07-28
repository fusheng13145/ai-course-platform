package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 文件分片上传会话实体
 * 用于记录大文件分片上传进度、分片信息、上传人及会话过期时间
 */
@TableName("file_upload_sessions")
@Data
public class FileUploadSession {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 原始文件名
     */
    private String filename;

    /**
     * 文件总大小（单位：字节）
     */
    private Long fileSize;

    /**
     * 文件总分片数
     */
    private Integer totalChunks;

    /**
     * 已上传分片数
     */
    private Integer uploadedChunks;

    /**
     * 文件最终存储路径
     */
    private String uploadPath;

    /**
     * 上传状态（上传中/已完成/已过期/已取消）
     */
    private String status;

    /**
     * 上传人ID
     */
    private Long uploaderId;

    /**
     * 上传会话过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 记录创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 记录更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}