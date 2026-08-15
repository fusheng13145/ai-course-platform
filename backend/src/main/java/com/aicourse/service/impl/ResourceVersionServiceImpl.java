package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.ResourceVersion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ResourceVersionMapper;
import com.aicourse.service.ResourceVersionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资源版本服务实现类
 * 实现资源版本的创建、历史查询、回滚及当前版本获取等核心逻辑
 */
@Service
public class ResourceVersionServiceImpl extends ServiceImpl<ResourceVersionMapper, ResourceVersion> implements ResourceVersionService {

    private static final Logger log = LoggerFactory.getLogger(ResourceVersionServiceImpl.class);

    /**
     * 创建新版本
     * 查询当前最大版本号+1作为新版本号，默认设为激活状态并将其他版本置为非激活
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceVersion createNewVersion(Long resourceId, String fileName, String fileType,
                                            String url, String description, Long uploaderId, Long fileSize) {
                                                log.info("创建资源版本");

        // 查询该资源当前最大版本号
        LambdaQueryWrapper<ResourceVersion> maxQuery = new LambdaQueryWrapper<ResourceVersion>()
                .eq(ResourceVersion::getResourceId, resourceId)
                .orderByDesc(ResourceVersion::getVersionNumber)
                .last("LIMIT 1");
        ResourceVersion latestVersion = this.getOne(maxQuery);

        int nextVersionNumber = (latestVersion != null && latestVersion.getVersionNumber() != null)
                ? latestVersion.getVersionNumber() + 1 : 1;

        // 将该资源的其他版本设为非激活
        this.lambdaUpdate()
                .eq(ResourceVersion::getResourceId, resourceId)
                .set(ResourceVersion::getIsActive, false)
                .update();

        // 创建新版本并设为激活
        ResourceVersion newVersion = new ResourceVersion();
        newVersion.setResourceId(resourceId);
        newVersion.setVersionNumber(nextVersionNumber);
        newVersion.setFileName(fileName);
        newVersion.setFileType(fileType);
        newVersion.setUrl(url);
        newVersion.setDescription(description);
        newVersion.setFileSize(fileSize);
        newVersion.setUploaderId(uploaderId);
        newVersion.setIsActive(true);
        newVersion.setCreatedAt(LocalDateTime.now());
        this.save(newVersion);

        return newVersion;
    }

    /**
     * 获取资源的版本历史列表（按版本号降序）
     */
    @Override
    public List<ResourceVersion> getVersionHistory(Long resourceId) {
        List<ResourceVersion> versions = this.list(
                new LambdaQueryWrapper<ResourceVersion>()
                        .eq(ResourceVersion::getResourceId, resourceId)
                        .orderByDesc(ResourceVersion::getVersionNumber)
        );
        return versions;
    }

    /**
     * 回滚到指定版本
     * 将目标版本设为激活状态，其他版本设为非激活
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackToVersion(Long resourceId, Integer versionNumber) {
        // 校验目标版本是否存在
        ResourceVersion targetVersion = this.getOne(
                new LambdaQueryWrapper<ResourceVersion>()
                        .eq(ResourceVersion::getResourceId, resourceId)
                        .eq(ResourceVersion::getVersionNumber, versionNumber)
        );
        if (targetVersion == null) {
            throw new BusinessException("目标版本不存在");
        }

        // 将该资源的所有版本设为非激活
        this.lambdaUpdate()
                .eq(ResourceVersion::getResourceId, resourceId)
                .set(ResourceVersion::getIsActive, false)
                .update();

        // 将目标版本设为激活
        targetVersion.setIsActive(true);
        this.updateById(targetVersion);
    }

    /**
     * 获取资源当前启用的版本
     */
    @Override
    public ResourceVersion getActiveVersion(Long resourceId) {
        return this.getOne(
                new LambdaQueryWrapper<ResourceVersion>()
                        .eq(ResourceVersion::getResourceId, resourceId)
                        .eq(ResourceVersion::getIsActive, true)
        );
    }
}
