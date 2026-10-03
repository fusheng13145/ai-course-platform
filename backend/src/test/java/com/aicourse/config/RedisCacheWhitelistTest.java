package com.aicourse.config;

import com.aicourse.entity.Course;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.Question;
import com.aicourse.entity.RolePermission;
import com.aicourse.vo.ExamPaperDetailVO;
import com.aicourse.vo.KnowledgeTreeNodeVO;
import com.aicourse.vo.ProctorThresholdVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Redis 缓存反序列化白名单护栏。
 *
 * <h2>为什么需要这个测试</h2>
 * {@link RedisObjectMapperFactory} 使用 {@code activateDefaultTyping(NON_FINAL)}，写缓存时会把
 * 运行时类型以 {@code "@class"} 写进 JSON；读缓存时该类型必须通过 {@code BasicPolymorphicTypeValidator}
 * 白名单校验，否则抛
 * <pre>
 *   SerializationException / InvalidTypeIdException:
 *   Could not resolve type id '...' ... PolymorphicTypeValidator denied resolution
 * </pre>
 * 表现为**「接口首次调用正常、之后每次调用 500」**（首次未命中缓存故正常，之后命中即崩）。
 *
 * <h2>该类问题已复发 3 次</h2>
 * <ol>
 *   <li>白名单缺 {@code com.aicourse.vo.} → {@code /exam-papers/{id}/detail} 等接口二次调用 500</li>
 *   <li>白名单缺 {@code java.math.} → 含 {@code BigDecimal} 的实体（Course.credits）读缓存失败</li>
 *   <li>白名单缺 MyBatis-Plus {@code Page} → {@code /questions/search} 二次调用 500（2026-09-21 修复）</li>
 * </ol>
 * 每次都是「新增一种缓存返回类型 → 忘了同步白名单」。本测试把这一步变成**编译期之后、上线之前的硬门禁**：
 * 任何人新增 {@code @Cacheable} 方法，只要返回类型不在白名单内，测试立刻失败并指出类型名。
 */
@DisplayName("Redis 缓存反序列化白名单护栏")
class RedisCacheWhitelistTest {

    private static final ObjectMapper MAPPER = RedisObjectMapperFactory.create();

    // ==================== 一、自动发现：所有 @Cacheable 返回类型必须过白名单 ====================

    @Test
    @DisplayName("所有 @Cacheable 方法的返回类型（含泛型参数）都能通过反序列化白名单")
    void everyCacheableReturnTypePassesWhitelist() throws Exception {
        Set<Class<?>> cachedTypes = discoverCacheableReturnTypes();

        assertThat(cachedTypes)
                .as("未能扫描到任何 @Cacheable 返回类型——扫描逻辑可能已失效，护栏形同虚设")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Class<?> type : cachedTypes) {
            if (isDeniedByWhitelist(type)) {
                violations.add(type.getName());
            }
        }

