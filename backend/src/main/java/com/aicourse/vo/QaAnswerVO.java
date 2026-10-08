package com.aicourse.vo;

import lombok.Data;

import java.util.List;

/**
 * 智能问答出参（{@code POST /ai/qa/ask} 的业务数据，{@code sessionId} 由控制器补入）。
 * <p>
 * {@code sources} 为 RAG 检索命中的课程资料片段，用于界面「溯源」展示；
 * 降级为纯 LLM 直答时为空列表（不返回 null，前端据此判断是否渲染出处区块）。
 */
@Data
public class QaAnswerVO {

    private String answer;

    private List<QaSourceVO> sources;

    public QaAnswerVO() {
    }

    public QaAnswerVO(String answer, List<QaSourceVO> sources) {
        this.answer = answer;
        this.sources = sources;
    }
}
