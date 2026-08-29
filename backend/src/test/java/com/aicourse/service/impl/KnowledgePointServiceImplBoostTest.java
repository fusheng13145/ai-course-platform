package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 纯 Mockito 单元测试：补充覆盖 KnowledgePointServiceImpl 的知识树构建、依赖查询、
 * 校验、筛选与掌握度计算等分支（与 KnowledgePointServiceTest 组合提升指令覆盖率）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgePointServiceImplBoostTest {

    @Mock
    private KnowledgePointMapper knowledgePointMapper;
    @Mock
    private KnowledgePointRelationService relationService;
    @Mock
    private QuestionService questionService;
    @Mock
    private WrongQuestionService wrongQuestionService;

    private KnowledgePointServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), KnowledgePoint.class);
        service = new KnowledgePointServiceImpl(relationService, questionService, wrongQuestionService);
        ReflectionTestUtils.setField(service, "baseMapper", knowledgePointMapper);
    }

    @Test
    @DisplayName("getKnowledgeTree 空列表")
    void getKnowledgeTree_empty() {
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        assertTrue(service.getKnowledgeTree(1L).isEmpty());
    }

    @Test
    @DisplayName("getKnowledgeTree 构建父子层级")
    void getKnowledgeTree_build() {
        KnowledgePoint root = new KnowledgePoint();
        root.setId(1L);
        root.setName("根");
        root.setCourseId(1L);
        root.setParentId(null);
        KnowledgePoint child = new KnowledgePoint();
        child.setId(2L);
        child.setName("子");
        child.setCourseId(1L);
        child.setParentId(1L);
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(root, child));

        List<Map<String, Object>> tree = service.getKnowledgeTree(1L);
        assertEquals(1, tree.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) tree.get(0).get("children");
        assertEquals(1, children.size());
        assertEquals(2L, children.get(0).get("id"));
    }

    @Test
    @DisplayName("getChildPoints 返回子节点")
    void getChildPoints_ok() {
        KnowledgePoint p = new KnowledgePoint();
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(p));
        assertEquals(1, service.getChildPoints(1L).size());
    }

    @Test
    @DisplayName("getPrerequisites 无关联返回空")
    void getPrerequisites_empty() {
        when(relationService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        assertTrue(service.getPrerequisites(1L).isEmpty());
    }

    @Test
    @DisplayName("getPrerequisites 有关联返回前置知识点")
    void getPrerequisites_ok() {
        KnowledgePointRelation rel = new KnowledgePointRelation();
        rel.setSourceId(10L);
        rel.setTargetId(1L);
        rel.setRelationType("PREREQUISITE");
        when(relationService.list(any(Wrapper.class))).thenReturn(List.of(rel));
        KnowledgePoint p = new KnowledgePoint();
        when(knowledgePointMapper.selectBatchIds(any())).thenReturn(List.of(p));
        assertEquals(1, service.getPrerequisites(1L).size());
    }

    @Test
    @DisplayName("getSubsequentPoints 无关联返回空")
    void getSubsequentPoints_empty() {
        when(relationService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        assertTrue(service.getSubsequentPoints(1L).isEmpty());
    }

    @Test
    @DisplayName("getSubsequentPoints 有关联返回后续知识点")
    void getSubsequentPoints_ok() {
        KnowledgePointRelation rel = new KnowledgePointRelation();
        rel.setSourceId(1L);
        rel.setTargetId(20L);
        rel.setRelationType("PREREQUISITE");
        when(relationService.list(any(Wrapper.class))).thenReturn(List.of(rel));
        KnowledgePoint p = new KnowledgePoint();
        when(knowledgePointMapper.selectBatchIds(any())).thenReturn(List.of(p));
        assertEquals(1, service.getSubsequentPoints(1L).size());
    }

    @Test
    @DisplayName("createWithValidation 名称为 null 抛异常")
    void create_nameNull() {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setCourseId(1L);
        assertThrows(BusinessException.class, () -> service.createWithValidation(kp));
    }

    @Test
    @DisplayName("createWithValidation 名称为空白抛异常")
    void create_nameBlank() {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setName("   ");
        kp.setCourseId(1L);
        assertThrows(BusinessException.class, () -> service.createWithValidation(kp));
    }

    @Test
    @DisplayName("createWithValidation 课程ID为 null 抛异常")
    void create_courseNull() {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setName("xxx");
        assertThrows(BusinessException.class, () -> service.createWithValidation(kp));
    }

    @Test
    @DisplayName("createWithValidation 难度等级非法抛异常")
    void create_difficultyInvalid() {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setName("xxx");
        kp.setCourseId(1L);
        kp.setDifficultyLevel("SUPER");
        assertThrows(BusinessException.class, () -> service.createWithValidation(kp));
    }

    @Test
    @DisplayName("createWithValidation 难度等级小写转大写")
    void create_difficultyLower() {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setName("xxx");
        kp.setCourseId(1L);
        kp.setDifficultyLevel("easy");
        when(knowledgePointMapper.insert(any(KnowledgePoint.class))).thenReturn(1);
        KnowledgePoint r = service.createWithValidation(kp);
        assertEquals("EASY", r.getDifficultyLevel());
    }

    @Test
    @DisplayName("createWithValidation 重要度越界抛异常")
    void create_importanceOutOfRange() {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setName("xxx");
        kp.setCourseId(1L);
        kp.setDifficultyLevel("HARD");
        kp.setImportance(9);
        assertThrows(BusinessException.class, () -> service.createWithValidation(kp));
    }

    @Test
    @DisplayName("createWithValidation 重要度为 null 默认 3")
    void create_importanceNullDefault() {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setName("xxx");
        kp.setCourseId(1L);
        when(knowledgePointMapper.insert(any(KnowledgePoint.class))).thenReturn(1);
        KnowledgePoint r = service.createWithValidation(kp);
        assertEquals(3, r.getImportance());
    }

    @Test
    @DisplayName("createWithValidation 合法保存")
    void create_valid() {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setName("xxx");
        kp.setCourseId(1L);
        kp.setDifficultyLevel("MEDIUM");
        kp.setImportance(4);
        when(knowledgePointMapper.insert(any(KnowledgePoint.class))).thenReturn(1);
        KnowledgePoint r = service.createWithValidation(kp);
        assertEquals("MEDIUM", r.getDifficultyLevel());
        assertEquals(4, r.getImportance());
    }

    @Test
    @DisplayName("getListWithFilters 默认排序（无筛选）")
    void listFilters_default() {
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new KnowledgePoint()));
        assertEquals(1, service.getListWithFilters(1L, null, null, null, null, true).size());
    }

    @Test
    @DisplayName("getListWithFilters 难度筛选 + 重要度范围")
    void listFilters_difficultyImportance() {
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new KnowledgePoint()));
        assertEquals(1, service.getListWithFilters(1L, "HARD", 2, 4, null, true).size());
    }

    @Test
    @DisplayName("getListWithFilters 按 importance 升序")
    void listFilters_sortImportanceAsc() {
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new KnowledgePoint()));
        assertEquals(1, service.getListWithFilters(1L, null, null, null, "importance", true).size());
    }

    @Test
    @DisplayName("getListWithFilters 按 importance 降序")
    void listFilters_sortImportanceDesc() {
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new KnowledgePoint()));
        assertEquals(1, service.getListWithFilters(1L, null, null, null, "importance", false).size());
    }

    @Test
    @DisplayName("getListWithFilters 按 difficultyLevel 升序")
    void listFilters_sortDifficultyAsc() {
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new KnowledgePoint()));
        assertEquals(1, service.getListWithFilters(1L, null, null, null, "difficultyLevel", true).size());
    }

    @Test
    @DisplayName("getListWithFilters 按 difficultyLevel 降序")
    void listFilters_sortDifficultyDesc() {
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new KnowledgePoint()));
        assertEquals(1, service.getListWithFilters(1L, null, null, null, "difficultyLevel", false).size());
    }

    @Test
    @DisplayName("calculateMastery 无关联题目返回 0")
    void mastery_noQuestions() {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setId(1L);
        kp.setName("函数");
        when(knowledgePointMapper.selectById(1L)).thenReturn(kp);
        when(questionService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        assertEquals(0, BigDecimal.ZERO.compareTo(service.calculateMastery(1L, 2L)));
    }

    @Test
    @DisplayName("calculateMastery 有关联题目但无错题返回 100")
    void mastery_noWrong() {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setId(1L);
        kp.setName("函数");
        when(knowledgePointMapper.selectById(1L)).thenReturn(kp);
        Question q = new Question();
        q.setId(10L);
        q.setKnowledgePoints("函数");
        when(questionService.list(any(Wrapper.class))).thenReturn(List.of(q));
        when(wrongQuestionService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        assertEquals(0, BigDecimal.valueOf(100.00).compareTo(service.calculateMastery(1L, 2L)));
    }

    @Test
    @DisplayName("calculateMastery 部分掌握计算比例")
    void mastery_partial() {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setId(1L);
        kp.setName("函数");
        when(knowledgePointMapper.selectById(1L)).thenReturn(kp);
        Question q = new Question();
        q.setId(10L);
        q.setKnowledgePoints("函数");
        when(questionService.list(any(Wrapper.class))).thenReturn(List.of(q));

        WrongQuestion wq = new WrongQuestion();
        wq.setId(5L);
        wq.setQuestionId(10L);
        wq.setStudentId(2L);
        wq.setMastered(true); // 已掌握
        when(wrongQuestionService.list(any(Wrapper.class))).thenReturn(List.of(wq));

        BigDecimal r = service.calculateMastery(1L, 2L);
        assertTrue(r.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("calculateMastery 按知识点名称精确匹配（FIND_IN_SET），非 ID 子串")
    void mastery_matchesByNameNotById() {
        // 回归锁定：knowledgePoints 存名称列表，须按名称 FIND_IN_SET 精确匹配，
        // 不得再用 pointId（数字）做 LIKE 子串匹配（1 会误命中 12/11）
        KnowledgePoint kp = new KnowledgePoint();
        kp.setId(1L);
        kp.setName("函数");
        when(knowledgePointMapper.selectById(1L)).thenReturn(kp);
        when(questionService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

        service.calculateMastery(1L, 2L);

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(questionService).list(captor.capture());
        var wrapper = captor.getValue();
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("FIND_IN_SET"), "应按名称精确匹配，SQL 应含 FIND_IN_SET，实际: " + sql);
        @SuppressWarnings("unchecked")
        Map<String, Object> params =
                ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs();
        assertTrue(params.containsValue("函数"), "FIND_IN_SET 参数应为知识点名称，实际参数: " + params);
    }

    @Test
    @DisplayName("getRootPoints 返回根节点")
    void getRootPoints_ok() {
        when(knowledgePointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new KnowledgePoint()));
        assertEquals(1, service.getRootPoints(1L).size());
    }

    @Test
    @DisplayName("save/updateById/removeById 覆写委托")
    void crud_overrides() {
        KnowledgePoint p = new KnowledgePoint();
        when(knowledgePointMapper.insert(any(KnowledgePoint.class))).thenReturn(1);
        when(knowledgePointMapper.updateById(any(KnowledgePoint.class))).thenReturn(1);
        when(knowledgePointMapper.deleteById(any(Serializable.class))).thenReturn(1);
        assertTrue(service.save(p));
        assertTrue(service.updateById(p));
        assertTrue(service.removeById(9L));
    }
}
