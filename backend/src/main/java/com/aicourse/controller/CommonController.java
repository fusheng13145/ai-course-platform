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
        return ApiResponse.success("验证码发送成功", null);
    }

    @GetMapping("/feature-roadmap")
    public ApiResponse<Map<String, Object>> featureRoadmap() {
        return ApiResponse.success(Map.of(
            "version", "1.0.0",
            "features", java.util.List.of(
                Map.of("name", "课程管理", "status", "done"),
                Map.of("name", "班级管理", "status", "done"),
                Map.of("name", "用户管理", "status", "done"),
                Map.of("name", "AI课程", "status", "partial"),
                Map.of("name", "AI助手", "status", "partial")
            )));
    }
}
