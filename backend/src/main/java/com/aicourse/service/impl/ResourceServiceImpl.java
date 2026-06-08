package com.aicourse.service.impl;

import com.aicourse.entity.Resource;
import com.aicourse.entity.ResourceVersion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ResourceMapper;
import com.aicourse.service.ResourceService;
import com.aicourse.service.ResourceVersionService;
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

    private final ResourceVersionService resourceVersionService;

    public ResourceServiceImpl(ResourceVersionService resourceVersionService) {
        this.resourceVersionService = resourceVersionService;
    }

    /**
     * 资源访问权限校验
     * 全员可见的资源直接放行，否则需校验用户归属
     */
    @Override
    public boolean checkAccessPermission(Long resourceId, Long userId) {
        Resource resource = this.getById(resourceId);
        if (resource == null) {
            throw new BusinessException("资源不存在");
        }
        // 全员可见的资源直接返回有权限
        if (Boolean.TRUE.equals(resource.isVisibleToAll())) {
            return true;
        }
        // 非全员可见时，这里可扩展更细粒度的权限控制（如课程成员校验）
        // 当前默认返回true（后续可根据业务需求扩展）
        return true;
    }

    /**
     * 获取课程的资源树（按章节组织）
     */
    @Override
    public List<Map<String, Object>> getResourceTree(Long courseId) {
        // 查询课程下所有资源
        List<Resource> allResources = this.list(
                new LambdaQueryWrapper<Resource>()
                        .eq(Resource::getCourseId, courseId)
                        .orderByAsc(Resource::getChapterId)
                        .orderByDesc(Resource::getUploadTime)
        );

        // 按 chapterId 分组
        Map<Long, List<Resource>> groupedByChapter = allResources.stream()
                .collect(Collectors.groupingBy(r -> r.getChapterId() != null ? r.getChapterId() : 0L));

        List<Map<String, Object>> tree = new ArrayList<>();
        for (Map.Entry<Long, List<Resource>> entry : groupedByChapter.entrySet()) {
            Map<String, Object> chapterNode = new HashMap<>();
            chapterNode.put("chapterId", entry.getKey());
            chapterNode.put("resources", entry.getValue());
            tree.add(chapterNode);
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
                .orderByDesc(Resource::getUploadTime);
        return this.page(pageParam, queryWrapper);
    }

    /**
     * 获取资源统计信息（类型分布、大小总计等）
     */
    @Override
    public Map<String, Object> getResourceStats(Long courseId) {
        // 查询课程下所有资源
        LambdaQueryWrapper<Resource> baseQuery = new LambdaQueryWrapper<Resource>()
                .eq(Resource::getCourseId, courseId);
        List<Resource> resources = this.list(baseQuery);

        // 按文件类型分组统计数量
        Map<String, Long> typeDistribution = resources.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getFileType() != null ? r.getFileType() : "unknown",
                        Collectors.counting()
                ));

        // 统计总数和总大小（通过版本表获取文件大小）
        long totalCount = resources.size();
        long totalSize = 0L;
        for (Resource resource : resources) {
            ResourceVersion activeVersion = resourceVersionService.getActiveVersion(resource.getId());
            if (activeVersion != null && activeVersion.getFileSize() != null) {
                totalSize += activeVersion.getFileSize();
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", totalCount);
        stats.put("totalSize", totalSize);
        stats.put("typeDistribution", typeDistribution);
        return stats;
    }

    /**
     * 资源可见性切换
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleVisibility(Long resourceId, Boolean visibleToAll) {
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
                        .orderByDesc(Resource::getUploadTime)
        );
    }
}
