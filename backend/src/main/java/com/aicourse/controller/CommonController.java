package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/common")
public class CommonController {
    @GetMapping("/captcha")
    public ApiResponse<Map<String, String>> captcha() {
        String code = String.valueOf((int) (Math.random() * 9000 + 1000));
        return ApiResponse.success(Map.of("code", code));
    }

    @PostMapping("/verify-code")
    public ApiResponse<Void> sendVerifyCode(@RequestBody Map<String, String> body) {
        return ApiResponse.success("验证码已发送（模拟）", null);
    }

    @GetMapping("/feature-roadmap")
    public ApiResponse<Map<String, Object>> featureRoadmap() {
        return ApiResponse.success(Map.of(
            "version", "1.0.0",
            "features", java.util.List.of(
                Map.of("name", "用户管理", "status", "done"),
                Map.of("name", "课程管理", "status", "done"),
                Map.of("name", "在线考试", "status", "done"),
                Map.of("name", "AI助手", "status", "partial"),
                Map.of("name", "知识图谱", "status", "partial")
            )));
    }
}
