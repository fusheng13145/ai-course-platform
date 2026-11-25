package com.aicourse.config;

import java.util.List;

/**
 * 跨域来源解析的唯一实现（DEF-07）。
 *
 * <p>HTTP CORS 与 WebSocket 握手必须用同一套来源判定。此前两条链路各写一份「未配置就回退
 * localhost」的逻辑：HTTP 侧尊重 {@code app.cors.dev-fallback}，WebSocket 侧无条件回退。
 * 于是生产环境把 dev-fallback 关掉后，浏览器跨域请求被拒，但 WebSocket 仍然接受
 * {@code http://localhost:5173}，形成一个「HTTP 拒绝、WS 放行」的口子。
 *
 * <p>口径：
 * <ol>
 *     <li>配置了 {@code cors.allowed-origins} → 一律以配置为准，不做任何兜底；</li>
 *     <li>未配置且 dev-fallback=true → 回退本地开发地址（仅开发便利）；</li>
 *     <li>未配置且 dev-fallback=false → 返回空列表，调用方据此 fail-closed。</li>
 * </ol>
 */
public final class CorsOriginResolver {

    /** 本地前端开发地址；仅 dev-fallback 生效时使用。 */
    public static final String DEV_FALLBACK_ORIGIN = "http://localhost:5173";

    private CorsOriginResolver() {
    }

    /**
     * @param configured   {@code cors.allowed-origins} 绑定值，可为 null
     * @param devFallback  {@code app.cors.dev-fallback}，生产应为 false
     * @return 非 null 的来源列表；未配置且关闭兜底时返回空列表
     */
    public static List<String> resolve(List<String> configured, boolean devFallback) {
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        return devFallback ? List.of(DEV_FALLBACK_ORIGIN) : List.of();
    }

    /** 解析结果是否为空（空表示 fail-closed：拒绝一切跨域）。 */
    public static boolean isFailClosed(List<String> resolved) {
        return resolved == null || resolved.isEmpty();
    }
}
