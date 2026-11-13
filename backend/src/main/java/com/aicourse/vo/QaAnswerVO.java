package com.aicourse.vo;

import lombok.Data;

import java.util.List;

/**
 * 智能问答出参（{@code POST /ai/qa/ask} 的业务数据，{@code sessionId} 由控制器补入）。
 * <p>
 * {@code sources} 为 RAG 检索命中的课程资料片段，用于界面「溯源」展示；
 * 降级为纯 LLM 直答时为空列表（不返回 null，前端据此判断是否渲染出处区块）。
 * <p>
 * {@code mode} 如实标记本次答案的证据来源：{@code ENGINE}=真实引擎召回 + 本端大模型生成；
 * {@code LOCAL}=引擎不可用时的本地知识点接地降级。验收与排障据此区分「引擎应答」与「降级作答」，
 * 前端可忽略该新增字段。
 */
@Data
public class QaAnswerVO {

    private String answer;

    private List<QaSourceVO> sources;

    private String mode;

    public QaAnswerVO() {
    }

    public QaAnswerVO(String answer, List<QaSourceVO> sources) {
        this(answer, sources, null);
    }

    public QaAnswerVO(String answer, List<QaSourceVO> sources, String mode) {
        this.answer = answer;
        this.sources = sources;
        this.mode = mode;
    }
}
