package com.aicourse.service.component.ai;

import java.util.function.Supplier;

/**
 * 当前线程的"AI 调用归属上下文"。
 *
 * <p><b>为什么需要它：</b>真正发起大模型调用的位置是 {@link AiCoreComponent}（token 估算、供应商、
 * 耗时都在那里才拿得到），而调用者身份（userId）、归属课程（courseId）与业务场景（scene）只在
 * 控制器入口处才知道。两者之间隔着若干层服务调用，若逐层透传参数会把 AI 服务签名全部污染，
 * 故用<b>显式作用域</b>（{@link #run}）把入口信息带到执行点。</p>
 *
 * <p>⚠️ 两条使用纪律（写进手册 §19 相邻条）：
 * <ol>
 *   <li>只允许通过 {@link #run} 成对设置/清理，且清理必须在 {@code finally} 里——
 *       否则线程池复用会把上一个请求的归属串到下一个请求（污染统计，比不记更糟）；</li>
 *   <li>该上下文是 {@link ThreadLocal}，<b>跨线程即失效</b>：本控制器的 AI 端点以
 *       {@code Callable} 在 MVC 异步线程上执行，作用域必须设在 {@code Callable} 内部；
 *       流式（SSE）路径的实际调用发生在订阅线程上，须在作用域内用 {@link #capture()}
 *       捕获 {@link Snapshot} 随 Flux 闭包携带，终止回调才能拿到归属。</li>
 * </ol>
 */
public final class AiCallContext {

    private static final ThreadLocal<AiCallContext> HOLDER = new ThreadLocal<>();

    private final Long userId;
    private final Long courseId;
    private final String scene;

    private AiCallContext(Long userId, Long courseId, String scene) {
        this.userId = userId;
        this.courseId = courseId;
        this.scene = scene;
    }

    /**
     * 调用归属的不可变快照：供流式（SSE）路径在作用域内捕获后，随 Flux 闭包跨线程携带。
     *
     * <p>流式订阅发生在 Reactor 线程上，{@link ThreadLocal} 作用域跨线程即失效；
     * 快照把「归属三元组」从 Tomcat 线程显式搬运到流终止回调，是流式调用落库的唯一归属来源。</p>
     */
    public record Snapshot(Long userId, Long courseId, String scene) {
    }

    /**
     * 捕获当前线程作用域的不可变快照；未处于作用域内返回 {@code null}（流式路径据此跳过落库，
     * 与同步路径"宁可少记、不记错归属"的口径一致）。
     */
    public static Snapshot capture() {
        AiCallContext ctx = HOLDER.get();
        return ctx == null ? null : new Snapshot(ctx.userId, ctx.courseId, ctx.scene);
    }

    /** 设置当前线程的调用归属（由 {@link #run} 调用，业务代码不要直接调用）。 */
    public static void set(Long userId, Long courseId, String scene) {
        HOLDER.set(new AiCallContext(userId, courseId, scene));
    }

    /**
     * 在"AI 调用归属"作用域内执行一段逻辑：进入时设置，退出时（含异常路径）清理。
     *
     * <p>控制器入口统一用它包裹会触发大模型调用的那段代码，例如：</p>
     * <pre>{@code
     * return () -> AiCallContext.run(userId, cid, "QA_ASK", () -> { ... 原有逻辑 ... });
     * }</pre>
     *
     * <p>⚠️ 只允许这一种用法——不要自己 {@code set} 之后忘记 {@code clear}（见类注释第 1 条纪律）。</p>
     *
     * @param userId   调用者用户ID（可空）
     * @param courseId 归属课程ID（可空 = 未绑定课程）
     * @param scene    业务场景
     * @param body     产生 AI 调用的业务逻辑
     * @return {@code body} 的返回值
     */
    public static <T> T run(Long userId, Long courseId, String scene, Supplier<T> body) {
        set(userId, courseId, scene);
        try {
            return body.get();
        } finally {
            // 必须在 finally 清理：线程池复用会把归属串到下一个请求
            clear();
        }
    }

    /** 读取当前线程的调用归属；未处于作用域内返回 {@code null}（此时不落库）。 */
    public static AiCallContext get() {
        return HOLDER.get();
    }

    /** 清理当前线程的调用归属。 */
    public static void clear() {
        HOLDER.remove();
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getScene() {
        return scene;
    }
}
