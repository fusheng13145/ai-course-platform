package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 统一错误响应体DTO
 * 当API请求失败时返回的标准错误信息格式
 */
@Schema(description = "统一错误响应体")
public class ApiErrorResponse {

    @Schema(description = "错误码", example = "400")
    private int code;

    @Schema(description = "错误信息", example = "参数校验失败")
    private String message;

    @Schema(description = "错误详情/描述", example = "用户名不能为空")
    private String detail;

    @Schema(description = "错误发生的时间戳")
    private LocalDateTime timestamp = LocalDateTime.now();

    @Schema(description = "请求路径", example = "/api/v1/users/register")
    private String path;

    // ====================== 构造方法 ======================

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public ApiErrorResponse(int code, String message, String detail, String path) {
        this.code = code;
        this.message = message;
        this.detail = detail;
        this.path = path;
    }

    // ====================== 静态工厂方法 ======================

    /**
     * 创建400错误响应（请求参数错误）
     */
    public static ApiErrorResponse badRequest(String message) {
        return new ApiErrorResponse(400, message);
    }

    /**
     * 创建401错误响应（未认证）
     */
    public static ApiErrorResponse unauthorized(String message) {
        return new ApiErrorResponse(401, message);
    }

    /**
     * 创建403错误响应（无权限）
     */
    public static ApiErrorResponse forbidden(String message) {
        return new ApiErrorResponse(403, message);
    }

    /**
     * 创建404错误响应（资源不存在）
     */
    public static ApiErrorResponse notFound(String message) {
        return new ApiErrorResponse(404, message);
    }

    /**
     * 创建500错误响应（服务器内部错误）
     */
    public static ApiErrorResponse internalError(String message) {
        return new ApiErrorResponse(500, message);
    }

    // ====================== Getter & Setter ======================

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

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
