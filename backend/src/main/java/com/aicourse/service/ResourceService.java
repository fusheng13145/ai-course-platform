package com.aicourse.service;

import com.aicourse.entity.Resource;
import com.aicourse.vo.ChapterResourceTreeVO;
import com.aicourse.vo.ResourceStatsVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 课程资源服务接口
 * 提供资源的访问权限校验、资源树构建、搜索、统计、可见性管理及章节资源查询等能力
 */
public interface ResourceService extends IService<Resource> {

    /**
     * 资源访问权限校验
     * 校验指定用户是否有权访问该资源（基于 visibleToAll 字段）
     * @param resourceId 资源ID
     * @param userId 用户ID
     * @return 是否有访问权限
     */
    boolean checkAccessPermission(Long resourceId, Long userId);

    /**
     * 获取课程的资源树（按章节组织）
     * 查询课程下所有资源，按 chapterId 分组返回树形结构
     * @param courseId 课程ID
     * @return 资源树列表
     */
    List<ChapterResourceTreeVO> getResourceTree(Long courseId);

    /**
     * 资源搜索（按标题/描述模糊搜索）
     * @param keyword 搜索关键词
     * @param courseId 限定课程ID（可选）
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页搜索结果
     */
    Page<Resource> searchResources(String keyword, Long courseId, int page, int size);

    /**
     * 获取资源统计信息（类型分布、大小总计等）
     * @param courseId 课程ID
     * @return 统计数据Map
     */
    ResourceStatsVO getResourceStats(Long courseId);

    /**
     * 资源可见性切换
     * @param resourceId 资源ID
     * @param visibleToAll 是否全员可见
     */
    void toggleVisibility(Long resourceId, Boolean visibleToAll);

    /**
     * 获取课程下某章节的资源列表
     * @param courseId 课程ID
     * @param chapterId 章节ID
     * @return 该章节下的资源列表
     */
    List<Resource> getResourcesByChapter(Long courseId, Long chapterId);
}
