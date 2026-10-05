package com.aicourse.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;

/**
 * 成绩方案 components 字段的线格式桥接：库内与实体保持 JSON 文本，HTTP 出入参保持对象数组。
 *
 * <p>前端（CgPolicyDialog 表单、CgReportTable 权重列）始终按数组读写该字段，而实体声明为 String，
 * 直接绑定会让保存请求落在 HttpMessageNotReadableException（400）、读取后 .map/v-for 拿到字符串。
 * 转换放在 Jackson 层，是为了让 DB 列类型、MyBatis 映射与 computeFinal 的解析逻辑都无需改动。</p>
 */
public final class GradeComponentsJson {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private GradeComponentsJson() {
    }

    /** 入站：数组/对象 → 其 JSON 文本；字符串（历史调用方已自行序列化）→ 原样保留。 */
    public static class Deserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                return node.asText();
            }
            return node.toString();
        }
    }

    /** 出站：JSON 文本 → 数组节点；空值或不可解析的历史脏数据降级为空数组，避免前端渲染报错。 */
    public static class Serializer extends JsonSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeTree(parseToArray(value));
        }
    }

    /** 未配置方案（null）同样出空数组，让客户端只需处理「数组」这一种形状。 */
    public static class NullSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartArray();
            gen.writeEndArray();
        }
    }

    /** 校验/解析共用：把存储文本转成数组节点，非法或非数组时返回空数组。 */
    public static ArrayNode parseToArray(String value) {
        if (value == null || value.isBlank()) {
            return MAPPER.createArrayNode();
        }
        try {
            JsonNode node = MAPPER.readTree(value);
            return node.isArray() ? (ArrayNode) node : MAPPER.createArrayNode();
        } catch (Exception e) {
            return MAPPER.createArrayNode();
        }
    }
}
