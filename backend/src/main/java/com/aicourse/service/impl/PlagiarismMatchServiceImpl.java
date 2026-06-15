package com.aicourse.service.impl;

import com.aicourse.entity.PlagiarismMatch;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.PlagiarismMatchMapper;
import com.aicourse.service.PlagiarismMatchService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查重匹配明细服务实现类
 */
@Service
public class PlagiarismMatchServiceImpl extends ServiceImpl<PlagiarismMatchMapper, PlagiarismMatch> implements PlagiarismMatchService {

    /**
     * 批量保存匹配明细记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveMatches(Long checkId, List<Map<String, Object>> matches) {
        if (matches == null || matches.isEmpty()) {
            return;
        }
        List<PlagiarismMatch> matchList = new ArrayList<>();
        for (Map<String, Object> matchData : matches) {
            PlagiarismMatch match = new PlagiarismMatch();
            match.setPlagiarismCheckId(checkId);
            match.setMatchedSubmissionId((Long) matchData.get("matchedSubmissionId"));
            match.setMatchedSource((String) matchData.get("matchedSource"));
            match.setSimilarity((BigDecimal) matchData.get("similarity"));
            match.setOriginalText((String) matchData.get("originalText"));
            match.setMatchedText((String) matchData.get("matchedText"));
            match.setMatchType((String) matchData.get("matchType"));
            matchList.add(match);
        }
        this.saveBatch(matchList);
    }

    /**
     * 获取原文对照视图（用于前端展示两段文本的对比）
     */
    @Override
    public Map<String, Object> getComparisonView(Long matchId) {
        PlagiarismMatch match = this.getById(matchId);
        if (match == null) {
            throw new BusinessException("匹配记录不存在");
        }
        Map<String, Object> view = new HashMap<>();
        view.put("matchId", match.getId());
        view.put("plagiarismCheckId", match.getPlagiarismCheckId());
        view.put("matchedSubmissionId", match.getMatchedSubmissionId());
        view.put("matchedSource", match.getMatchedSource());
        view.put("similarity", match.getSimilarity());
        view.put("matchType", match.getMatchType());
        // 原文对照信息
        Map<String, String> comparison = new HashMap<>();
        comparison.put("originalText", match.getOriginalText() != null ? match.getOriginalText() : "");
        comparison.put("matchedText", match.getMatchedText() != null ? match.getMatchedText() : "");
        view.put("comparison", comparison);
        // 相似度等级描述
        view.put("levelDescription", getLevelDescription(match.getSimilarity()));
        return view;
    }

    /**
     * 根据相似度值返回等级描述文字
     */
    private String getLevelDescription(BigDecimal similarity) {
        if (similarity == null) {
            return "未知";
        }
        int cmp = similarity.compareTo(new BigDecimal("0.7"));
        if (cmp >= 0) {
            return "高度疑似抄袭（相似度≥70%），建议重点审查";
        }
        cmp = similarity.compareTo(new BigDecimal("0.4"));
        if (cmp >= 0) {
            return "中度疑似改写（相似度40%-70%），建议人工复核";
        }
        cmp = similarity.compareTo(new BigDecimal("0.2"));
        if (cmp >= 0) {
            return "轻度相似（相似度20%-40%），存在一定参考嫌疑";
        }
        return "低度相似（相似度<20%），基本正常";
    }
}
