package com.aicourse.llm;

import java.util.List;
import java.util.Map;

/**
 * 大语言模型（LLM）供应商抽象
 * <p>
 * 将底层模型调用从 {@code AIService} 中解耦，使系统可在 DeepSeek / OpenAI / 通义千问(Qwen)
 * 等供应商之间自由切换，而无需改动业务代码。新增模型只需新增一个 {@link LLMProvider} 实现
 * 并在配置 {@code ai.provider} 中登记即可。
 */
public interface LLMProvider {

    /**
     * 执行一次对话补全（无结构化约束）
     *
     * @param messages     角色-内容消息列表（role/content）
     * @param temperature  采样温度，可空（使用供应商默认）
     * @return 模型返回的文本
     */
    String complete(List<Map<String, String>> messages, Double temperature);

    /**
     * 执行一次对话补全，并要求模型以 JSON 对象形式返回（用于结构化输出）
     *
     * @param messages     角色-内容消息列表（role/content）
     * @param temperature  采样温度，可空
     * @param jsonSchema   可选的 JSON Schema 描述（作为 system 提示的一部分，引导模型输出）
     * @return 模型返回的 JSON 文本
     */
    String completeJson(List<Map<String, String>> messages, Double temperature, String jsonSchema);

    /**
     * 执行一次流式对话补全（SSE 逐 token 推送）
     * <p>
     * 与 {@link #complete} 不同，本方法以 SSE（Server-Sent Events）方式流式返回，
     * 每个 Flux 事件为一个 content delta 片段，前端可实时渲染打字机效果。
     * 实现类需在请求体中设置 {@code "stream": true} 并解析 SSE {@code data:} 行。
     *
     * @param messages     角色-内容消息列表（role/content）
     * @param temperature  采样温度，可空
     * @return 流式内容增量（由 reactor.core.publisher.Flux 封装）
     */
    reactor.core.publisher.Flux<String> stream(List<Map<String, String>> messages, Double temperature);

    /**
     * 供应商标识（如 deepseek / openai / qwen）
     */
    String name();

    /**
     * 是否已正确配置（如 API Key 非空）。未配置时业务层应返回清晰的提示而非伪造内容。
     */
    boolean isConfigured();
}
