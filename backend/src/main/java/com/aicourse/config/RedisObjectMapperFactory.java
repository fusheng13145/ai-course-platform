package com.aicourse.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
                // 修复（2026-09-14，P0）：白名单原先只有 entity 包，缺失 vo 包。
                // 由于开启的是 NON_FINAL 默认类型，VO 在**写入**缓存时会带上
                // "@class":"com.aicourse.vo.XxxVO"，而**读取**时被 PTV 拒绝，抛
                //   SerializationException: Could not resolve type id
                //   'com.aicourse.vo.ExamPaperDetailVO' ... PolymorphicTypeValidator denied resolution
                // 表现为：这类接口「首次调用正常、之后每次调用 500」——影响
                //   GET /exam-papers/{id}/detail（ExamPaperDetailVO）
                //   GET /knowledge/points/tree/{courseId}（List<KnowledgeTreeNodeVO>）
                //   GET /proctor/thresholds（ProctorThresholdVO）
                // 现补入 vo 包，仍保持「项目自有类型白名单」而非 LaissezFaire，攻击面不扩大。
                .allowIfSubType("com.aicourse.vo.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.time.")
                // 修复：实体含 BigDecimal 字段（如 Course.credits / Course.price，对应
                // DECIMAL(5,2) / DECIMAL(10,2)）。BigDecimal 为非 final 类型，
                // NON_FINAL 默认类型策略会写入 "@class":"java.math.BigDecimal"，
                // 但白名单原先未包含 java.math.，反序列化时
                // PolymorphicTypeValidator 拒绝解析该 type id，导致
                // "Could not resolve type id 'java.math.BigDecimal'" ——
                // 任何从缓存读取含 BigDecimal 实体的操作都会失败。
                .allowIfSubType("java.math.")
                // 修复（2026-09-21）：白名单缺 MyBatis-Plus 分页对象 Page。
                // QuestionServiceImpl.searchQuestions 标了 @Cacheable("questionSearch")，
                // 缓存值是 Page<Question>，NON_FINAL 策略写入
                //   "@class":"com.baomidou.mybatisplus.extension.plugins.pagination.Page"
                // 读取时被 PTV 拒绝 → 症状：GET /questions/search「首次调用 200、
                // 之后同一查询必 500」。实测 teacher / student / admin 三角色均复现
                // （miss=200 / hit=500），Redis 中确认缓存值带 @class=...pagination.Page。
                // 补入 MyBatis-Plus 数据容器类型；仍为窄白名单，不扩大攻击面。
                .allowIfSubType("com.baomidou.mybatisplus.extension.plugins.pagination.Page")
                .allowIfSubType("com.baomidou.mybatisplus.core.metadata.OrderItem")
                .build();
        // 修复（2026-09-21）：Page 含只读派生属性 getPages()（无 setter、无对应字段），
        // 序列化时会写出 "pages":N，反序列化时 Jackson 默认（FAIL_ON_UNKNOWN_PROPERTIES
        // =true）会抛 UnrecognizedPropertyException，同样表现为「命中缓存即 500」。
        // 缓存 JSON 由本服务自身写入，字段增删不应导致读取崩溃，故对缓存反序列化放宽该检查。
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        mapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false);
        return mapper;
    }
}
