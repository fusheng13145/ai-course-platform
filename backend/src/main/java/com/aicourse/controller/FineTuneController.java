package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.service.LLMFineTuneService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型微调（LoRA/PEFT）管理接口（管理员）
 * <p>
 * 对外暴露 Python 微调微服务（端口 8003）的编排入口：健康检查 / 启动任务 / 状态查询 / 列表 / 取消 / 推理 / 权重导出。
 * 降级语义：{@code fine-tune.enabled=false} 或 Python 服务不可达时返回明确不可用信息（HTTP 503 语义），不抛 500。
 * 路径受 SecurityConfig {@code /admin/** hasRole(ADMIN)} 保护。
 * 对接契约见 docs/Python接口契约-微调服务.md
 */
@RestController
@RequestMapping("/admin/fine-tune")
@Tag(name = "模型微调（LoRA）", description = "LoRA 微调任务编排（对接 Python 微调服务 8003）")
public class FineTuneController {

    private static final Logger log = LoggerFactory.getLogger(FineTuneController.class);

    private final LLMFineTuneService fineTuneService;

    public FineTuneController(LLMFineTuneService fineTuneService) {
        this.fineTuneService = fineTuneService;
    }

    /** 微调服务不可用时的统一错误响应（泛型方法：兼容各端点返回类型） */
    private <T> ApiResponse<T> unavailable(String msg) {
        return ApiResponse.error(ApiCode.SERVICE_UNAVAILABLE, msg);
    }

    /** 检查 Python 微调微服务是否可达 */
    @GetMapping("/health")
    @Operation(summary = "微调服务健康检查")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean available = fineTuneService.healthCheck();
        m.put("available", available);
        return ApiResponse.success(available ? "微调服务可达" : "微调服务不可达或未启用", m);
    }

    /** 提交一个新的 LoRA 微调任务（需微调服务可用） */
    @PostMapping("/tasks")
    @Operation(summary = "启动 LoRA 微调任务")
    public ApiResponse<String> start(@RequestParam Long courseId,
                                     @RequestParam(defaultValue = "qwen-7b") String baseModel,
                                     @RequestBody(required = false) JsonNode config) {
        if (!fineTuneService.healthCheck()) {
            return unavailable("微调服务未启用或不可达，无法启动任务");
        }
        String configJson = config != null ? config.toString() : "{}";
        return ApiResponse.success("微调任务已提交", fineTuneService.startFineTune(courseId, baseModel, configJson));
    }

    /** 查询单个微调任务的最新状态 */
    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查询微调任务状态")
    public ApiResponse<String> status(@PathVariable String taskId) {
        if (!fineTuneService.healthCheck()) {
            return unavailable("微调服务未启用或不可达");
        }
        return ApiResponse.success("查询成功", fineTuneService.getTaskStatus(taskId));
    }

    /** 列出微调任务（可按课程筛选） */
    @GetMapping("/tasks")
    @Operation(summary = "微调任务列表")
    public ApiResponse<String> list(@RequestParam(required = false) Long courseId) {
        if (!fineTuneService.healthCheck()) {
            return unavailable("微调服务未启用或不可达");
        }
        return ApiResponse.success("查询成功", fineTuneService.listTasks(courseId));
    }

    /** 取消一个正在运行的微调任务 */
    @PostMapping("/tasks/{taskId}/cancel")
    @Operation(summary = "取消微调任务")
    public ApiResponse<String> cancel(@PathVariable String taskId) {
        if (!fineTuneService.healthCheck()) {
            return unavailable("微调服务未启用或不可达");
        }
        return ApiResponse.success("已提交取消", fineTuneService.cancelTask(taskId));
    }

    /** 使用微调后的模型对 prompt 进行推理 */
    @PostMapping("/tasks/{taskId}/infer")
    @Operation(summary = "使用微调模型推理")
    public ApiResponse<String> infer(@PathVariable String taskId,
                                     @RequestParam String prompt,
                                     @RequestBody(required = false) JsonNode params) {
        if (!fineTuneService.healthCheck()) {
            return unavailable("微调服务未启用或不可达");
        }
        String paramsJson = params != null ? params.toString() : "{\"temperature\":0.7}";
        return ApiResponse.success("推理完成", fineTuneService.inferWithFineTuned(taskId, prompt, paramsJson));
    }

    /** 导出指定微调任务的 LoRA 权重并生成下载链接 */
    @GetMapping("/tasks/{taskId}/export")
    @Operation(summary = "导出 LoRA 权重")
    public ApiResponse<String> export(@PathVariable String taskId) {
        if (!fineTuneService.healthCheck()) {
            return unavailable("微调服务未启用或不可达");
        }
        return ApiResponse.success("导出链接已生成", fineTuneService.exportWeights(taskId));
    }
}
