package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.LearningPathMapper;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.service.WrongQuestionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearningPathServiceImplBoostTest {

    @Mock private LearningPathNodeService nodeService;
    @Mock private KnowledgePointService knowledgePointService;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private LearningPathMapper learningPathMapper;

    private LearningPathServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), LearningPath.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), KnowledgePoint.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), LearningPathNode.class);

        service = new LearningPathServiceImpl(nodeService, knowledgePointService, wrongQuestionService);
        ReflectionTestUtils.setField(service, "baseMapper", learningPathMapper);
        // 同步 mapper 到持有 mapper 的内部组件（兼容构造器组装的真实组件初始持有 null mapper；mastery 组件无 mapper 字段）
        for (String field : new String[]{"stateComponent", "generateComponent", "queryComponent"}) {
            ReflectionTestUtils.setField(ReflectionTestUtils.getField(service, field), "learningPathMapper", learningPathMapper);
        }

        lenient().when(learningPathMapper.selectById(anyLong())).thenReturn(null);
        lenient().when(learningPathMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);
        lenient().when(learningPathMapper.selectList(any())).thenReturn(new ArrayList<>());
        lenient().when(learningPathMapper.insert(any(LearningPath.class))).thenReturn(1);
        lenient().when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);
        lenient().when(nodeService.getPathNodes(anyLong())).thenReturn(new ArrayList<>());
        lenient().when(nodeService.getNodeStatusStats(anyLong())).thenReturn(new HashMap<>());
        lenient().when(nodeService.save(any(LearningPathNode.class))).thenReturn(true);
        lenient().when(knowledgePointService.getRootPoints(anyLong())).thenReturn(new ArrayList<>());
        lenient().when(knowledgePointService.getChildPoints(anyLong())).thenReturn(new ArrayList<>());
        lenient().when(knowledgePointService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
    }

    // ===== deriveMastery =====

    @Test
    @DisplayName("deriveMastery 学情异常静默降级")
    void derive_exception() {
        when(wrongQuestionService.getByKnowledgePoints(anyLong(), anyLong()))
                .thenThrow(new RuntimeException("db err"));
        assertTrue(service.deriveMastery(1L, 1L).isEmpty());
    }

    @Test
    @DisplayName("deriveMastery 知识点匹配复用 calculateMastery")
    void derive_matched() {
        KnowledgePoint p = new KnowledgePoint(); p.setId(5L); p.setName("递归"); p.setCourseId(1L);
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of(p));
        Map<String, List<WrongQuestion>> grouped = new HashMap<>();
        grouped.put("递归", new ArrayList<>(List.of(new WrongQuestion())));
        when(wrongQuestionService.getByKnowledgePoints(anyLong(), anyLong())).thenReturn(grouped);
        when(knowledgePointService.calculateMastery(5L, 1L)).thenReturn(BigDecimal.valueOf(40));
        Map<String, Double> m = service.deriveMastery(1L, 1L);
        assertEquals(40.0, m.get("递归"));
    }

    @Test
    @DisplayName("deriveMastery 名称不匹配走兜底公式")
    void derive_fallback() {
        KnowledgePoint p = new KnowledgePoint(); p.setId(5L); p.setName("其它"); p.setCourseId(1L);
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of(p));
        WrongQuestion w = new WrongQuestion(); w.setMastered(false);
        Map<String, List<WrongQuestion>> grouped = new HashMap<>();
        grouped.put("未知点", new ArrayList<>(List.of(w)));
        when(wrongQuestionService.getByKnowledgePoints(anyLong(), anyLong())).thenReturn(grouped);
        Map<String, Double> m = service.deriveMastery(1L, 1L);
        assertTrue(m.containsKey("未知点"));
    }

    @Test
    @DisplayName("deriveMastery 空分组返回空")
    void derive_empty() {
        when(wrongQuestionService.getByKnowledgePoints(anyLong(), anyLong())).thenReturn(new HashMap<>());
        assertTrue(service.deriveMastery(1L, 1L).isEmpty());
    }

    // ===== generateLearningPath =====

    @Test
    @DisplayName("generateLearningPath 无知识点返回空")
    void gen_emptyPoints() {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        assertTrue(service.generateLearningPath(1L, 1L).isEmpty());
    }

    @Test
    @DisplayName("generateLearningPath 薄弱优先并注入学情描述")
    void gen_withMastery() {
        KnowledgePoint p = new KnowledgePoint();
        p.setId(1L); p.setName("递归"); p.setImportance(5); p.setEstimatedTime(60);
        p.setDescription("基础");
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of(p));
        // 模拟学情：学生「递归」知识点有错题，使其进入 mastery 推导结果
        Map<String, List<WrongQuestion>> grouped = new HashMap<>();
        grouped.put("递归", new ArrayList<>(List.of(new WrongQuestion())));
        when(wrongQuestionService.getByKnowledgePoints(anyLong(), anyLong())).thenReturn(grouped);
        when(knowledgePointService.calculateMastery(1L, 1L)).thenReturn(BigDecimal.valueOf(50));
        List<LearningPathNode> nodes = service.generateLearningPath(1L, 1L);
        assertEquals(1, nodes.size());
        assertTrue(nodes.get(0).getDescription().contains("学情诊断"));
    }

    @Test
    @DisplayName("generateLearningPath 超过 8 个知识点截断")
    void gen_moreThan8() {
        List<KnowledgePoint> pts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            KnowledgePoint p = new KnowledgePoint();
            p.setId((long) i); p.setName("KP" + i); p.setImportance(i);
            pts.add(p);
        }
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(pts);
        assertEquals(8, service.generateLearningPath(1L, 1L).size());
    }

    // ===== 生命周期 =====

    @Test
    @DisplayName("updateProgress 无节点直接返回")
    void updateProgress_empty() {
        when(nodeService.getPathNodes(anyLong())).thenReturn(new ArrayList<>());
        assertDoesNotThrow(() -> service.updateProgress(1L));
        verify(learningPathMapper, never()).updateById(any(LearningPath.class));
    }

    @Test
    @DisplayName("updateProgress 全部完成触发 completePath")
    void updateProgress_allComplete() {
        LearningPathNode n1 = new LearningPathNode(); n1.setStatus("COMPLETED");
        LearningPathNode n2 = new LearningPathNode(); n2.setStatus("COMPLETED");
        when(nodeService.getPathNodes(anyLong())).thenReturn(List.of(n1, n2));
        LearningPath path = new LearningPath(); path.setId(1L);
        when(learningPathMapper.selectById(anyLong())).thenReturn(path);
        service.updateProgress(1L);
        // 全部完成：updateProgress 先更新完成率(updateById)，再 completePath 终态置 COMPLETED(updateById)，共两次
        verify(learningPathMapper, times(2)).updateById(any(LearningPath.class));
    }

    @Test
    @DisplayName("advanceToNextStage 路径不存在抛异常")
    void advance_null() {
        when(learningPathMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.advanceToNextStage(1L));
    }

    @Test
    @DisplayName("advanceToNextStage 超过总阶段完成路径")
    void advance_exceeds() {
        LearningPath path = new LearningPath(); path.setId(1L);
        path.setCurrentStage(2); path.setTotalStages(2);
        when(learningPathMapper.selectById(anyLong())).thenReturn(path);
        service.advanceToNextStage(1L);
        assertEquals("COMPLETED", path.getStatus());
    }

    @Test
    @DisplayName("advanceToNextStage 正常推进更新")
    void advance_normal() {
        LearningPath path = new LearningPath(); path.setId(1L);
        path.setCurrentStage(0); path.setTotalStages(3);
        when(learningPathMapper.selectById(anyLong())).thenReturn(path);
        service.advanceToNextStage(1L);
        assertEquals(1, path.getCurrentStage());
        verify(learningPathMapper).updateById(any(LearningPath.class));
    }

    @Test
    @DisplayName("completePath 路径不存在抛异常")
    void complete_null() {
        when(learningPathMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.completePath(1L));
    }

    @Test
    @DisplayName("pausePath 各分支")
    void pause_branches() {
        when(learningPathMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.pausePath(1L));

        LearningPath p = new LearningPath(); p.setId(1L); p.setStatus("COMPLETED");
        when(learningPathMapper.selectById(anyLong())).thenReturn(p);
        assertThrows(BusinessException.class, () -> service.pausePath(1L));

        p.setStatus("ACTIVE");
        service.pausePath(1L);
        assertEquals("PAUSED", p.getStatus());
    }

    @Test
    @DisplayName("resumePath 各分支")
    void resume_branches() {
        when(learningPathMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.resumePath(1L));

        LearningPath p = new LearningPath(); p.setId(1L); p.setStatus("ACTIVE");
        when(learningPathMapper.selectById(anyLong())).thenReturn(p);
        assertThrows(BusinessException.class, () -> service.resumePath(1L));

        p.setStatus("PAUSED");
        service.resumePath(1L);
        assertEquals("ACTIVE", p.getStatus());
    }

    // ===== getActivePath / getStudentPaths =====

    @Test
    @DisplayName("getActivePath / getStudentPaths")
    void activeAndList() {
        assertNull(service.getActivePath(1L, 1L));
        when(learningPathMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertTrue(service.getStudentPaths(1L, 1L).isEmpty());
    }

    // ===== getDailyTasks =====

    @Test
    @DisplayName("getDailyTasks null / 空 / 解析失败 / 解析成功")
    void dailyTasks_branches() {
        when(learningPathMapper.selectById(anyLong())).thenReturn(null);
        assertTrue(service.getDailyTasks(1L).isEmpty());

        LearningPath p = new LearningPath();
        when(learningPathMapper.selectById(anyLong())).thenReturn(p);
        assertTrue(service.getDailyTasks(1L).isEmpty());

        p.setDailyTasks("not json");
        assertTrue(service.getDailyTasks(1L).isEmpty());

        p.setDailyTasks("[{\"day\":1}]");
        assertEquals(1, service.getDailyTasks(1L).size());
    }

    // ===== getPathDetail =====

    @Test
    @DisplayName("getPathDetail null / stages与planDetail解析失败回退原始串")
    void pathDetail_branches() {
        when(learningPathMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getPathDetail(1L));

        LearningPath p = new LearningPath();
        p.setId(1L); p.setStages("bad"); p.setPlanDetail("bad");
        when(learningPathMapper.selectById(anyLong())).thenReturn(p);
        when(nodeService.getPathNodes(anyLong())).thenReturn(new ArrayList<>());
        when(nodeService.getNodeStatusStats(anyLong())).thenReturn(new HashMap<>());
        Map<String, Object> r = service.getPathDetail(1L);
        assertTrue(r.containsKey("stagesRaw"));
        assertTrue(r.containsKey("planDetailRaw"));
    }

    // ===== generatePath =====

    @Test
    @DisplayName("generatePath 已有活跃路径抛异常")
    void genPath_existing() {
        LearningPath active = new LearningPath(); active.setId(9L); active.setStatus("ACTIVE");
        when(learningPathMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(active);
        assertThrows(BusinessException.class, () -> service.generatePath(1L, 1L, "n", "g"));
    }

    @Test
    @DisplayName("generatePath 无根知识点抛异常")
    void genPath_noRoot() {
        when(knowledgePointService.getRootPoints(anyLong())).thenReturn(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.generatePath(1L, 1L, "n", "g"));
    }

    @Test
    @DisplayName("generatePath 生成含子节点阶段")
    void genPath_ok() {
        KnowledgePoint root = new KnowledgePoint(); root.setId(1L); root.setName("根"); root.setEstimatedTime(60);
        when(knowledgePointService.getRootPoints(anyLong())).thenReturn(List.of(root));
        KnowledgePoint child = new KnowledgePoint(); child.setId(2L); child.setName("子"); child.setPointType("VIDEO"); child.setEstimatedTime(30);
        when(knowledgePointService.getChildPoints(1L)).thenReturn(List.of(child));
        LearningPath r = service.generatePath(1L, 1L, "n", "g");
        assertNotNull(r);
        verify(nodeService, atLeastOnce()).save(any(LearningPathNode.class));
    }

    // ===== generatePathPersonalized =====

    @Test
    @DisplayName("generatePathPersonalized 掌握度为空回退标准生成")
    void personalized_emptyMastery() {
        when(wrongQuestionService.getByKnowledgePoints(anyLong(), anyLong())).thenReturn(new HashMap<>());
        when(knowledgePointService.getRootPoints(anyLong())).thenReturn(new ArrayList<>());
        assertThrows(BusinessException.class,
                () -> service.generatePathPersonalized(1L, 1L, "n", "g", null));
    }

    @Test
    @DisplayName("generatePathPersonalized 已有活跃路径抛异常")
    void personalized_existing() {
        LearningPath active = new LearningPath(); active.setId(9L); active.setStatus("ACTIVE");
        when(learningPathMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(active);
        Map<String, Double> m = new HashMap<>(); m.put("X", 10.0);
        assertThrows(BusinessException.class,
                () -> service.generatePathPersonalized(1L, 1L, "n", "g", m));
    }

    @Test
    @DisplayName("generatePathPersonalized 使用掌握度生成并覆盖难度排序")
    void personalized_ok() {
        Map<String, Double> m = new HashMap<>(); m.put("X", 10.0);
        when(wrongQuestionService.getByKnowledgePoints(anyLong(), anyLong())).thenReturn(new HashMap<>());
        KnowledgePoint root = new KnowledgePoint(); root.setId(1L); root.setName("X"); root.setEstimatedTime(60);
        when(knowledgePointService.getRootPoints(anyLong())).thenReturn(List.of(root));
        KnowledgePoint child = new KnowledgePoint(); child.setId(2L); child.setName("C"); child.setPointType("PRACTICE");
        child.setDifficultyLevel("HARD"); child.setEstimatedTime(30);
        when(knowledgePointService.getChildPoints(1L)).thenReturn(List.of(child));
        LearningPath r = service.generatePathPersonalized(1L, 1L, "n", "g", m);
        assertNotNull(r);
    }

    // ===== determineNodeType（反射，迁移至 generate 组件后）=====

    @Test
    @DisplayName("determineNodeType 各类知识点类型")
    void determineNodeType() throws Exception {
        Object gen = ReflectionTestUtils.getField(service, "generateComponent");
        Method m = gen.getClass().getDeclaredMethod("determineNodeType", String.class);
        m.setAccessible(true);
        assertEquals("TEST", m.invoke(gen, "EXAM"));
        assertEquals("TEST", m.invoke(gen, "TEST"));
        assertEquals("PRACTICE", m.invoke(gen, "PRACTICE"));
        assertEquals("PRACTICE", m.invoke(gen, "EXERCISE"));
        assertEquals("VIDEO", m.invoke(gen, "VIDEO"));
        assertEquals("VIDEO", m.invoke(gen, "COURSEWARE"));
        assertEquals("STUDY", m.invoke(gen, "OTHER"));
        assertEquals("STUDY", m.invoke(gen, (Object) null));
    }

    // ===== estimateCompletionDate =====

    @Test
    @DisplayName("estimateCompletionDate 路径不存在抛异常")
    void estimate_null() {
        when(learningPathMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.estimateCompletionDate(1L));
    }

    @Test
    @DisplayName("estimateCompletionDate 无完成节点用默认 30 天")
    void estimate_noCompleted() {
        LearningPath p = new LearningPath(); p.setId(1L);
        when(learningPathMapper.selectById(anyLong())).thenReturn(p);
        when(nodeService.getPathNodes(anyLong())).thenReturn(new ArrayList<>());
        service.estimateCompletionDate(1L);
        assertNotNull(p.getEstimatedCompletionDate());
    }

    @Test
    @DisplayName("estimateCompletionDate 按平均耗时推算")
    void estimate_computed() {
        LearningPath p = new LearningPath(); p.setId(1L);
        when(learningPathMapper.selectById(anyLong())).thenReturn(p);
        LearningPathNode n1 = new LearningPathNode(); n1.setStatus("COMPLETED"); n1.setActualTime(120);
        LearningPathNode n2 = new LearningPathNode(); n2.setStatus("PENDING");
        when(nodeService.getPathNodes(anyLong())).thenReturn(List.of(n1, n2));
        service.estimateCompletionDate(1L);
        assertNotNull(p.getEstimatedCompletionDate());
    }
}
