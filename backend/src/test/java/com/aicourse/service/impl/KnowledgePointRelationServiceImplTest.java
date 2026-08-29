package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.KnowledgePointRelationMapper;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * KnowledgePointRelationServiceImpl 单元测试（B 主战场新文件类，缺口 368）。
 * extends ServiceImpl<KnowledgePointRelationMapper, KnowledgePointRelation>：手动构造(baseMapper) + 注入 @Lazy KnowledgePointService(mock)；
 * 实体 lambda wrapper 涉及 KnowledgePointRelation / KnowledgePoint → 注册 TableInfo；
 * save/list/getById/removeById/count(page) 分别锚定到 insert/selectList/selectById/deleteById/selectCount/selectPage。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgePointRelationServiceImplTest {

    @Mock private KnowledgePointService knowledgePointService;
    @Mock private KnowledgePointRelationMapper mapper;

    private KnowledgePointRelationServiceImpl service;
    private KnowledgePoint kp;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), KnowledgePointRelation.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), KnowledgePoint.class);

        service = new KnowledgePointRelationServiceImpl(knowledgePointService);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        kp = new KnowledgePoint();
        kp.setId(1L);
        kp.setName("函数");
        kp.setCourseId(10L);
        kp.setPointType("concept");

        when(knowledgePointService.getById(anyLong())).thenReturn(kp);
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of(kp));
        when(mapper.selectById(anyLong())).thenReturn(new KnowledgePointRelation());
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(new KnowledgePointRelation()));
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(mapper.insert(any(KnowledgePointRelation.class))).thenReturn(1);
        when(mapper.deleteById(anyLong())).thenReturn(1);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<KnowledgePointRelation> p = inv.getArgument(0);
            p.setRecords(List.of(new KnowledgePointRelation()));
            p.setTotal(1);
            return p;
        });
    }

    // ===================== createRelation =====================

    @Test
    @DisplayName("createRelation 正常创建")
    void create_ok() {
        KnowledgePointRelation r = service.createRelation(1L, 2L, "prereq", new BigDecimal("0.5"), "说明");
        assertNotNull(r);
        verify(mapper).insert(any(KnowledgePointRelation.class));
    }

    @Test
    @DisplayName("createRelation 源知识点不存在抛异常")
    void create_srcNull() {
        when(knowledgePointService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.createRelation(1L, 2L, "prereq", null, null));
    }

    @Test
    @DisplayName("createRelation 目标知识点不存在抛异常")
    void create_tgtNull() {
        when(knowledgePointService.getById(2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.createRelation(1L, 2L, "prereq", null, null));
    }

    @Test
    @DisplayName("createRelation 已存在则抛异常")
    void create_exists() {
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.createRelation(1L, 2L, "prereq", null, null));
    }

    // ===================== getRelationsByPoint =====================

    @Test
    @DisplayName("getRelationsByPoint 返回关联列表")
    void byPoint_ok() {
        assertEquals(1, service.getRelationsByPoint(1L).size());
    }

    // ===================== deleteRelation =====================

    @Test
    @DisplayName("deleteRelation 正常删除")
    void delete_ok() {
        service.deleteRelation(5L);
        verify(mapper).deleteById(5L);
    }

    @Test
    @DisplayName("deleteRelation 不存在抛异常")
    void delete_null() {
        when(mapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.deleteRelation(5L));
    }

    // ===================== getGraphData =====================

    @Test
    @DisplayName("getGraphData 有知识点则构建节点与边")
    void graph_withPoints() {
        KnowledgePointRelation rel = new KnowledgePointRelation();
        rel.setSourceId(1L);
        rel.setTargetId(2L);
        rel.setStrength(new BigDecimal("0.3"));
        rel.setRelationType("prereq");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(rel));

        Map<String, Object> g = service.getGraphData(10L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) g.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> links = (List<Map<String, Object>>) g.get("links");
        assertEquals(1, nodes.size());
        assertEquals(1, links.size());
        assertEquals("函数", nodes.get(0).get("name"));
    }

    @Test
    @DisplayName("getGraphData 无知识点则节点边皆空")
    void graph_noPoints() {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());
        Map<String, Object> g = service.getGraphData(10L);
        @SuppressWarnings("unchecked")
        List<?> nodes = (List<?>) g.get("nodes");
        @SuppressWarnings("unchecked")
        List<?> links = (List<?>) g.get("links");
        assertTrue(nodes.isEmpty());
        assertTrue(links.isEmpty());
    }

    // ===================== getRelationList =====================

    @Test
    @DisplayName("getRelationList 无课程筛选直接分页")
    void list_noCourse() {
        Page<KnowledgePointRelation> r = service.getRelationList(1, 10, null);
        assertEquals(1, r.getRecords().size());
    }

    @Test
    @DisplayName("getRelationList 有课程且有知识点")
    void list_withCourse() {
        Page<KnowledgePointRelation> r = service.getRelationList(1, 10, 10L);
        assertEquals(1, r.getRecords().size());
    }

    @Test
    @DisplayName("getRelationList 有课程但无知识点返回空页")
    void list_withCourseNoPoints() {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());
        Page<KnowledgePointRelation> r = service.getRelationList(1, 10, 10L);
        assertTrue(r.getRecords().isEmpty());
    }

    // ===================== source/target relations =====================

    @Test
    @DisplayName("getSourceRelations 返回出边")
    void src_ok() {
        assertEquals(1, service.getSourceRelations(1L).size());
    }

    @Test
    @DisplayName("getTargetRelations 返回入边")
    void tgt_ok() {
        assertEquals(1, service.getTargetRelations(2L).size());
    }
}
