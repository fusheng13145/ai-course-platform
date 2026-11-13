package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.Resource;
import com.aicourse.entity.ResourceVersion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ResourceMapper;
import com.aicourse.service.ResourceService;
import com.aicourse.service.ResourceVersionService;
import com.aicourse.vo.ChapterResourceTreeVO;
import com.aicourse.vo.ResourceStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 课程资源服务实现类
 * 实现资源的访问权限校验、资源树构建、搜索、统计、可见性管理及章节资源查询等核心逻辑
 */
@Service
public class ResourceServiceImpl extends ServiceImpl<ResourceMapper, Resource> implements ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceServiceImpl.class);

    private final ResourceVersionService resourceVersionService;

    public ResourceServiceImpl(ResourceVersionService resourceVersionService) {
        this.resourceVersionService = resourceVersionService;
    }

    /**
     * 获取课程的资源树（按章节组织）
     */
    @Override
    public List<ChapterResourceTreeVO> getResourceTree(Long courseId) {
        // 查询课程下所有资源
        List<Resource> allResources = this.list(
                new LambdaQueryWrapper<Resource>()
                        .eq(Resource::getCourseId, courseId)
                        // 内容审核（③）读路径执行点：已隐藏资源不进入资源树
                        .ne(Resource::getModerationStatus, Resource.MODERATION_HIDDEN)
                        .orderByAsc(Resource::getChapterId)
                        .orderByDesc(Resource::getUploadTime)
        );

        // 按 chapterId 分组
        Map<Long, List<Resource>> groupedByChapter = allResources.stream()
                .collect(Collectors.groupingBy(r -> r.getChapterId() != null ? r.getChapterId() : 0L));

        List<ChapterResourceTreeVO> tree = new ArrayList<>();
        for (Map.Entry<Long, List<Resource>> entry : groupedByChapter.entrySet()) {
            tree.add(new ChapterResourceTreeVO(entry.getKey(), entry.getValue()));
        }
        return tree;
    }

    /**
     * 资源搜索（按标题/描述模糊搜索）
     */
    @Override
    public Page<Resource> searchResources(String keyword, Long courseId, int page, int size) {
        Page<Resource> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<Resource>()
                .and(w -> w.like(Resource::getFileName, keyword)
                        .or()
                        .like(Resource::getDescription, keyword))
                .eq(courseId != null, Resource::getCourseId, courseId)
                // 内容审核（③）读路径执行点：已隐藏资源不出现在搜索结果里
                .ne(Resource::getModerationStatus, Resource.MODERATION_HIDDEN)
                .orderByDesc(Resource::getUploadTime);
        return this.page(pageParam, queryWrapper);
    }

    /**
     * 获取资源统计信息（类型分布、大小总计等）
     */
    @Override
    public ResourceStatsVO getResourceStats(Long courseId) {
        // 查询课程下所有资源
        LambdaQueryWrapper<Resource> baseQuery = new LambdaQueryWrapper<Resource>()
                .eq(Resource::getCourseId, courseId)
                // 内容审核（③）读路径执行点：统计口径同样不计已隐藏资源（否则"已隐藏却仍计入总数"自相矛盾）
                .ne(Resource::getModerationStatus, Resource.MODERATION_HIDDEN);
        List<Resource> resources = this.list(baseQuery);

        // 按文件类型分组统计数量
        Map<String, Long> typeDistribution = resources.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getFileType() != null ? r.getFileType() : "unknown",
                        Collectors.counting()
                ));

        // 批量取 active 版本（B-03 N+1 治理，2026-11-07）：此前逐资源 getActiveVersion，
        // 大课程（百级资源）会放大成百次单查；改为一次 IN 查询后按 resourceId 取用。
        Map<Long, ResourceVersion> activeVersionByResource = loadActiveVersions(resources);

        // 统计总数和总大小（通过版本表获取文件大小）
        long totalCount = resources.size();
        long totalSize = 0L;
        for (Resource resource : resources) {
            ResourceVersion activeVersion = activeVersionByResource.get(resource.getId());
            if (activeVersion != null && activeVersion.getFileSize() != null) {
                totalSize += activeVersion.getFileSize();
            }
        }

        return new ResourceStatsVO(totalCount, totalSize, typeDistribution);
    }

    /**
     * 一次 IN 查询加载资源集合的全部 active 版本，返回 resourceId → ResourceVersion。
     * 同一资源出现多行 active（历史数据异常）时保留版本号更高的一行，与单查语义保持兼容。
     */
    private Map<Long, ResourceVersion> loadActiveVersions(List<Resource> resources) {
        List<Long> resourceIds = resources.stream()
                .map(Resource::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        return resourceVersionService.list(new LambdaQueryWrapper<ResourceVersion>()
                        .in(ResourceVersion::getResourceId, resourceIds)
                        .eq(ResourceVersion::getIsActive, true))
                .stream()
                .filter(v -> v.getResourceId() != null)
                .collect(Collectors.toMap(ResourceVersion::getResourceId, v -> v, (a, b) -> {
                    int av = a.getVersionNumber() != null ? a.getVersionNumber() : 0;
                    int bv = b.getVersionNumber() != null ? b.getVersionNumber() : 0;
                    return av >= bv ? a : b;
                }));
    }

    /**
     * 资源可见性切换
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleVisibility(Long resourceId, Boolean visibleToAll) {
        log.info("切换资源");

        Resource resource = this.getById(resourceId);
        if (resource == null) {
            throw new BusinessException("资源不存在");
        }
        resource.setVisibleToAll(visibleToAll);
        this.updateById(resource);
    }

    /**
     * 获取课程下某章节的资源列表
     */
    @Override
    public List<Resource> getResourcesByChapter(Long courseId, Long chapterId) {
        return this.list(
                new LambdaQueryWrapper<Resource>()
                        .eq(Resource::getCourseId, courseId)
                        .eq(Resource::getChapterId, chapterId)
                        // 内容审核（③）读路径执行点：已隐藏资源不进入章节目录
                        .ne(Resource::getModerationStatus, Resource.MODERATION_HIDDEN)
                        .orderByDesc(Resource::getUploadTime)
        );
    }
}
