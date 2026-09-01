package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.mapper.LearningPathMapper;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.vo.LearningPathDetailVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LearningPathServiceImpl 单元测试（纯 Mockito）。
 * <p>继承 ServiceImpl，需在 setUp 注入 baseMapper (mock) 并注册 LearningPath/KnowledgePoint 的 TableInfo，
 * 否则源码内 LambdaQueryWrapper.eq(...) 解析列名会失败。</p>
 */
@ExtendWith(MockitoExtension.class)
class LearningPathServiceImplTest {

    @Mock private LearningPathNodeService nodeService;
    @Mock private KnowledgePointService knowledgePointService;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private LearningPathMapper learningPathMapper;

    private LearningPathServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), LearningPath.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), KnowledgePoint.class);
        service = new LearningPathServiceImpl(nodeService, knowledgePointService, wrongQuestionService);
        ReflectionTestUtils.setField(service, "baseMapper", learningPathMapper);
        // 同步 mapper 到持有 mapper 的内部组件（兼容构造器组装的真实组件初始持有 null mapper；mastery 组件无 mapper 字段）
        for (String field : new String[]{"stateComponent", "generateComponent", "queryComponent"}) {
            ReflectionTestUtils.setField(ReflectionTestUtils.getField(service, field), "learningPathMapper", learningPathMapper);
        }
    }

    private KnowledgePoint kp(Long id, String name, Integer importance, Integer est, String type) {
        KnowledgePoint p = new KnowledgePoint();
        p.setId(id); p.setName(name); p.setImportance(importance);
        p.setEstimatedTime(est); p.setPointType(type); p.setCourseId(1L);
        return p;
    }

    // ===================== deriveMastery =====================
    @Test
    @DisplayName("deriveMastery 空分组返回空")
    void deriveMastery_empty() {
        when(wrongQuestionService.getByKnowledgePoints(1L, 1L)).thenReturn(Collections.emptyMap());
        assertTrue(service.deriveMastery(1L, 1L).isEmpty());
    }

    @Test
    @DisplayName("deriveMastery 命中知识点用 calculateMastery")
    void deriveMastery_matched() {
        WrongQuestion wq = new WrongQuestion(); wq.setMastered(true);
        when(wrongQuestionService.getByKnowledgePoints(1L, 1L)).thenReturn(Map.of("函数", List.of(wq)));
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of(kp(1L, "函数", 5, 60, "CONCEPT")));
        when(knowledgePointService.calculateMastery(1L, 1L)).thenReturn(new BigDecimal("80"));

        Map<String, Double> r = service.deriveMastery(1L, 1L);
        assertEquals(80.0, r.get("函数"));
    }

    @Test
    @DisplayName("deriveMastery 未命中走兜底公式")
    void deriveMastery_fallback() {
        WrongQuestion wq = new WrongQuestion(); wq.setMastered(false);
        when(wrongQuestionService.getByKnowledgePoints(1L, 1L)).thenReturn(Map.of("未知", List.of(wq)));
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());

        Map<String, Double> r = service.deriveMastery(1L, 1L);
        assertEquals(75.0, r.get("未知"));
    }

    @Test
    @DisplayName("deriveMastery 异常静默降级")
    void deriveMastery_exception() {
        when(wrongQuestionService.getByKnowledgePoints(anyLong(), anyLong())).thenThrow(new RuntimeException("boom"));
        assertTrue(service.deriveMastery(1L, 1L).isEmpty());
    }

    // ===================== generateLearningPath =====================
    @Test
    @DisplayName("generateLearningPath 按薄弱优先生成节点")
    void generateLearningPath_ok() {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of(
                kp(1L, "A", 5, 60, "CONCEPT"), kp(2L, "B", 3, 60, "CONCEPT")));

        List<LearningPathNode> nodes = service.generateLearningPath(1L, 1L);
        assertEquals(2, nodes.size());
        assertEquals("A", nodes.get(0).getTitle());
        assertEquals("B", nodes.get(1).getTitle());
    }

    // ===================== 状态机 =====================
    @Test
    @DisplayName("getActivePath 命中返回路径")
    void getActivePath_found() {
        LearningPath p = new LearningPath(); p.setId(1L);
        when(learningPathMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(p);
        assertSame(p, service.getActivePath(1L, 1L));
    }

    @Test
    @DisplayName("getActivePath 无命中返回 null")
    void getActivePath_null() {
        when(learningPathMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        assertNull(service.getActivePath(1L, 1L));
    }

    @Test
    @DisplayName("updateProgress 无节点直接返回")
    void updateProgress_empty() {
        when(nodeService.getPathNodes(1L)).thenReturn(List.of());
        service.updateProgress(1L);
        verify(nodeService).getPathNodes(1L);
        verify(learningPathMapper, never()).updateById(any(LearningPath.class));
    }

    @Test
    @DisplayName("updateProgress 部分完成仅更新完成率")
    void updateProgress_partial() {
        LearningPathNode n1 = new LearningPathNode(); n1.setStatus("COMPLETED");
        LearningPathNode n2 = new LearningPathNode(); n2.setStatus("PENDING");
        when(nodeService.getPathNodes(1L)).thenReturn(List.of(n1, n2));
        LearningPath p = new LearningPath(); p.setId(1L);
        when(learningPathMapper.selectById(any())).thenReturn(p);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        service.updateProgress(1L);
        verify(learningPathMapper, times(1)).updateById(any(LearningPath.class));
    }

    @Test
    @DisplayName("updateProgress 全部完成触发 completePath")
    void updateProgress_allCompleted() {
        LearningPathNode n1 = new LearningPathNode(); n1.setStatus("COMPLETED");
        LearningPathNode n2 = new LearningPathNode(); n2.setStatus("COMPLETED");
        when(nodeService.getPathNodes(1L)).thenReturn(List.of(n1, n2));
        LearningPath p = new LearningPath(); p.setId(1L);
        when(learningPathMapper.selectById(any())).thenReturn(p);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        service.updateProgress(1L);
        // updateProgress 一次 updateById + completePath 一次 updateById
        verify(learningPathMapper, times(2)).updateById(any(LearningPath.class));
    }

    @Test
    @DisplayName("advanceToNextStage 路径不存在抛异常")
    void advance_null() {
        when(learningPathMapper.selectById(any())).thenReturn(null);
        assertThrows(com.aicourse.exception.BusinessException.class, () -> service.advanceToNextStage(1L));
    }

    @Test
    @DisplayName("advanceToNextStage 未超阶段仅更新")
    void advance_notExceed() {
        LearningPath p = new LearningPath(); p.setId(1L); p.setCurrentStage(1); p.setTotalStages(3);
        when(learningPathMapper.selectById(any())).thenReturn(p);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);
        service.advanceToNextStage(1L);
        assertEquals(2, p.getCurrentStage());
        verify(learningPathMapper, times(1)).updateById(any(LearningPath.class));
    }

    @Test
    @DisplayName("advanceToNextStage 超阶段触发完成")
    void advance_exceed() {
        LearningPath p = new LearningPath(); p.setId(1L); p.setCurrentStage(2); p.setTotalStages(3);
        when(learningPathMapper.selectById(any())).thenReturn(p);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);
        service.advanceToNextStage(1L);
        verify(learningPathMapper, times(1)).updateById(any(LearningPath.class));
    }

    @Test
    @DisplayName("completePath 正常完成")
    void completePath_ok() {
        LearningPath p = new LearningPath(); p.setId(1L); p.setStatus("ACTIVE");
        when(learningPathMapper.selectById(any())).thenReturn(p);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);
        service.completePath(1L);
        assertEquals("COMPLETED", p.getStatus());
        assertEquals(new BigDecimal("100.00"), p.getCompletionRate());
        verify(learningPathMapper).updateById(any(LearningPath.class));
    }

    @Test
    @DisplayName("pausePath 状态校验与更新")
    void pausePath_ok() {
        LearningPath p = new LearningPath(); p.setId(1L); p.setStatus("ACTIVE");
        when(learningPathMapper.selectById(any())).thenReturn(p);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);
        service.pausePath(1L);
        assertEquals("PAUSED", p.getStatus());
    }

    @Test
    @DisplayName("pausePath 非活跃状态抛异常")
    void pausePath_notActive() {
        LearningPath p = new LearningPath(); p.setId(1L); p.setStatus("COMPLETED");
        when(learningPathMapper.selectById(any())).thenReturn(p);
        assertThrows(com.aicourse.exception.BusinessException.class, () -> service.pausePath(1L));
    }

    @Test
    @DisplayName("resumePath 暂停态恢复")
    void resumePath_ok() {
        LearningPath p = new LearningPath(); p.setId(1L); p.setStatus("PAUSED");
        when(learningPathMapper.selectById(any())).thenReturn(p);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);
        service.resumePath(1L);
        assertEquals("ACTIVE", p.getStatus());
    }

    // ===================== 详情/每日任务 =====================
    @Test
    @DisplayName("getDailyTasks 无数据返回空")
    void getDailyTasks_null() {
        LearningPath p = new LearningPath(); p.setId(1L);
        when(learningPathMapper.selectById(any())).thenReturn(p);
        assertTrue(service.getDailyTasks(1L).isEmpty());
    }

    @Test
    @DisplayName("getDailyTasks 合法 JSON 解析")
    void getDailyTasks_valid() {
        LearningPath p = new LearningPath(); p.setId(1L);
        p.setDailyTasks("[{\"dayNumber\":1,\"taskTitle\":\"t\"}]");
        when(learningPathMapper.selectById(any())).thenReturn(p);
        assertEquals(1, service.getDailyTasks(1L).size());
    }

    @Test
    @DisplayName("getPathDetail 正常解析 stages/planDetail")
    void getPathDetail_ok() {
        LearningPath p = new LearningPath(); p.setId(1L);
        p.setStages("[{\"stageNumber\":1,\"stageName\":\"S\"}]");
        p.setPlanDetail("{\"totalStages\":1}");
        when(learningPathMapper.selectById(any())).thenReturn(p);
        when(nodeService.getPathNodes(1L)).thenReturn(List.of());
        when(nodeService.getNodeStatusStats(1L)).thenReturn(Map.of("COMPLETED", 1L));

        LearningPathDetailVO d = service.getPathDetail(1L);
        assertNotNull(d.getStagesParsed());
        assertNotNull(d.getPlanDetailParsed());
    }

    @Test
    @DisplayName("getPathDetail stages 非法 JSON 回退原始串")
    void getPathDetail_badStages() {
        LearningPath p = new LearningPath(); p.setId(1L);
        p.setStages("not-json");
        when(learningPathMapper.selectById(any())).thenReturn(p);
        when(nodeService.getPathNodes(1L)).thenReturn(List.of());
        when(nodeService.getNodeStatusStats(1L)).thenReturn(Map.of());
        LearningPathDetailVO d = service.getPathDetail(1L);
        assertNotNull(d.getStagesRaw());
    }

    // ===================== 生成路径 =====================
    @Test
    @DisplayName("generatePath 已有活跃路径抛异常")
    void generatePath_existingActive() {
        LearningPath active = new LearningPath(); active.setId(9L);
        when(learningPathMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(active);
        assertThrows(com.aicourse.exception.BusinessException.class,
                () -> service.generatePath(1L, 1L, "n", "g"));
    }

    @Test
    @DisplayName("generatePath 无根知识点抛异常")
    void generatePath_noRoot() {
        when(learningPathMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(knowledgePointService.getRootPoints(1L)).thenReturn(List.of());
        assertThrows(com.aicourse.exception.BusinessException.class,
                () -> service.generatePath(1L, 1L, "n", "g"));
    }

    @Test
    @DisplayName("generatePath 正常生成（含子节点）")
    void generatePath_ok() {
        when(learningPathMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        KnowledgePoint root = kp(10L, "R", 5, 60, "CONCEPT");
        when(knowledgePointService.getRootPoints(1L)).thenReturn(List.of(root));
        KnowledgePoint c1 = kp(20L, "C1", 3, 30, "EXAM");
        KnowledgePoint c2 = kp(21L, "C2", 3, 30, "VIDEO");
        when(knowledgePointService.getChildPoints(10L)).thenReturn(List.of(c1, c2));
        when(learningPathMapper.insert(any(LearningPath.class))).thenReturn(1);
        when(nodeService.save(any(LearningPathNode.class))).thenReturn(true);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        LearningPath p = service.generatePath(1L, 1L, "n", "g");
        assertEquals("ACTIVE", p.getStatus());
        assertNotNull(p.getStages());
        assertEquals(1, p.getTotalStages());
        verify(nodeService, times(2)).save(any(LearningPathNode.class));
    }

    @Test
    @DisplayName("generatePath 根无子节点时为根自身建节点（默认 STUDY）")
    void generatePath_rootNoChild() {
        when(learningPathMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        KnowledgePoint root = kp(10L, "R", 5, 60, "CONCEPT");
        when(knowledgePointService.getRootPoints(1L)).thenReturn(List.of(root));
        when(knowledgePointService.getChildPoints(10L)).thenReturn(List.of());
        when(learningPathMapper.insert(any(LearningPath.class))).thenReturn(1);
        when(nodeService.save(any(LearningPathNode.class))).thenReturn(true);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        LearningPath p = service.generatePath(1L, 1L, "n", "g");
        verify(nodeService, times(1)).save(any(LearningPathNode.class));
        assertEquals(1, p.getTotalStages());
    }

    @Test
    @DisplayName("generatePathPersonalized 带掌握度数据正常生成")
    void generatePathPersonalized_withMastery() {
        when(learningPathMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        KnowledgePoint root = kp(10L, "R", 5, 60, "CONCEPT");
        when(knowledgePointService.getRootPoints(1L)).thenReturn(List.of(root));
        KnowledgePoint c1 = kp(20L, "C1", 3, 30, "PRACTICE");
        KnowledgePoint c2 = kp(21L, "C2", 3, 30, "EASY");
        KnowledgePoint c3 = kp(22L, "C3", 3, 30, "HARD");
        when(knowledgePointService.getChildPoints(10L)).thenReturn(List.of(c1, c2, c3));
        when(learningPathMapper.insert(any(LearningPath.class))).thenReturn(1);
        when(nodeService.save(any(LearningPathNode.class))).thenReturn(true);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        LearningPath p = service.generatePathPersonalized(1L, 1L, "n", "g", Map.of("R", 30.0));
        assertEquals("ACTIVE", p.getStatus());
        verify(nodeService, times(3)).save(any(LearningPathNode.class));
    }

    @Test
    @DisplayName("generatePathPersonalized 无数据回退标准生成")
    void generatePathPersonalized_fallback() {
        when(learningPathMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(wrongQuestionService.getByKnowledgePoints(1L, 1L)).thenReturn(Collections.emptyMap());
        KnowledgePoint root = kp(10L, "R", 5, 60, "CONCEPT");
        when(knowledgePointService.getRootPoints(1L)).thenReturn(List.of(root));
        when(knowledgePointService.getChildPoints(10L)).thenReturn(List.of());
        when(learningPathMapper.insert(any(LearningPath.class))).thenReturn(1);
        when(nodeService.save(any(LearningPathNode.class))).thenReturn(true);
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);

        LearningPath p = service.generatePathPersonalized(1L, 1L, "n", "g", Map.of());
        assertNotNull(p);
    }

    // ===================== 列表/预估 =====================
    @Test
    @DisplayName("getStudentPaths 返回路径列表")
    void getStudentPaths() {
        LearningPath p = new LearningPath(); p.setId(1L);
        when(learningPathMapper.selectList(any())).thenReturn(List.of(p));
        assertEquals(1, service.getStudentPaths(1L, 1L).size());
    }

    @Test
    @DisplayName("estimateCompletionDate 无完成节点用默认")
    void estimate_noCompleted() {
        LearningPath p = new LearningPath(); p.setId(1L);
        when(learningPathMapper.selectById(any())).thenReturn(p);
        LearningPathNode n = new LearningPathNode(); n.setStatus("PENDING");
        when(nodeService.getPathNodes(1L)).thenReturn(List.of(n));
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);
        service.estimateCompletionDate(1L);
        verify(learningPathMapper).updateById(any(LearningPath.class));
    }

    @Test
    @DisplayName("estimateCompletionDate 有完成节点按耗时推算")
    void estimate_withCompleted() {
        LearningPath p = new LearningPath(); p.setId(1L);
        when(learningPathMapper.selectById(any())).thenReturn(p);
        LearningPathNode done = new LearningPathNode(); done.setStatus("COMPLETED"); done.setActualTime(60);
        LearningPathNode pend = new LearningPathNode(); pend.setStatus("PENDING");
        when(nodeService.getPathNodes(1L)).thenReturn(List.of(done, pend));
        when(learningPathMapper.updateById(any(LearningPath.class))).thenReturn(1);
        service.estimateCompletionDate(1L);
        verify(learningPathMapper).updateById(any(LearningPath.class));
    }
}