        assertThat(violations)
                .as("以下类型被用作缓存值但未加入 RedisObjectMapperFactory 白名单；"
                        + "症状为「首次调用正常、之后每次调用 500」。请在 RedisObjectMapperFactory.create() 的 "
                        + "BasicPolymorphicTypeValidator 补充相应 allowIfSubType(...)")
                .isEmpty();
    }

    @Test
    @DisplayName("反证：白名单确实会拒绝范围外类型（证明上面的探针不是假绿）")
    void whitelistActuallyRejectsOutOfScopeTypes() {
        assertThatThrownBy(() -> MAPPER.readValue("{\"@class\":\"java.io.File\",\"path\":\"x\"}", Object.class))
                .as("若此处不再抛错，说明 isDeniedByWhitelist 的判定依据已失效，自动发现测试将失去意义")
                .hasMessageContaining("denied resolution");
    }

    // ==================== 二、真实往返：历史三次故障的现场复现 ====================

    @Test
    @DisplayName("回归 #3：Page<Question> 往返不丢失类型与分页信息（题库搜索缓存的现场）")
    void pageOfQuestionSurvivesRoundTrip() throws Exception {
        Page<Question> page = new Page<>(1, 5);
        page.setRecords(List.of(singleChoice(6L), singleChoice(7L)));
        page.setTotal(2L);

        Object restored = roundTrip(page);

        assertThat(restored).isInstanceOf(Page.class);
        Page<?> p = (Page<?>) restored;
        assertThat(p.getTotal()).isEqualTo(2L);
        assertThat(p.getCurrent()).isEqualTo(1L);
        assertThat(p.getSize()).isEqualTo(5L);
        assertThat(p.getRecords()).hasSize(2);
        assertThat(p.getRecords().get(0)).isInstanceOf(Question.class);
        assertThat(((Question) p.getRecords().get(0)).getContent()).isNotBlank();
    }

    @Test
    @DisplayName("回归 #2：含 BigDecimal 与 LocalDateTime 的实体往返不丢精度/时间")
    void courseEntitySurvivesRoundTrip() throws Exception {
        Course course = new Course();
        course.setId(1L);
        course.setName("Python程序设计");
        course.setCredits(new BigDecimal("3.00"));
        course.setCreatedAt(LocalDateTime.of(2026, 9, 21, 14, 8, 51));

        Object restored = roundTrip(course);

        assertThat(restored).isInstanceOf(Course.class);
        Course c = (Course) restored;
        assertThat(c.getName()).isEqualTo("Python程序设计");
        assertThat(c.getCredits()).isEqualByComparingTo("3.00");
        assertThat(c.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 21, 14, 8, 51));
    }

    @Test
    @DisplayName("回归 #1：List 泛型集合往返后退化为具体实体而非 LinkedHashMap")
    void listOfEntitySurvivesRoundTripWithoutTypeErasure() throws Exception {
        Course course = new Course();
        course.setId(2L);
        course.setName("数据结构");
        // 用 ArrayList：真实缓存值来自 MyBatis 查询，确为 ArrayList。
        // 注意约束：Java 9+ 的 List.of() 返回 ImmutableCollections$List12，**不能作为缓存根值**
        // （反序列化时无法还原，抛 MismatchedInput）。嵌套在 Map 值里则可用（见下一条用例）。
        // 生产代码写入缓存时请使用可变集合。
        List<Course> list = new ArrayList<>();
        list.add(course);

        Object restored = roundTrip(list);

        assertThat(restored).isInstanceOf(List.class);
        List<?> l = (List<?>) restored;
        assertThat(l).hasSize(1);
        assertThat(l.get(0))
                .as("泛型擦除导致元素退化为 Map 时，上游强转 (Course) 会抛 ClassCastException")
                .isInstanceOf(Course.class);
        assertThat(((Course) l.get(0)).getName()).isEqualTo("数据结构");
    }

    @Test
    @DisplayName("VO 类型（vo 包）往返正常：ExamPaperDetailVO / KnowledgeTreeNodeVO / ProctorThresholdVO")
    void voTypesSurviveRoundTrip() throws Exception {
        ExamPaperDetailVO detail = new ExamPaperDetailVO();
        detail.setPaper(new ExamPaper());
        detail.setQuestionCount(5);
        assertThat(roundTrip(detail)).isInstanceOf(ExamPaperDetailVO.class);

        KnowledgeTreeNodeVO node = new KnowledgeTreeNodeVO();
        node.setId(1L);
        node.setName("数据结构基础");
        assertThat(roundTrip(node)).isInstanceOf(KnowledgeTreeNodeVO.class);

        assertThat(roundTrip(new ProctorThresholdVO())).isInstanceOf(ProctorThresholdVO.class);
    }

    @Test
    @DisplayName("实体类型往返正常：KnowledgePoint / RolePermission")
    void entityTypesSurviveRoundTrip() throws Exception {
        KnowledgePoint point = new KnowledgePoint();
        point.setName("时间复杂度");
        assertThat(roundTrip(point)).isInstanceOf(KnowledgePoint.class);

        RolePermission perm = new RolePermission();
        assertThat(roundTrip(perm)).isInstanceOf(RolePermission.class);
    }

    @Test
    @DisplayName("Map<String,Object> 缓存值（analytics / permissions 场景）中的嵌套实体可还原")
    void mapCachedValuesSurviveRoundTrip() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", 12L);
        payload.put("name", "平台概览");
        payload.put("ratio", new BigDecimal("0.85"));
        payload.put("nested", new LinkedHashMap<>(Map.of("count", 3L)));
        payload.put("courses", List.of(new Course()));
        payload.put("createdAt", LocalDateTime.of(2026, 9, 21, 0, 0));

        Object restored = roundTrip(payload);

        assertThat(restored).isInstanceOf(Map.class);
        Map<?, ?> m = (Map<?, ?>) restored;
        assertThat(m.get("total")).isEqualTo(12L);
        assertThat(m.get("ratio")).isInstanceOf(BigDecimal.class);
        assertThat(m.get("nested")).isInstanceOf(Map.class);
        assertThat((List<?>) m.get("courses")).first().isInstanceOf(Course.class);
        assertThat(m.get("createdAt")).isInstanceOf(LocalDateTime.class);
    }

    // ==================== 内部工具 ====================

    /** 扫描所有带 @Service / @Component 的类，收集其 @Cacheable 方法的返回类型（含泛型实参）。 */
    private Set<Class<?>> discoverCacheableReturnTypes() {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        provider.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        Set<Class<?>> types = new LinkedHashSet<>();
        for (BeanDefinition bd : provider.findCandidateComponents("com.aicourse")) {
            String className = bd.getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> clazz;
            try {
                clazz = Class.forName(className, false, getClass().getClassLoader());
            } catch (Throwable ignored) {
                continue;
            }
            if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getAnnotation(Cacheable.class) == null) {
                    continue;
                }
                collectRawTypes(method.getGenericReturnType(), types);
            }
        }
        return types;
    }

    /** 递归展开泛型，收集所有涉及的原始类型（如 Page&lt;Question&gt; → Page + Question）。 */
    private void collectRawTypes(Type type, Set<Class<?>> out) {
        if (type instanceof Class<?> c) {
            if (c.isArray()) {
                collectRawTypes(c.getComponentType(), out);
            } else if (c != Object.class && c != void.class && !c.isPrimitive()) {
                out.add(c);
            }
        } else if (type instanceof ParameterizedType pt) {
            collectRawTypes(pt.getRawType(), out);
            for (Type arg : pt.getActualTypeArguments()) {
                collectRawTypes(arg, out);
            }
        } else if (type instanceof WildcardType wt) {
            for (Type bound : wt.getUpperBounds()) {
                collectRawTypes(bound, out);
            }
        } else if (type instanceof TypeVariable<?> tv) {
            for (Type bound : tv.getBounds()) {
                collectRawTypes(bound, out);
            }
        }
    }

    /**
     * 判定某类型是否会被白名单拒绝。
     *
     * <p>做法：构造只含 {@code "@class"} 的最小 JSON 探针。若类型被白名单拒绝，Jackson 在**类型解析阶段**
     * 即抛 {@code InvalidTypeIdException(... PolymorphicTypeValidator denied resolution)}；
     * 若类型被放行，则可能因缺少字段/构造而抛其他异常，或直接实例化成功——这两种都说明「未被拒绝」。
     */
    private boolean isDeniedByWhitelist(Class<?> type) {
        String probe = "{\"@class\":\"" + type.getName() + "\"}";
        try {
            MAPPER.readValue(probe, Object.class);
            return false;
        } catch (Exception e) {
            return String.valueOf(e.getMessage()).contains("denied resolution");
        }
    }

    private static Object roundTrip(Object value) throws Exception {
        String json = MAPPER.writeValueAsString(value);
        return MAPPER.readValue(json, Object.class);
    }

    private static Question singleChoice(Long id) {
        Question q = new Question();
        q.setId(id);
        q.setType("SINGLE_CHOICE");
        q.setContent("下列哪种数据结构遵循先进先出(FIFO)原则？");
        q.setOptions("[\"栈\",\"队列\",\"二叉树\",\"图\"]");
        q.setCorrectAnswers("[\"1\"]");
        q.setDifficulty("EASY");
        q.setScore(new BigDecimal("5.00"));
        q.setCourseId(1L);
        q.setIsActive(true);
        return q;
    }
}
