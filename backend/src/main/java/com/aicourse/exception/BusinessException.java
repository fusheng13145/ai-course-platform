package com.aicourse.exception;

/**
 * 自定义业务异常
 * 用于业务逻辑主动抛出异常，携带自定义错误码与提示信息
 */
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    /**
     * 构造方法，默认错误码 400
     * @param message 异常提示信息
     */
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    /**
     * 构造方法，自定义错误码与提示信息
     * @param code 错误码
     * @param message 异常提示信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造方法，包装原始异常，便于日志追踪堆栈
     * @param code 错误码
     * @param message 异常提示信息
     * @param cause 原始异常对象
     */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}