package com.aicourse.service;

/**
 * 课程知识库编排服务接口。
 * <p>
 * 解决「知识库只能由管理员逐篇手工上传文档、课程自身已有的结构化资料（课程简介 / 章节内容 / 章节资源）
 * 与知识库彻底脱节」的断链问题：把课程资料枚举出来，幂等地灌入 RAG 知识库并重建索引。
 * </p>
 * <p>
 * 职责边界（与 {@link RAGService}、{@code ContractPaths} 一致）：RAG 引擎（分词 / 向量 / 检索 / 索引）
 * 由团队另一成员以 Python 微服务（端口 8002）实现，本服务只做<b>消费侧编排</b>——枚举课程资料、
 * 按文档名幂等去重、调用 {@link RAGService} 的 {@code addDocument} / {@code buildIndex}。
 * </p>
 */
public interface CourseKnowledgeBaseService {

    /**
     * 从课程结构化资料构建知识库：枚举课程简介 / 章节 / 资源 → 幂等写入 → 重建课程索引。
     * <p>
     * 返回 JSON 文本（与 {@code RAGServiceImpl} 同风格，由控制器统一反序列化为结构化响应），
     * 关键字段：{@code courseId}、{@code courseName}、{@code mode}（ENGINE / LOCAL / UNAVAILABLE）、
     * {@code total}、{@code added}、{@code skipped}、{@code indexedChunks}、{@code documents}、
     * {@code note}，不可用时附 {@code error} / {@code code}（503）。
     * </p>
     *
     * @param courseId 课程ID
     * @return 构建结果 JSON 字符串；课程不存在时抛 {@code BusinessException(404)}
     */
    String buildFromCourse(Long courseId);
}
