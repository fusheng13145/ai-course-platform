package com.aicourse.service;

import java.util.List;
import java.util.Map;

/**
 * GraphRAG 图谱增强检索服务。
 * <p>
 * 在现有文本检索（BM25）基础上，叠加知识图谱的结构化信息：
 * <ol>
 *   <li><b>实体链接</b>：将用户问题中的关键词匹配到课程知识点实体；</li>
 *   <li><b>子图检索</b>：以匹配实体为种子，在 {@code knowledge_point_relations}
 *       表上做 BFS 遍历（1-2 跳），收集关联知识点与关系三元组；</li>
 *   <li><b>上下文构建</b>：将子图信息格式化为 LLM 可消费的 Prompt 片段。</li>
 * </ol>
 * 该服务是纯 MySQL 实现，不依赖 Neo4j/外部图数据库。知识图谱数据由团队 Python
 * 侧负责灌入 {@code knowledge_points} + {@code knowledge_point_relations} 表，
 * 本服务在数据就绪后即时可用。
 */
public interface GraphRAGService {

    /**
     * 从课程知识图谱中检索与问题相关的图谱上下文。
     *
     * @param question  用户问题文本
     * @param courseId  课程 ID
     * @param maxHops   子图扩展最大跳数（建议 1-2）
     * @return {@code {"entities": [...], "triples": [...], "relatedPoints": [...]}}
     *         若图谱为空或未匹配到实体，返回空结果
     */
    Map<String, Object> retrieveGraphContext(String question, Long courseId, int maxHops);

    /**
     * 将图谱上下文格式化为 LLM Prompt 可注入的文本片段。
     *
     * @param graphContext  {@link #retrieveGraphContext} 的返回值
     * @return 格式化的文本（如 "【知识图谱关联信息】\n- A 是 B 的前置知识\n..."）
     */
    String formatGraphContextForPrompt(Map<String, Object> graphContext);

    /**
     * 判断当前课程知识图谱是否有可用数据。
     */
    boolean isGraphAvailable(Long courseId);
}
