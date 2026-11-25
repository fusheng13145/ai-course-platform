package com.aicourse.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;

/**
 * 下游 Python 引擎响应体的 JSON 归一化工具（阶段3 P0 冗余收敛）。
 *
 * <p>Python 侧接口在异常时会返回纯文本而非 JSON，Controller 必须保证"永不为格式问题抛 500"，
 * 因此统一约定：解析成功返回结构节点，解析失败回退为文本节点，入参为 null 时原样返回 null。</p>
 *
 * <p>收敛前该逻辑在 {@code CourseKnowledgeBaseController} / {@code DeepGraphRagController} /
 * {@code RAGController} 各存一份（仅日志前缀不同），是阶段2 重复造车 P0 的第 1 组。</p>
 */
@Slf4j
public final class JsonNodeSupport {

    private JsonNodeSupport() {
    }

    /**
     * 解析 JSON 文本，失败时回退为文本节点。
     *
     * @param raw    下游返回的原始文本，允许为 {@code null}
     * @param mapper 调用方容器内的 ObjectMapper（沿用其既有配置，避免另造实例）
     * @param scene  场景标识，仅用于日志定位
     * @return 解析后的节点；{@code raw} 为 null 时返回 null
     */
    public static JsonNode parseOrText(String raw, ObjectMapper mapper, String scene) {
        if (raw == null) {
            return null;
        }
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            log.warn("{} 响应解析为JSON失败，已回退为文本节点: {}", scene, e.getMessage());
            return TextNode.valueOf(raw);
        }
    }
}
