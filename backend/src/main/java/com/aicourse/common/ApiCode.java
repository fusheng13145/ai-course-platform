package com.aicourse.common;

/**
 * API 响应状态码常量，与 {@code GlobalExceptionHandler} 中使用的 {@code HttpStatus} 枚举保持一致。
 *
 * <p>用于消除控制器 / 服务中散落的状态码魔法数字（如 {@code ApiResponse.error(ApiCode.BAD_REQUEST, ...)}、
 * {@code new BusinessException(ApiCode.UNAUTHORIZED, ...)}），统一为语义化常量，降低前后端状态码不一致风险。</p>
 */
public final class ApiCode {

    public static final int OK = 200;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    /** 请求方法不支持（对应 HTTP 405），用于错误方法调用端点等场景。 */
    public static final int METHOD_NOT_ALLOWED = 405;
    /** 触发限流 / 配额上限（对应 HTTP 429），用于 AI 用量配额熔断等场景。 */
    public static final int TOO_MANY_REQUESTS = 429;
    public static final int INTERNAL_SERVER_ERROR = 500;

    private ApiCode() {
    }
}
