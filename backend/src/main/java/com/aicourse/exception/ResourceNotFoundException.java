package com.aicourse.exception;

/**
 * 资源不存在异常
 * 用于查询数据、文件、接口资源不存在时抛出，统一返回 404
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * 包装原始异常构造
     * @param message 提示信息
     * @param cause 原始异常
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}