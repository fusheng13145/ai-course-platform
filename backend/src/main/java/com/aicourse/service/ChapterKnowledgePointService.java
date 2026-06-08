package com.aicourse.service;

import com.aicourse.entity.ChapterKnowledgePoint;
import com.aicourse.entity.KnowledgePoint;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 章节-知识点关联服务接口
 * 提供章节与知识点的映射管理功能
 */
public interface ChapterKnowledgePointService extends IService<ChapterKnowledgePoint> {

    /**
     * 获取章节关联的知识点
     * @param chapterId 章节ID
     * @return 知识点列表
     */
    List<KnowledgePoint> getPointsByChapter(Long chapterId);

    /**
     * 批量设置章节-知识点映射（先删后增）
     * @param chapterId 章节ID
     * @param pointIds 知识点ID列表
     */
    void batchSetMapping(Long chapterId, List<Long> pointIds);
}
