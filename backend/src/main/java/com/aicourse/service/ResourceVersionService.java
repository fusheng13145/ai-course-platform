package com.aicourse.service;

import com.aicourse.entity.ResourceVersion;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 资源版本服务接口
 * 提供资源版本的创建、历史查询、回滚及当前版本获取等能力
 */
public interface ResourceVersionService extends IService<ResourceVersion> {

    /**
     * 创建新版本
     * 自动计算版本号，默认设为当前激活版本并将其他版本置为非激活
     * @param resourceId 关联资源ID
     * @param fileName 文件名
     * @param fileType 文件类型
     * @param url 资源地址
     * @param description 版本说明
     * @param uploaderId 上传人ID
     * @param fileSize 文件大小（字节）
     * @return 新创建的版本实体
     */
    ResourceVersion createNewVersion(Long resourceId, String fileName, String fileType,
                                     String url, String description, Long uploaderId, Long fileSize);

    /**
     * 获取资源的版本历史列表
     * @param resourceId 资源ID
     * @return 版本列表（按版本号降序）
     */
    List<ResourceVersion> getVersionHistory(Long resourceId);

    /**
     * 回滚到指定版本
     * 将目标版本设为当前激活版本，其他版本置为非激活
     * @param resourceId 资源ID
     * @param versionNumber 目标版本号
     */
    void rollbackToVersion(Long resourceId, Integer versionNumber);

    /**
     * 获取资源当前启用的版本
     * @param resourceId 资源ID
     * @return 当前激活的版本实体
     */
    ResourceVersion getActiveVersion(Long resourceId);
}
