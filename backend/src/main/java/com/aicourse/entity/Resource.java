package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 课程资源实体
 * 存储课程配套文件、资料、视频等资源信息，包含文件地址、归属及可见权限
 */
@TableName("resources")
@Data
public class Resource {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 资源访问链接
     */
    private String url;

    /**
     * 所属课程ID
     */
    private Long courseId;

    /**
     * 所属章节ID
     */
    private Long chapterId;

    /**
     * 上传人用户ID
     */
    private Long uploaderId;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 资源描述
     */
    private String description;

    /**
     * 是否全员可见 0-否 1-是
     */
    @TableField("visible_to_all")
    private Boolean visibleToAll;

    public Boolean isVisibleToAll() {
        return visibleToAll;
    }

}