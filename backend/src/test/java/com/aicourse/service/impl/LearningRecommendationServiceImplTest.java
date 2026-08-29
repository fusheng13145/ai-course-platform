package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.service.AIService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LearningRecommendationServiceImpl 单元测试（B 主战场新文件类，缺口 252）。
 * 非 ServiceImpl：构造器注入 5 个协作服务（全部 mock）；
 * 仅在源内构造 LambdaQueryWrapper<CourseEnrollment> / <KnowledgePoint> → 注册两者 TableInfo；
 * 多数据源失败均有 try/catch 降级，逐一触发异常以覆盖降级分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearningRecommendationServiceImplTest {

    @Mock private CourseEnrollmentService courseEnrollmentService;
    @Mock private CourseService courseService;
    @Mock private KnowledgePointService knowledgePointService;
    @Mock private AnalyticsService analyticsService;
    @Mock private AIService aiService;

    private LearningRecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CourseEnrollment.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), KnowledgePoint.class);

        service = new LearningRecommendationServiceImpl(courseEnrollmentService, courseService,
                knowledgePointService, analyticsService, aiService);

        CourseEnrollment en = new CourseEnrollment();
        en.setStudentId(1L);
        en.setCourseId(10L);
        en.setIsActive(true);
        en.setProgress(new BigDecimal("50"));

        Course course = new Course();
        course.setId(10L);
        course.setName("高等数学");

        KnowledgePoint kp = new KnowledgePoint();
        kp.setId(1L);
        kp.setCourseId(10L);
        kp.setName("函数");

        when(courseEnrollmentService.list(any(Wrapper.class))).thenReturn(List.of(en));
        when(courseService.listByIds(anyCollection())).thenReturn(List.of(course));
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of(kp));
        when(knowledgePointService.calculateMastery(anyLong(), anyLong())).thenReturn(new BigDecimal("60"));
        when(analyticsService.getUserDashboard(anyLong())).thenReturn(Map.of("avgScore", "85"));
        when(aiService.recommendLearningWithContext(anyMap())).thenReturn("个性化推荐语");
    }

    @Test
    @DisplayName("recommendLearning 未登录返回提示")
    void nullUser() {
        Map<String, Object> r = service.recommendLearning(null);
        assertEquals("请先登录", r.get("recommendation"));
    }

    @Test
    @DisplayName("recommendLearning 无选课返回提示")
    void noEnrollment() {
        when(courseEnrollmentService.list(any(Wrapper.class))).thenReturn(List.of());
        Map<String, Object> r = service.recommendLearning(1L);
        assertEquals("您尚未加入任何课程，请先选课以获取个性化推荐。", r.get("recommendation"));
    }

    @Test
    @DisplayName("recommendLearning 正常路径组装上下文并调用 LLM")
    void ok() {
        Map<String, Object> r = service.recommendLearning(1L);
        assertEquals("个性化推荐语", r.get("recommendation"));
        assertTrue((Boolean) r.get("dataDriven"));
        // weakPoints 写入传递给 LLM 的 context，而非 result
        @SuppressWarnings("unchecked")
        Map<String, Object> ctx = (Map<String, Object>) captureContext();
        @SuppressWarnings("unchecked")
        List<?> weak = (List<?>) ctx.get("weakPoints");
        assertEquals(1, weak.size());
        assertTrue(ctx.containsKey("courseList"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureContext() {
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(aiService).recommendLearningWithContext(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("recommendLearning 薄弱点查询异常降级")
    void weakPointsDegrade() {
        when(knowledgePointService.list(any(Wrapper.class))).thenThrow(new RuntimeException("kp err"));
        Map<String, Object> r = service.recommendLearning(1L);
        assertEquals("个性化推荐语", r.get("recommendation"));
        assertFalse(r.containsKey("weakPoints"));
    }

    @Test
    @DisplayName("recommendLearning 成绩统计异常降级")
    void scoresDegrade() {
        when(analyticsService.getUserDashboard(anyLong())).thenThrow(new RuntimeException("analytics err"));
        Map<String, Object> r = service.recommendLearning(1L);
        assertEquals("个性化推荐语", r.get("recommendation"));
        assertFalse(r.containsKey("recentScores"));
    }

    @Test
    @DisplayName("recommendLearning 掌握度达标则不计入薄弱点")
    void noWeakPoints() {
        when(knowledgePointService.calculateMastery(anyLong(), anyLong())).thenReturn(new BigDecimal("90"));
        Map<String, Object> r = service.recommendLearning(1L);
        @SuppressWarnings("unchecked")
        Map<String, Object> ctx = (Map<String, Object>) captureContext();
        @SuppressWarnings("unchecked")
        List<?> weak = (List<?>) ctx.get("weakPoints");
        assertTrue(weak.isEmpty());
    }

    @Test
    @DisplayName("recommendLearning 薄弱点超上限时截断至 MAX_WEAK_POINTS 且按掌握度升序")
    void weakPointsTruncated() {
        List<KnowledgePoint> kps = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            KnowledgePoint p = new KnowledgePoint();
            p.setId(i);
            p.setCourseId(10L);
            p.setName("kp" + i);
            kps.add(p);
        }
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(kps);
        // 掌握度：id 1-9 → 5,10,...,45（均 <70，共 9 个薄弱点）；id 10 → 100（达标）
        when(knowledgePointService.calculateMastery(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    long id = inv.getArgument(0, Long.class);
                    return id <= 9 ? BigDecimal.valueOf(id * 5) : BigDecimal.valueOf(100);
                });

        Map<String, Object> r = service.recommendLearning(1L);
        @SuppressWarnings("unchecked")
        Map<String, Object> ctx = (Map<String, Object>) captureContext();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> weak = (List<Map<String, Object>>) ctx.get("weakPoints");
        assertEquals(8, weak.size()); // MAX_WEAK_POINTS
        assertEquals(new BigDecimal("5"), weak.get(0).get("mastery")); // 最弱优先
        assertEquals(new BigDecimal("40"), weak.get(7).get("mastery")); // 截断在第 8 个（id 8）
    }

    @Test
    @DisplayName("recommendLearning 成绩统计无 avgScore 则不追加")
    void noRecentScores() {
        when(analyticsService.getUserDashboard(anyLong())).thenReturn(Map.of("other", 1));
        Map<String, Object> r = service.recommendLearning(1L);
        assertFalse(r.containsKey("recentScores"));
    }
}
