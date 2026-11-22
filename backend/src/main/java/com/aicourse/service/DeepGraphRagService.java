package com.aicourse.service;

import com.aicourse.integration.deepgraph.DeepGraphRagContract.DeepGraphRetrievalResult;

/**
 * DeepGraph RAG 服务接口（文档图谱检索增强，Python 微服务 8501）。
 * <p>
 * 对接 {@code python-services/deepgraph_rag/}（已验收 DeepSeek-RAG-Chatbot 原样复制 + 契约适配器）。
 * 与 8002 {@link RAGService} 互补：本服务面向<b>整篇课程文档</b>的混合检索与 Neo4j GraphRAG
 * 关系上下文；问答主链经 {@code CourseEvidenceComponent} 把两路召回合并接地，引擎未启用/不可达时
 * 本服务返回 503 降级、上层静默忽略，行为与未集成前完全一致。
 * </p>
 */
public interface DeepGraphRagService {

    /** 健康检查：true 表示 8501 服务可达。 */
    boolean healthCheck();

    /** 健康状态 JSON（含 enabled 与可达性，运维/前端降级提示用）。 */
    String healthJson();

    /**
     * 检索（类型化返回，供问答链判定 {@code mode}/{@code code} 并合并证据）。
     * 引擎不可用时 {@code mode=UNAVAILABLE} 且 {@code code=503}。
     */
    DeepGraphRetrievalResult retrieveTyped(String query, Long courseId, int topK);

    /** 检索（JSON 透传，管理面板/调试用）。 */
    String retrieve(String query, Long courseId, int topK);

    /** 文档入库（整篇文本走原实现 process_documents 全链路），返回 JSON。 */
    String ingest(Long courseId, String documentName, String content);

    /** 二进制文档入库（PDF/DOCX/TXT 原文，raw body 转发 8501），返回 JSON。 */
    String ingestFile(Long courseId, String documentName, byte[] content, String contentType);

    /** 课程文档列表 JSON（courseId 为空时由控制器层先做权限收口）。 */
    String listDocuments(Long courseId);

    /** 删除课程文档（连同向量索引/图谱/章节数据），返回 JSON。 */
    String deleteDocument(Long courseId, String documentName);

    /** 章节知识图谱构建（LLM 三元组抽取 + Neo4j 同步），返回 JSON。 */
    String chapterGraph(Long courseId, String documentName, String chapterName, String chapterText);

    /** 学习路径规划（Neo4j 反向追溯前置知识），返回 JSON。 */
    String learningPath(String target, Long courseId, int maxDepth, boolean searchAllSources);

    /**
     * 把引擎聚合的课程图谱（LLM 真实抽取的章节三元组 + 层次大纲骨架）幂等落库
     * MySQL {@code knowledge_points}/{@code knowledge_point_relations}——平台
     * 「知识图谱」页（/knowledge/points/graph）渲染的数据源。
     *
     * @return 同步摘要 JSON（added/ skipped/总数）
     */
    String syncCourseKnowledgeGraph(Long courseId, String documentName);
}
