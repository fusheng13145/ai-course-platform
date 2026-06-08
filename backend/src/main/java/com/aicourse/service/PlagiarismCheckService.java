package com.aicourse.service;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.PlagiarismMatch;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 查重检测服务接口
 */
public interface PlagiarismCheckService extends IService<PlagiarismCheck> {

    /**
     * 创建查重任务（初始状态为PENDING）
     * @param submissionId 提交记录ID
     * @param checkType    查重类型（TEXT/SOURCE_CODE）
     * @return 新创建的查重任务
     */
    PlagiarismCheck createCheck(Long submissionId, String checkType);

    /**
     * 执行查重检测（文本相似度比较算法）
     * @param checkId 查重任务ID
     */
    void executeCheck(Long checkId);

    /**
     * 获取查重结果详情
     * @param checkId 查重任务ID
     * @return 查重结果详情Map
     */
    Map<String, Object> getCheckResult(Long checkId);

    /**
     * 获取高相似度匹配列表（超过指定阈值）
     * @param checkId   查重任务ID
     * @param threshold 相似度阈值
     * @return 高相似度匹配明细列表
     */
    List<PlagiarismMatch> getHighSimilarityMatches(Long checkId, BigDecimal threshold);

    /**
     * 获取某提交的所有查重历史记录
     * @param submissionId 提交记录ID
     * @return 查重历史列表
     */
    List<PlagiarismCheck> getSubmissionChecks(Long submissionId);
}
