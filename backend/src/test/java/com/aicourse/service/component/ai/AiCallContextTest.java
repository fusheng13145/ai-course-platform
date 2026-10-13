package com.aicourse.service.component.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiCallContext 单元测试：作用域的"设置-清理"必须成对，异常路径也要清理。
 *
 * <p>这是跨课程 AI 统计正确性的根：ThreadLocal 若在异常路径泄漏，线程池复用时
 * 下一个请求的调用会被记到上一个请求的课程上（污染统计，比不记更糟）。</p>
 */
class AiCallContextTest {

    @AfterEach
    void tearDown() {
        AiCallContext.clear();
    }

    @Test
    @DisplayName("run 在作用域内可见归属，退出后已清理")
    void run_setsAndClears() {
        String result = AiCallContext.run(5L, 4007L, "QA_ASK", () -> {
            AiCallContext ctx = AiCallContext.get();
            assertNotNull(ctx);
            assertEquals(5L, ctx.getUserId());
            assertEquals(4007L, ctx.getCourseId());
            assertEquals("QA_ASK", ctx.getScene());
            return "ok";
        });

        assertEquals("ok", result);
        assertNull(AiCallContext.get());
    }

    @Test
    @DisplayName("run 中抛异常时同样清理（否则线程池复用会串号）")
    void run_exceptionPath_clears() {
        assertThrows(IllegalStateException.class, () ->
                AiCallContext.run(1L, 2L, "QA_ASK", () -> {
                    throw new IllegalStateException("boom");
                }));
        assertNull(AiCallContext.get());
    }

    @Test
    @DisplayName("未进入作用域时 get() 为 null（= 不落库，不记错误归属）")
    void get_outsideScope_isNull() {
        assertNull(AiCallContext.get());
    }

    @Test
    @DisplayName("courseId 允许为 null：通识问答/作文批改不应被硬塞课程")
    void run_nullCourseId_allowed() {
        AiCallContext.run(9L, null, "ESSAY_CORRECT", () -> {
            assertEquals(9L, AiCallContext.get().getUserId());
            assertNull(AiCallContext.get().getCourseId());
            return null;
        });
    }
}
