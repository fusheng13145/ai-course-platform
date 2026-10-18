package com.aicourse.exception;

import com.aicourse.common.ApiCode;

/**
 * 大模型上游不可用（503）。
 *
 * <p><b>为什么要有这个类型</b>：原先 {@code LLMProvider} 把「未配置密钥」「401 凭据被拒」「额度用尽」
 * 「网络不可达」「返回体没有 choices」统统塌成一个 {@code null}，上层再统一换成
 * {@code AiCoreComponent.SERVICE_ERROR_MSG} 并以 {@code ApiResponse.success(...)} 发出——
 * 于是 HTTP 200 / code 200 / message=success，只有正文是一句"暂时不可用"。实测代价（手册 DR-74）：
 * 问答与批改把失败伪装成成功，出题把这句中文当成模型输出的 JSON 去解析，报成
 * "AI生成题目解析失败: Unrecognized token 'AI'"，查重失败被落成 {@code LOW_RISK}（把"没查成"报成"没抄袭"）。</p>
 *
 * <p><b>本类型确立的不变量</b>（三层各自只留一种含义）：
 * <ul>
 *   <li>供应商返回<b>非空字符串</b> ⇒ 真实生成成功；</li>
 *   <li>供应商返回 <b>null</b> ⇒ <b>仅</b>表示「未配置 API Key」，由 {@code AiCoreComponent} 转成本异常；</li>
 *   <li>供应商<b>抛本异常</b> ⇒ 调用确实失败（凭据/额度/传输/空响应），必须让请求以 503 结束。</li>
 * </ul>
 * 与 Python 引擎侧既有口径同形：{@code mode=UNAVAILABLE / code=503}（手册 §34、{@code rag_service.py}）。</p>
 *
 * <p><b>与"兜底 catch"的关系</b>：多个业务组件用 {@code catch (Exception)} 把 AI 异常转成本地缺省值，
 * 那正是伪装成功的另一条路径。这些捕获点必须<b>先重新抛出本异常</b>再处理其它异常，
 * 否则 503 会被就地吃成"看起来正常"的空结果。</p>
 */
public class LLMUnavailableException extends BusinessException {

    /** 失败类别：决定给用户的可操作文案，也决定运维该查哪一处。 */
    public enum Kind {
        /** 未配置 API Key——部署形态问题，不是上游故障。 */
        NOT_CONFIGURED,
        /** 401/403 一类凭据被拒：Key 失效、填错、或不是该供应商的 Key。 */
        CREDENTIAL_REJECTED,
        /** 402/429 一类额度或限流。 */
        QUOTA_OR_THROTTLED,
        /** 网络不可达、超时、5xx。 */
        UPSTREAM_UNREACHABLE,
        /** 200 但没有可用内容（无 choices / content 空白）。 */
        EMPTY_RESPONSE
    }

    private final Kind kind;

    public LLMUnavailableException(Kind kind, String message) {
        super(ApiCode.SERVICE_UNAVAILABLE, message);
        this.kind = kind;
    }

    public LLMUnavailableException(Kind kind, String message, Throwable cause) {
        super(ApiCode.SERVICE_UNAVAILABLE, message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** 按 HTTP 状态给出类别，供 provider 的异常分支复用。 */
    public static Kind kindOfStatus(int status) {
        if (status == 401 || status == 403) {
            return Kind.CREDENTIAL_REJECTED;
        }
        if (status == 402 || status == 429) {
            return Kind.QUOTA_OR_THROTTLED;
        }
        return Kind.UPSTREAM_UNREACHABLE;
    }
}
