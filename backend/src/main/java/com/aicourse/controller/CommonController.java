package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 公共通用接口控制器
 */
@RestController
@RequestMapping("/common")
public class CommonController {

    private static final String UPLOAD_DIR = "uploads/";

    /**
     * 生成随机图形验证码
     * @return 验证码字符串
     */
    @GetMapping("/captcha")
    public ApiResponse<Map<String, String>> captcha() {
        String code = String.valueOf((int) (Math.random() * 9000 + 1000));
        return ApiResponse.success(Map.of("code", code));
    }

    /**
     * 发送验证码
     * @param body 请求参数
     * @return 操作结果
     */
    @PostMapping("/verify-code")
    public ApiResponse<Void> sendVerifyCode(@RequestBody Map<String, String> body) {
        return ApiResponse.success("验证码发送成功", null);
    }

    /**
     * 验证码发送备用接口
     * @param body 请求参数
     * @return 操作结果
     */
    @PostMapping("/verification-code")
    public ApiResponse<Void> sendVerificationCode(@RequestBody Map<String, String> body) {
        return sendVerifyCode(body);
    }

    /**
     * 文件上传
     * @param file 上传文件
     * @return 文件访问地址、原名、大小
     */
    @PostMapping("/upload")
    public ApiResponse<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        // 1. 校验文件是否为空
        if (file.isEmpty()) {
            return ApiResponse.error(400, "上传文件不能为空");
        }

        // 2. 创建上传目录并校验结果
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                return ApiResponse.error(500, "上传目录创建失败");
            }
        }

        // 3. 处理文件名与后缀，处理 null 情况
        String originalName = file.getOriginalFilename();
        String safeOriginalName = originalName == null ? "未命名文件" : originalName;

        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }

        String fileName = UUID.randomUUID() + ext;
        Path filePath = Paths.get(UPLOAD_DIR + fileName);

        try {
            // 4. 写入文件
            Files.write(filePath, file.getBytes());

            // 5. 返回结果
            return ApiResponse.success(Map.of(
                    "url", "/uploads/" + fileName,
                    "fileName", safeOriginalName,
                    "size", String.valueOf(file.getSize())
            ));
        } catch (IOException e) {
            return ApiResponse.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取功能路线图
     * @return 版本及功能进度信息
     */
    @GetMapping("/feature-roadmap")
    public ApiResponse<Map<String, Object>> featureRoadmap() {
        List<Map<String, String>> features = List.of(
                Map.of("name", "课程管理", "status", "done"),
                Map.of("name", "班级管理", "status", "done"),
                Map.of("name", "用户管理", "status", "done"),
                Map.of("name", "AI课程", "status", "partial"),
                Map.of("name", "AI助手", "status", "partial")
        );
        return ApiResponse.success(Map.of(
                "version", "1.0.0",
                "features", features
        ));
    }
}