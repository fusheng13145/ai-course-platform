package com.aicourse.service;

/**
 * RAG服务接口
 * 对接Python RAG微服务（端口 8002），实现基于知识库的智能问答
 * Python端需实现对应的 /api/v1/rag/* 接口
 */
public interface RAGService {

    /**
     * 健康检查
     * @return true表示Python服务可达
     */
    boolean healthCheck();

    /**
     * 基于知识库的问答
     * @param question 用户问题
     * @param courseId 课程ID（可选，为空则搜索全部知识库）
     * @param topK 返回结果数量
     * @return 问答结果JSON，包含答案和引用来源
     */
    String ask(String question, Long courseId, int topK);

    /**
     * 向知识库添加文档
     * @param courseId 课程ID
     * @param documentName 文档名称
     * @param content 文档内容
     * @return 操作结果JSON
     */
    String addDocument(Long courseId, String documentName, String content);

    /**
     * 从知识库删除文档
     * @param documentId 文档ID
     * @return 操作结果
     */
    String deleteDocument(Long documentId);

    /**
     * 获取知识库文档列表
     * @param courseId 课程ID
     * @return 文档列表JSON
     */
    String listDocuments(Long courseId);

    /**
     * 更新文档内容
     * @param documentId 文档ID
     * @param content 新内容
     * @return 操作结果
     */
    String updateDocument(Long documentId, String content);

    /**
     * 构建/更新课程向量索引
     * @param courseId 课程ID
     * @return 操作结果
     */
    String buildIndex(Long courseId);

    /**
     * 清理课程索引
     * @param courseId 课程ID
     * @return 操作结果
     */
    String clearIndex(Long courseId);

    /**
     * 检索相似文档（仅召回，不生成答案）
     * @param query 查询文本
     * @param courseId 课程ID
     * @param topK 返回数量
     * @return 召回结果JSON
     */
    String retrieve(String query, Long courseId, int topK);
}