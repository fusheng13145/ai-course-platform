package com.aicourse.vo;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 流式智能问答的服务层聚合结果：把「逐段回答增量」与「检索出处」分开承载，
 * 使 SSE 端点在推送 token 之外，还能在流末尾下发课程资料出处（供界面溯源）。
 *
 * <p>与 {@link QaAnswerVO} 同构（同步问答的 {@code answer} 在此对应 {@code tokens} 流），
 * 出处字段亦与之对齐，便于两条问答链路复用同一套展示口径。</p>
 */
public class QaStreamVO {

    /** 逐段回答增量（已做输出护栏清洗）。 */
    private final Flux<String> tokens;

    /** 检索命中的课程资料出处；未检索/检索失败（降级纯 LLM）时为空列表，不为 null。 */
    private final List<QaSourceVO> sources;

    public QaStreamVO(Flux<String> tokens, List<QaSourceVO> sources) {
        this.tokens = tokens;
        this.sources = sources == null ? List.of() : sources;
    }

    public Flux<String> getTokens() {
        return tokens;
    }

    public List<QaSourceVO> getSources() {
        return sources;
    }
}
