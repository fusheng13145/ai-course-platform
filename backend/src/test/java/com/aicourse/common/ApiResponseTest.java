package com.aicourse.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ApiResponse 单元测试：覆盖全部静态工厂方法、getter/setter、timestamp。
 */
class ApiResponseTest {

    @Test
    void success_withData() {
        // 注意：success(T data) 与 success(String message) 对 String 参数存在重载歧义，
        // 此处用 Integer 数据明确走 success(T data) 默认 message 分支，验证 message=success、data=data
        ApiResponse<Integer> r = ApiResponse.success(1);
        assertEquals(200, r.getCode());
        assertEquals("success", r.getMessage());
        assertEquals(1, r.getData());
        assertNotNull(r.getTimestamp());
    }

    @Test
    void success_messageAndData() {
        ApiResponse<String> r = ApiResponse.success("ok", "payload");
        assertEquals("ok", r.getMessage());
        assertEquals("payload", r.getData());
    }

    @Test
    void success_noArgs() {
        ApiResponse<String> r = ApiResponse.success();
        assertEquals(200, r.getCode());
        assertNull(r.getData());
    }

    @Test
    void success_messageOnly() {
        ApiResponse<String> r = ApiResponse.success("only-msg");
        assertEquals("only-msg", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    void error_codeAndMessage() {
        ApiResponse<String> r = ApiResponse.error(400, "bad");
        assertEquals(400, r.getCode());
        assertEquals("bad", r.getMessage());
    }

    @Test
    void error_withData() {
        ApiResponse<String> r = ApiResponse.error(500, "err", "detail");
        assertEquals(500, r.getCode());
        assertEquals("detail", r.getData());
    }

    @Test
    void setters() {
        ApiResponse<Integer> r = ApiResponse.success(1);
        r.setCode(201);
        r.setMessage("m");
        r.setData(2);
        r.setTimestamp(LocalDateTime.of(2020, 1, 1, 0, 0));
        assertEquals(201, r.getCode());
        assertEquals("m", r.getMessage());
        assertEquals(2, r.getData());
        assertEquals(LocalDateTime.of(2020, 1, 1, 0, 0), r.getTimestamp());
    }
}
