package com.aicourse.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Prompt 注入护栏：对送入 LLM 的用户输入做「结构化控制符中和」，对 LLM 输出做「活动内容清洗」。
 *
 * <p><b>定位——与角色分离形成纵深：</b>本项目已把用户内容放入 {@code user} 角色、指令放入
 * {@code system} 角色（结构化分离是抵御注入的第一防线）。但攻击者仍可能在用户内容里塞入
 * 伪造的对话模板控制符（如 ChatML 的 {@code <|im_start|>system}）或「忽略以上指令」类越狱语句，
 * 试图从 {@code user} 角色中「越狱」为系统指令。本护栏负责：</p>
 *
 * <ol>
 *   <li><b>输入侧 {@link #neutralizeInjection(String)}：</b>移除/中和常见对话模板控制符与角色标记，
 *       使其无法被下游模型解释为角色切换；保留正常学术文本，避免误伤合法内容。</li>
 *   <li><b>输出侧 {@link #sanitizeOutput(String)}：</b>剥离脚本、iframe、事件处理器、javascript: 伪协议等
 *       活动内容，防止 LLM 输出（可能源自被污染的检索语料）在前端被当作 HTML 执行造成存储型 XSS。</li>
 * </ol>
 *
 * <p>设计原则：<b>中和而非拒绝</b>。直接拒绝含关键词的输入既伤害正常教学场景（学生本就可能讨论
 * 「系统提示词」这类话题），又易被同义改写绕过；结构化控制符则可安全移除而不损伤语义。</p>
 */
@Component
public class PromptGuard {

    private static final Logger log = LoggerFactory.getLogger(PromptGuard.class);

    /** ChatML / 指令模板等特殊控制 token（跨供应商常见形态）。 */
    private static final Pattern CONTROL_TOKENS = Pattern.compile(
            "<\\|.*?\\|>" +                       // <|im_start|> / <|im_end|> / <|system|> 等
            "|\\[/?INST\\]" +                      // [INST] [/INST]
            "|\\[/?SYS\\]" +                       // [SYS] [/SYS]
            "|<</?SYS>>" +                          // <<SYS>> <</SYS>>
            "|</?s>",                               // </s> <s>
            Pattern.CASE_INSENSITIVE);

    /** 行首伪造的角色标记（system:/assistant:/developer:），仅在行首匹配以尽量减少误伤正文。 */
    private static final Pattern ROLE_PREFIX = Pattern.compile(
            "(?im)^[ \\t]*(system|assistant|developer)[ \\t]*:");

    // ---------- 输出清洗：活动内容向量 ----------
    private static final Pattern SCRIPT_TAG = Pattern.compile(
            "(?is)<\\s*script.*?>.*?<\\s*/\\s*script\\s*>");
    private static final Pattern DANGEROUS_TAG = Pattern.compile(
            "(?is)<\\s*/?\\s*(iframe|object|embed|link|meta|style|base)[^>]*>");
    private static final Pattern EVENT_HANDLER = Pattern.compile(
            "(?i)\\son\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JS_URI = Pattern.compile(
            "(?i)javascript\\s*:");

    /**
     * 中和用户输入中的对话模板控制符与伪角色标记；保留正常文本。
     *
     * @param userInput 原始用户输入（可为 null）
     * @return 中和后的安全文本；null 输入返回空串
     */
    public String neutralizeInjection(String userInput) {
        if (userInput == null) {
            return "";
        }
        String cleaned = CONTROL_TOKENS.matcher(userInput).replaceAll(" ");
        // 行首伪角色标记：在冒号前插入零宽分隔，破坏「role:」结构又不丢失可读文本
        cleaned = ROLE_PREFIX.matcher(cleaned).replaceAll(m -> m.group(1) + "\u200b:");
        return cleaned;
    }

    /**
     * 清洗 LLM 输出中的活动内容，防止在前端渲染时执行。
     *
     * @param output LLM 原始输出（可为 null）
     * @return 清洗后的文本；null 输入原样返回 null（交由上层判空）
     */
    public String sanitizeOutput(String output) {
        if (output == null) {
            return null;
        }
        String cleaned = SCRIPT_TAG.matcher(output).replaceAll("");
        cleaned = DANGEROUS_TAG.matcher(cleaned).replaceAll("");
        cleaned = EVENT_HANDLER.matcher(cleaned).replaceAll("");
        cleaned = JS_URI.matcher(cleaned).replaceAll("");
        return cleaned;
    }
}
