package com.aicourse.service;

import java.util.Map;

/**
 * 学习推荐服务接口
 * 聚合选课进度、薄弱知识点、近期成绩等学情数据，构建数据驱动上下文后调用 LLM 生成个性化推荐。
 */
public interface LearningRecommendationService {

    /**
     * 生成个性化学习推荐（数据驱动 + LLM 解释层）
     * @param userId 学生ID；为 null 时返回"请先登录"提示
     * @return 推荐结果，包含 recommendation（推荐语）与 dataDriven（是否数据驱动）等字段
     */
    Map<String, Object> recommendLearning(Long userId);
}
