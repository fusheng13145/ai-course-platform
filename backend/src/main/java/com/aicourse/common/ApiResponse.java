package com.aicourse.common;

import java.time.LocalDateTime;

/**
 * 统一接口返回结果封装
 * @param <T> 响应数据泛型
 */
public class ApiResponse<T> {

    /** 响应码 */
    private int code;
    /** 响应描述信息 */
    private String message;
    /** 响应业务数据 */
    private T data;
    /** 时间戳（默认对象创建时赋值） */
    private LocalDateTime timestamp = LocalDateTime.now();

    /** 私有构造，禁止外部 new 创建 */
    private ApiResponse() {}

    // ===================== 成功响应 静态方法 =====================

    /**
     * 成功响应（默认提示 + 带数据）
     * @param data 业务数据
     * @return 统一返回对象
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = 200;
        response.message = "success";
        response.data = data;
        return response;
    }

    /**
     * 成功响应（自定义提示 + 带数据）
     * @param message 自定义提示信息
     * @param data 业务数据
     * @return 统一返回对象
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = 200;
        response.message = message;
        response.data = data;
        return response;
    }

    /**
     * 成功响应（无返回数据，仅状态）
     * @return 统一返回对象
     */
    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    /**
     * 成功响应（自定义提示，无数据）
     * @param message 自定义提示信息
     * @return 统一返回对象
     */
    public static <T> ApiResponse<T> success(String message) {
        return success(message, null);
    }

    // ===================== 失败响应 静态方法 =====================

    /**
     * 失败响应（错误码 + 错误信息，无数据）
     * @param code 错误码
     * @param message 错误描述
     * @return 统一返回对象
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.message = message;
        return response;
    }

    /**
     * 失败响应（错误码 + 错误信息 + 附带数据）
     * @param code 错误码
     * @param message 错误描述
     * @param data 附加数据
     * @return 统一返回对象
     */
    public static <T> ApiResponse<T> error(int code, String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.message = message;
        response.data = data;
        return response;
    }

    // ===================== Getter & Setter =====================
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}