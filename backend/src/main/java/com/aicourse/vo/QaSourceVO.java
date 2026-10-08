package com.aicourse.vo;

import lombok.Data;

/**
 * 智能问答的单条课程资料出处（{@code POST /ai/qa/ask} 响应中 {@code sources} 的元素）。
 * <p>
 * 字段与 Python RAG 引擎 {@code /api/v1/rag/ask} 的召回项一一对应，经
 * {@code RagContract.RagSource} 中转后在控制器出口做 HTML 转义，与 {@code answer} 同一口径。
 */
@Data
public class QaSourceVO {

    /** 引擎侧文档 ID（仅用于展示与去重，Java 侧不持久化引用） */
    private Long documentId;

    /** 文档名称，如「计算机网络-第6章-传输层TCP与UDP」 */
    private String documentName;

    /** 相关度（RRF 归一化后的 0~1 值） */
    private Double relevanceScore;

    /** 命中的原文片段 */
    private String snippet;

    public QaSourceVO() {
    }

    public QaSourceVO(Long documentId, String documentName, Double relevanceScore, String snippet) {
        this.documentId = documentId;
        this.documentName = documentName;
        this.relevanceScore = relevanceScore;
        this.snippet = snippet;
    }
}
