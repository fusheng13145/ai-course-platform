package com.aicourse.integration.support;

import com.aicourse.integration.ContractPaths;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketTimeoutException;

/**
 * 下游 HTTP 调用（RAG / 知识图谱 / DeepGraph）共用的失败分类与退避策略。
 *
 * <p><b>为什么必须只有一处</b>：这三个客户端各自抄了一份
 * {@code isExplicitFailure / sleep / isReadTimeout / isServiceUnavailable}（阶段2 DUP-10~13，
 * 12 份拷贝），再加上 RAG 与 DeepGraph 各一份 {@code isConnectFailure}。
 * 这类判定的后果是「要不要重试」，一旦某个客户端漏改其中一条，
 * 表现就是<b>同一次故障在不同端点上重试次数不同</b>——读超时的那一路会把问答端到端拖长 1+2 秒，
 * 直接破坏 {@code <5s} 指标，而排查时看起来只是"某个接口今天有点慢"。</p>
 *
 * <p><b>策略的单一事实源</b>（对应各客户端 {@code executeWithRetry} 里的 break 条件）：
 * <ul>
 *   <li>读超时 —— 不重试：重试只会把无效等待叠加，雪崩时放大故障；</li>
 *   <li>连接被拒 —— 不重试：对端进程没起，重试没有任何意义；</li>
 *   <li>HTTP 503 —— 不重试：对端已明确宣告不可用（契约化后由状态码表达）。</li>
 * </ul>
 * 三者都沿 {@code cause} 链判断：RestClient 会把 {@link SocketTimeoutException} /
 * {@link java.net.ConnectException} / 5xx 包成 {@code ResourceAccessException} 或
 * 上层异常，只看顶层类型一定漏判。</p>
 */
@Slf4j
public final class DownstreamRetrySupport {

    private DownstreamRetrySupport() {
    }

    /**
     * 响应体显式声明 {@code success=false} 时视为失败；缺省 {@code success} 的历史响应保持兼容。
     *
     * <p>Python 侧引擎不可用可能以 HTTP 200 + {@code {code:503, success:false}} 返回，
     * 只看字段是否存在会把"删除/新增失败"当成成功。</p>
     */
    public static boolean isExplicitFailure(JsonNode root) {
        return root != null
                && root.has(ContractPaths.R_SUCCESS)
                && root.get(ContractPaths.R_SUCCESS).isBoolean()
                && !root.get(ContractPaths.R_SUCCESS).asBoolean();
    }

    /** 指数退避（线性递增）：第 n 次重试等待 n 秒。 */
    public static void sleep(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
        } catch (InterruptedException ie) {
            log.warn("重试等待被中断", ie);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 是否为读超时：RestClient 会把底层 {@link SocketTimeoutException} 包装为
     * {@code ResourceAccessException}，也可能再被 {@code java.net.http} 的时间异常包一层。
     */
    public static boolean isReadTimeout(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SocketTimeoutException
                    || t instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /** 是否为连接失败（下游进程未启动最常见），沿 cause 链找 {@link java.net.ConnectException}。 */
    public static boolean isConnectFailure(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof java.net.ConnectException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * 是否为「引擎明确宣告不可用」（HTTP 503，契约化后由状态码表达）。
     *
     * <p>RestClient 会把 5xx 包成 {@code HttpServerErrorException}，且可能再被上层包装，
     * 因此沿 cause 链找并比对 {@link ContractPaths#CODE_UNAVAILABLE}。</p>
     */
    public static boolean isServiceUnavailable(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof org.springframework.web.client.HttpServerErrorException hse) {
                return hse.getStatusCode().value() == ContractPaths.CODE_UNAVAILABLE;
            }
            t = t.getCause();
        }
        return false;
    }
}
