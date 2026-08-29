package com.aicourse.service;

import java.util.List;
import java.util.Map;

/**
 * 全站知识点搜索服务（D6）。
 * 统一检索「知识点 / 题目 / 课程 / 笔记」四类内容，返回带相关度评分的扁平结果，
 * 供前端全局搜索栏渲染与跳转。
 */
public interface SearchService {

    /**
     * 全站聚合搜索。
     * 评分策略：对查询词做 CJK bigram + 英文分词（与查重引擎同源策略），
     * 名称/标题命中权重 0.7、内容命中权重 0.3，命中率 &gt; 0 即入候选并按分数降序。
     *
     * @param keyword 搜索关键字（非空）
     * @param types   参与检索的类型子集（knowledge / question / course / note）
     * @param userId  当前用户ID（note 类型仅检索本人笔记）
     * @param limit   返回条数上限
     * @return 结果列表项：{type, id, title, snippet, score}
     */
    List<Map<String, Object>> search(String keyword, List<String> types, Long userId, int limit);
}
