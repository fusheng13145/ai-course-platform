package com.aicourse.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 构造用于 Redis 序列化的共享 ObjectMapper。
 *
 * 关键修复（两层）：
 *
 * 1) 必须注册 JavaTimeModule。
 *    原先 GenericJackson2JsonRedisSerializer 使用裸 ObjectMapper，不认识
 *    java.time.* 类型（如 Course 的 startDate / createdAt 等 LocalDateTime 字段），
 *    缓存 List<Course> 时抛
 *    "Cannot serialize java.time.LocalDateTime; no serializer found"，
 *    被 AnalyticsService 的 try-catch 吞掉，导致仪表盘图表数据被清空。
 *
 * 2) 必须显式开启默认类型信息（activateDefaultTyping）。
 *    经实测，本版本 GenericJackson2JsonRedisSerializer(ObjectMapper) 构造器
 *    【不会】自动添加默认类型信息。若不开，List<Course> 这类泛型集合在反序列化时
 *    因运行期泛型擦除而退化为 List<LinkedHashMap>，上游代码对元素做强转
 *    (Course) 时会抛 ClassCastException（同样被 try-catch 清空图表）。
 *    开启 NON_FINAL 后，ArrayList / Course 等非 final 类型会写入 @class，
 *    反序列化即可还原为真实实体类型；String / Long 等 final 类型则不写 @class，
 *    兼顾正确性与开销。
 */
public final class RedisObjectMapperFactory {

    private RedisObjectMapperFactory() {
    }

    public static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();
        // 支持 java.time.LocalDateTime / LocalDate / LocalTime 等类型
        mapper.registerModule(new JavaTimeModule());
        // 以 ISO-8601 字符串形式写入日期，避免时间戳语义歧义
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 某些实体若暂时无可序列化字段也不要直接抛异常
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // 不序列化 null 字段，减小缓存体积
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // 关键：开启默认类型信息，使 List<Course> 等泛型集合可正确还原为具体实体类型。
        // 修复 P1-7（CWE-502）：原实现使用 LaissezFaireSubTypeValidator（全类型放行），
        // 一旦攻击者可影响 Redis 缓存内容，即可经 @class 触发危险 gadget 反序列化。
        // 现改为白名单校验：仅允许 com.aicourse.entity 包实体（本项目缓存的唯一类型来源），
        // 业务不受影响，攻击面收敛到业务实体范围。
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.aicourse.entity.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.time.")
                .build();
        mapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        mapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false);
        return mapper;
    }
}
