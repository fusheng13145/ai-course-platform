package com.aicourse.service;

/**
 * 大模型微调（LoRA/PEFT）服务接口
 * 对接Python微调微服务（端口 8003）
 * <p>
 * 支持 LoRA 低秩适配微调，可在不更新全量参数的情况下
 * 定制课程专属的大模型回答风格和知识体系
 * <p>
 * Python端需实现的API契约见 docs/系统架构设计.md 的集成契约章节
 */
public interface LLMFineTuneService {

    /**
     * 健康检查
     * @return true表示Python微调服务可达
     */
    boolean healthCheck();

    /**
     * 启动 LoRA 微调任务
     * @param courseId 课程ID（用于数据集筛选）
     * @param baseModel 基座模型名称（如 deepseek-chat、qwen-7b）
     * @param config 微调超参：{"epochs":3, "learning_rate":5e-5, "lora_rank":8, "lora_alpha":16}
     * @return 任务信息JSON（含 task_id）
     */
    String startFineTune(Long courseId, String baseModel, String config);

    /**
     * 查询微调任务状态
     * @param taskId 任务ID
     * @return 任务状态JSON（pending/running/completed/failed，含进度百分比）
     */
    String getTaskStatus(String taskId);

    /**
     * 列出所有微调任务
     * @param courseId 课程ID（可选，为null则返回全部）
     * @return 任务列表JSON
     */
    String listTasks(Long courseId);

    /**
     * 取消微调任务
     * @param taskId 任务ID
     * @return 操作结果
     */
    String cancelTask(String taskId);

    /**
     * 使用微调后的模型进行推理
     * @param taskId 微调任务ID（对应微调后的模型版本）
     * @param prompt 输入提示词
     * @param params 推理参数：{"temperature":0.7, "max_tokens":2048}
     * @return 推理结果JSON
     */
    String inferWithFineTuned(String taskId, String prompt, String params);

    /**
     * 导出微调后的 LoRA 权重
     * @param taskId 任务ID
     * @return 下载URL或文件路径JSON
     */
    String exportWeights(String taskId);

    /**
     * 删除微调任务及模型
     * @param taskId 任务ID
     * @return 操作结果
     */
    String deleteTask(String taskId);
}