package com.aicourse.service;

import com.aicourse.entity.PlagiarismMatch;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 查重匹配明细服务接口
 */
public interface PlagiarismMatchService extends IService<PlagiarismMatch> {

    /**
     * 批量保存匹配明细记录
     * @param checkId 查重任务ID
     * @param matches 匹配明细数据列表
     */
    void batchSaveMatches(Long checkId, List<Map<String, Object>> matches);

    /**
     * 获取原文对照视图（用于前端展示两段文本的对比）
     * @param matchId 匹配记录ID
     * @return 对照视图信息Map
     */
    Map<String, Object> getComparisonView(Long matchId);
}
