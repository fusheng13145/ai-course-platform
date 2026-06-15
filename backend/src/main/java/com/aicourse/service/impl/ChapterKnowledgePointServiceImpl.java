package com.aicourse.service.impl;

import com.aicourse.entity.ChapterKnowledgePoint;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.mapper.ChapterKnowledgePointMapper;
import com.aicourse.service.ChapterKnowledgePointService;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 章节-知识点关联服务实现类
 * 提供章节与知识点的映射管理功能实现
 */
@Service
public class ChapterKnowledgePointServiceImpl extends ServiceImpl<ChapterKnowledgePointMapper, ChapterKnowledgePoint>
        implements ChapterKnowledgePointService {

    private final KnowledgePointService knowledgePointService;

    public ChapterKnowledgePointServiceImpl(KnowledgePointService knowledgePointService) {
        this.knowledgePointService = knowledgePointService;
    }

    /**
     * 获取章节关联的知识点列表
     */
    @Override
    public List<KnowledgePoint> getPointsByChapter(Long chapterId) {
        // 先查章节-知识点映射表获取知识点ID列表
        LambdaQueryWrapper<ChapterKnowledgePoint> wrapper = new LambdaQueryWrapper<ChapterKnowledgePoint>()
                .eq(ChapterKnowledgePoint::getChapterId, chapterId);
        List<ChapterKnowledgePoint> mappings = list(wrapper);

        if (mappings.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查出知识点详情
        List<Long> pointIds = mappings.stream()
                .map(ChapterKnowledgePoint::getKnowledgePointId)
                .collect(Collectors.toList());

        // 通过KnowledgePointService批量查询
        List<KnowledgePoint> points = pointIds.stream()
                .map(knowledgePointService::getById)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        return points;
    }

    /**
     * 批量设置章节-知识点映射（先删除旧映射，再新增）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSetMapping(Long chapterId, List<Long> pointIds) {
        // 删除该章节下所有旧映射
        LambdaQueryWrapper<ChapterKnowledgePoint> deleteWrapper = new LambdaQueryWrapper<ChapterKnowledgePoint>()
                .eq(ChapterKnowledgePoint::getChapterId, chapterId);
        remove(deleteWrapper);

        // 批量插入新映射
        if (pointIds != null && !pointIds.isEmpty()) {
            for (Long pointId : pointIds) {
                ChapterKnowledgePoint mapping = new ChapterKnowledgePoint();
                mapping.setChapterId(chapterId);
                mapping.setKnowledgePointId(pointId);
                save(mapping);
            }
        }
    }
}
