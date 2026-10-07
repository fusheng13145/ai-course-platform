package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.mapper.WrongQuestionMapper;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.WrongQuestionGroupVO;
import com.aicourse.vo.WrongQuestionStatsVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 错题本服务补充分支覆盖测试（CoverageTest 命名惯例，自包含）。
 *
 * <p>既有 {@code WrongQuestionServiceImplTest} 未触达的方法：归属校验（IDOR 修复）、
 * 练习次数自增、按知识点分组、错因填写、派生字段补全的空集合兜底，以及
 * {@code extractKnowledgePointNames} 的 JSON/分隔符/未知知识点三条解析路径。
 * 本类按源码分支逐条命中，并用 wrapper 参数捕获验证「只查本学生 / 只选练习不足的题」
 * 这类无法从返回值观察的条件。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WrongQuestionServiceImplCoverageTest {

    @Mock private QuestionService questionService;
    @Mock private WrongQuestionMapper wrongQuestionMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;

    private WrongQuestionServiceImpl wrongQuestionService;

    @BeforeEach
    void setUp() {
        wrongQuestionService = new WrongQuestionServiceImpl(questionService, knowledgePointMapper);
        ReflectionTestUtils.setField(wrongQuestionService, "baseMapper", wrongQuestionMapper);
        // 注册 Question 的 TableInfo：wrapper 的 lambda 列名与条件参数在 getSqlSegment()
        // 时才落地，供 wrapperParams(...) 断言「只选练习不足的题 / 排除已掌握题」。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Question.class);
    }

    // ===================== recordWrongAnswer：practiceCount 为 null 的兜底 =====================

    @Test
    @DisplayName("记录错题 - 已有记录但 practiceCount 为 null 时从 1 起算并刷新快照")
    void recordWrongAnswer_existingWithoutPracticeCount() {
        WrongQuestion existing = new WrongQuestion();
        existing.setId(7L);
        existing.setStudentId(100L);
        existing.setQuestionId(200L);
        existing.setPracticeCount(null);
        existing.setMastered(true);

        when(wrongQuestionMapper.selectOne(any(), anyBoolean())).thenReturn(existing);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);
        when(questionService.getById(200L)).thenReturn(question(200L, "题干X", "选项X", "B", "讲解X", "知识点X"));

        WrongQuestion result = wrongQuestionService.recordWrongAnswer(100L, 200L, 10L, "D", "EXAM");

        assertSame(existing, result);
        assertEquals("D", result.getWrongAnswer());
        assertEquals(1, result.getPracticeCount());
        assertFalse(result.getMastered());
        assertNotNull(result.getLastPracticedAt());
        // 快照刷新：题干/正确答案/解析由题库回填
        assertEquals("题干X", result.getQuestionContent());
        assertEquals("B", result.getCorrectAnswer());
        assertEquals("讲解X", result.getQuestionAnalysis());
        assertEquals("知识点X", result.getKnowledgePointName());
        verify(wrongQuestionMapper).updateById(existing);
        verify(wrongQuestionMapper, never()).insert(any(WrongQuestion.class));
    }

    // ===================== 归属校验（markMasteredForStudent / deleteForStudent） =====================

    @Test
    @DisplayName("标记已掌握（带归属校验）- 记录不存在抛 404 文案且不改库")
    void markMasteredForStudent_notFound() {
        when(wrongQuestionMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> wrongQuestionService.markMasteredForStudent(999L, 100L));
        assertEquals("错题记录不存在", ex.getMessage());
        assertEquals(400, ex.getCode());
        verify(wrongQuestionMapper, never()).updateById(any(WrongQuestion.class));
    }

    @Test
    @DisplayName("标记已掌握（带归属校验）- 调用方 studentId 为 null 视为无权")
    void markMasteredForStudent_nullCallerStudentId() {
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wrongQuestion(1L, 100L, 200L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> wrongQuestionService.markMasteredForStudent(1L, null));
        assertEquals(403, ex.getCode());
        assertEquals("无权操作该错题记录", ex.getMessage());
        verify(wrongQuestionMapper, never()).updateById(any(WrongQuestion.class));
    }

    @Test
    @DisplayName("标记已掌握（带归属校验）- 错题本身无 studentId 视为无权")
    void markMasteredForStudent_recordWithoutStudentId() {
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wrongQuestion(1L, null, 200L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> wrongQuestionService.markMasteredForStudent(1L, 100L));
        assertEquals(403, ex.getCode());
        verify(wrongQuestionMapper, never()).updateById(any(WrongQuestion.class));
    }

    @Test
    @DisplayName("标记已掌握（带归属校验）- 他人错题被拒绝（IDOR）")
    void markMasteredForStudent_foreignRecord() {
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wrongQuestion(1L, 100L, 200L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> wrongQuestionService.markMasteredForStudent(1L, 200L));
        assertEquals(403, ex.getCode());
        assertEquals("无权操作该错题记录", ex.getMessage());
        verify(wrongQuestionMapper, never()).updateById(any(WrongQuestion.class));
    }

    @Test
    @DisplayName("标记已掌握（带归属校验）- 本人错题成功落库")
    void markMasteredForStudent_owned() {
        WrongQuestion wq = wrongQuestion(1L, 100L, 200L);
        wq.setMastered(false);
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wq);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);

        wrongQuestionService.markMasteredForStudent(1L, 100L);

        assertTrue(wq.getMastered());
        verify(wrongQuestionMapper).updateById(wq);
    }

    @Test
    @DisplayName("删除错题 - 本人记录走 deleteById，他人记录被 403 拦截")
    void deleteForStudent_ownedAndForeign() {
        WrongQuestion owned = wrongQuestion(1L, 100L, 200L);
        when(wrongQuestionMapper.selectById(1L)).thenReturn(owned);
        wrongQuestionService.deleteForStudent(1L, 100L);
        verify(wrongQuestionMapper).deleteById(1L);

        when(wrongQuestionMapper.selectById(2L)).thenReturn(wrongQuestion(2L, 100L, 200L));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> wrongQuestionService.deleteForStudent(2L, 777L));
        assertEquals(403, ex.getCode());
        verify(wrongQuestionMapper, never()).deleteById(2L);

        when(wrongQuestionMapper.selectById(3L)).thenReturn(null);
        BusinessException missing = assertThrows(BusinessException.class,
                () -> wrongQuestionService.deleteForStudent(3L, 100L));
        assertEquals("错题记录不存在", missing.getMessage());
    }

    // ===================== unmarkMastered / incrementPracticeCount =====================

    @Test
    @DisplayName("取消掌握 - practiceCount 为 null 时从 1 起算")
    void unmarkMastered_nullPracticeCount() {
        WrongQuestion wq = wrongQuestion(1L, 100L, 200L);
        wq.setMastered(true);
        wq.setPracticeCount(null);
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wq);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);

        wrongQuestionService.unmarkMastered(1L);

        assertFalse(wq.getMastered());
        assertEquals(1, wq.getPracticeCount());
        assertNotNull(wq.getLastPracticedAt());
    }

    @Test
    @DisplayName("练习次数自增 - 记录不存在抛异常")
    void incrementPracticeCount_notFound() {
        when(wrongQuestionMapper.selectById(9L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> wrongQuestionService.incrementPracticeCount(9L));
        assertEquals("错题记录不存在", ex.getMessage());
        verify(wrongQuestionMapper, never()).updateById(any(WrongQuestion.class));
    }

    @Test
    @DisplayName("练习次数自增 - null 兜底为 1，已有值累加")
    void incrementPracticeCount_nullAndExistingCount() {
        WrongQuestion nullCount = wrongQuestion(1L, 100L, 200L);
        nullCount.setPracticeCount(null);
        WrongQuestion hasCount = wrongQuestion(2L, 100L, 200L);
        hasCount.setPracticeCount(5);
        when(wrongQuestionMapper.selectById(1L)).thenReturn(nullCount);
        when(wrongQuestionMapper.selectById(2L)).thenReturn(hasCount);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);

        wrongQuestionService.incrementPracticeCount(1L);
        wrongQuestionService.incrementPracticeCount(2L);

        assertEquals(1, nullCount.getPracticeCount());
        assertEquals(6, hasCount.getPracticeCount());
        assertNotNull(hasCount.getLastPracticedAt());
        verify(wrongQuestionMapper, times(2)).updateById(any(WrongQuestion.class));
    }

    // ===================== 派生字段补全的空集合兜底 =====================

    @Test
    @DisplayName("错题本分页 - 结果记录为 null 时跳过派生字段补全且不查题库")
    @SuppressWarnings("unchecked")
    void getWrongQuestionBook_nullRecords() {
        Page<WrongQuestion> mockPage = new Page<>(1, 10);
        mockPage.setRecords(null);
        when(wrongQuestionMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        Page<WrongQuestion> result = wrongQuestionService.getWrongQuestionBook(100L, null, 1, 10);

        assertNull(result.getRecords());
        verify(questionService, never()).getById(any(Serializable.class));
    }

    // ===================== getByKnowledgePoints + extractKnowledgePointNames =====================

    @Test
    @DisplayName("按知识点分组 - 无错题返回空列表且不回填知识点ID")
    void getByKnowledgePoints_empty() {
        when(wrongQuestionMapper.selectList(any())).thenReturn(new ArrayList<>());

        List<WrongQuestionGroupVO> result = wrongQuestionService.getByKnowledgePoints(100L, null);

        assertTrue(result.isEmpty());
        verify(knowledgePointMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("按知识点分组 - JSON/分隔符/坏JSON/空白/无题目全部解析路径与按数量降序")
    void getByKnowledgePoints_parsesAllKnowledgePointFormats() {
        // wq1: JSON 数组含空串与 null -> ["A","B"]
        // wq2: 空 JSON 数组 -> 回落分隔符拆分得到字面量 "[]"
        // wq3: 中文逗号+分号分隔文本（含连续分隔符产生的空段）-> 三个知识点
        // wq4: 题目不存在 -> 未知知识点
        // wq5: knowledgePoints 为 null -> 未知知识点
        // wq6: knowledgePoints 为空白 -> 未知知识点
        // wq7: 只有分隔符 -> 拆分后为空 -> 未知知识点
        // wq8: 以 [ 开头但非法 JSON -> 异常回落拆分
        List<WrongQuestion> rows = new ArrayList<>(Arrays.asList(
                wrongQuestion(1L, 100L, 201L), wrongQuestion(2L, 100L, 202L),
                wrongQuestion(3L, 100L, 203L), wrongQuestion(4L, 100L, 204L),
                wrongQuestion(5L, 100L, 205L), wrongQuestion(6L, 100L, 206L),
                wrongQuestion(7L, 100L, 207L), wrongQuestion(8L, 100L, 208L)));
        when(wrongQuestionMapper.selectList(any())).thenReturn(rows);

        when(questionService.getById(201L)).thenReturn(
                question(201L, "题干1", null, null, null, "[\"A\",\"\",null,\"  B  \"]"));
        when(questionService.getById(202L)).thenReturn(question(202L, "题干2", null, null, null, "[]"));
        when(questionService.getById(203L)).thenReturn(question(203L, "题干3", null, null, null, "计算机网络，,数据结构;算法"));
        // 204 -> 未打桩，返回 null
        when(questionService.getById(205L)).thenReturn(question(205L, "题干5", null, null, null, null));
        when(questionService.getById(206L)).thenReturn(question(206L, "题干6", null, null, null, "   "));
        when(questionService.getById(207L)).thenReturn(question(207L, "题干7", null, null, null, ",,,"));
        when(questionService.getById(208L)).thenReturn(question(208L, "题干8", null, null, null, "[坏JSON"));

        when(knowledgePointMapper.selectList(any())).thenReturn(new ArrayList<>(Arrays.asList(
                knowledgePoint(1L, "A"), knowledgePoint(99L, "A"), knowledgePoint(2L, "B"),
                knowledgePoint(3L, "未出现在分组中的名字"))));

        List<WrongQuestionGroupVO> result = wrongQuestionService.getByKnowledgePoints(100L, 10L);

        assertEquals(8, result.size());
        // 四条无法解析的记录归入同一组，按 count 降序排在最前
        WrongQuestionGroupVO first = result.get(0);
        assertEquals("未知知识点", first.getKnowledgePointName());
        assertEquals(4, first.getCount());
        assertEquals(4, first.getQuestions().size());
        assertNull(first.getKnowledgePointId());

        WrongQuestionGroupVO groupA = result.stream()
                .filter(g -> "A".equals(g.getKnowledgePointName())).findFirst().orElseThrow();
        assertEquals(1L, groupA.getKnowledgePointId());
        assertEquals(1, groupA.getCount());
        // trim 后空串被丢弃，"  B  " 归一化为 "B"
        WrongQuestionGroupVO groupB = result.stream()
                .filter(g -> "B".equals(g.getKnowledgePointName())).findFirst().orElseThrow();
        assertEquals(2L, groupB.getKnowledgePointId());
        // 坏 JSON 保留原始字面量，且未命中 knowledge_points 表 -> ID 为 null
        assertTrue(result.stream().anyMatch(g -> "[坏JSON".equals(g.getKnowledgePointName())));
        assertTrue(result.stream().anyMatch(g -> "[]".equals(g.getKnowledgePointName())
                && g.getKnowledgePointId() == null));
        // 一道题多知识点会同时出现在多个分组
        assertSame(rows.get(0), groupA.getQuestions().get(0));
        // 派生字段在分组时被补齐
        assertEquals("题干1", rows.get(0).getQuestionContent());
        assertTrue(result.stream().anyMatch(g -> "算法".equals(g.getKnowledgePointName())));
        verify(knowledgePointMapper).selectList(any());
    }

    @Test
    @DisplayName("按知识点分组 - 知识点表无匹配记录时分组 ID 为 null")
    void getByKnowledgePoints_noKnowledgePointRows() {
        when(wrongQuestionMapper.selectList(any()))
                .thenReturn(new ArrayList<>(Collections.singletonList(wrongQuestion(1L, 100L, 201L))));
        when(questionService.getById(201L)).thenReturn(question(201L, "题干1", null, null, null, "数据结构"));
        when(knowledgePointMapper.selectList(any())).thenReturn(new ArrayList<>());

        List<WrongQuestionGroupVO> result = wrongQuestionService.getByKnowledgePoints(100L, null);

        assertEquals(1, result.size());
        assertEquals("数据结构", result.get(0).getKnowledgePointName());
        assertNull(result.get(0).getKnowledgePointId());
        assertEquals(1, result.get(0).getCount());
    }

    // ===================== getWrongQuestionStats =====================

    @Test
    @DisplayName("错题统计 - courseId 为 null 且含来源/错因/练习次数缺失的记录")
    void getWrongQuestionStats_nullCourseIdAndMissingFields() {
        WrongQuestion sparse = new WrongQuestion();
        sparse.setMastered(true);
        sparse.setSourceType(null);
        sparse.setReason("");
        sparse.setPracticeCount(null);

        WrongQuestion full = new WrongQuestion();
        full.setMastered(false);
        full.setSourceType("EXAM");
        full.setReason("概念不清");
        full.setPracticeCount(3);

        when(wrongQuestionMapper.selectCount(any())).thenReturn(2L, 1L);
        when(wrongQuestionMapper.selectList(any())).thenReturn(Arrays.asList(sparse, full));

        WrongQuestionStatsVO stats = wrongQuestionService.getWrongQuestionStats(100L, null);

        assertEquals(2L, stats.getTotal());
        assertEquals(1L, stats.getMastered());
        assertEquals(1L, stats.getUnmastered());
        assertEquals(50.0, stats.getMasteryRate());
        // sourceType 为 null 归入 OTHER；reason 为空串不进入错因分布
        assertEquals(1L, stats.getSourceTypeStats().get("OTHER"));
        assertEquals(1L, stats.getSourceTypeStats().get("EXAM"));
        assertEquals(1, stats.getReasonDistribution().size());
        assertEquals(1L, stats.getReasonDistribution().get("概念不清"));
        // practiceCount null 计入 neverOrOnce，3 次计入 multipleTimes
        assertEquals(1L, stats.getPracticeDistribution().get("neverOrOnce"));
        assertEquals(1L, stats.getPracticeDistribution().get("multipleTimes"));
    }

    @Test
    @DisplayName("错题统计 - courseId 非空且全部无错因、练习次数为 1")
    void getWrongQuestionStats_withCourseIdAndOnlyOncePracticed() {
        WrongQuestion wq = new WrongQuestion();
        wq.setMastered(false);
        wq.setSourceType("PRACTICE");
        wq.setReason(null);
        wq.setPracticeCount(1);
        when(wrongQuestionMapper.selectCount(any())).thenReturn(1L, 0L);
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.singletonList(wq));

        WrongQuestionStatsVO stats = wrongQuestionService.getWrongQuestionStats(100L, 10L);

        assertEquals(1L, stats.getTotal());
        assertEquals(0L, stats.getMastered());
        assertEquals(0.0, stats.getMasteryRate());
        assertTrue(stats.getReasonDistribution().isEmpty());
        assertEquals(1L, stats.getPracticeDistribution().get("neverOrOnce"));
        assertEquals(0L, stats.getPracticeDistribution().get("multipleTimes"));
        assertEquals(1L, stats.getSourceTypeStats().get("PRACTICE"));
    }

    // ===================== generatePracticePaper =====================

    @Test
    @DisplayName("生成练习卷 - 按练习次数升序取前 N 题（null 视作 0 优先）")
    void generatePracticePaper_sortsByPracticeCountAndTruncates() {
        WrongQuestion oftenPracticed = wrongQuestion(1L, 100L, 201L);
        oftenPracticed.setPracticeCount(5);
        WrongQuestion neverPracticed = wrongQuestion(2L, 100L, 202L);
        neverPracticed.setPracticeCount(null);
        WrongQuestion oncePracticed = wrongQuestion(3L, 100L, 203L);
        oncePracticed.setPracticeCount(1);
        when(wrongQuestionMapper.selectList(any()))
                .thenReturn(new ArrayList<>(Arrays.asList(oftenPracticed, neverPracticed, oncePracticed)));

        Question q202 = question(202L, "题干2", null, null, null, null);
        Question q203 = question(203L, "题干3", null, null, null, null);
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Arrays.asList(q202, q203)));

        List<Question> result = wrongQuestionService.generatePracticePaper(100L, 10L, 2);

        assertEquals(2, result.size());
        // count=0 -> 截取后无题目ID，直接返回空列表且不再查题库
        List<Question> none = wrongQuestionService.generatePracticePaper(100L, 10L, 0);
        assertTrue(none.isEmpty());

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(questionService, times(1)).list(captor.capture());
        Collection<Object> params = wrapperParams(captor.getValue());
        // 练习次数最少的两题（202/203）入选，练过 5 次的 201 被截断排除
        assertTrue(params.contains(202L), "应包含 202，实际=" + params);
        assertTrue(params.contains(203L), "应包含 203，实际=" + params);
        assertFalse(params.contains(201L), "不应包含练过 5 次的 201，实际=" + params);
    }

    // ===================== recommendRelatedQuestions =====================

    @Test
    @DisplayName("推荐相关题目 - 错题记录不存在抛异常")
    void recommendRelatedQuestions_wrongQuestionMissing() {
        when(wrongQuestionMapper.selectById(5L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> wrongQuestionService.recommendRelatedQuestions(5L));
        assertEquals("错题记录不存在", ex.getMessage());
        verify(questionService, never()).getById(any(Serializable.class));
    }

    @Test
    @DisplayName("推荐相关题目 - 原题目已被删除时返回空列表")
    void recommendRelatedQuestions_questionMissing() {
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wrongQuestion(1L, 100L, 200L));
        when(questionService.getById(200L)).thenReturn(null);

        List<Question> result = wrongQuestionService.recommendRelatedQuestions(1L);

        assertTrue(result.isEmpty());
        verify(wrongQuestionMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("推荐相关题目 - 原题目无知识点字段时返回空列表")
    void recommendRelatedQuestions_questionWithoutKnowledgePoints() {
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wrongQuestion(1L, 100L, 200L));
        when(questionService.getById(200L)).thenReturn(question(200L, "题干", null, null, null, null));

        List<Question> result = wrongQuestionService.recommendRelatedQuestions(1L);

        assertTrue(result.isEmpty());
        verify(wrongQuestionMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("推荐相关题目 - 排除已掌握与当前错题后按知识点检索")
    void recommendRelatedQuestions_excludesMasteredAndSelf() {
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wrongQuestion(1L, 100L, 200L));
        when(questionService.getById(200L)).thenReturn(question(200L, "题干", null, null, null, "Java基础"));
        when(wrongQuestionMapper.selectList(any()))
                .thenReturn(new ArrayList<>(Collections.singletonList(wrongQuestion(9L, 100L, 201L))));
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Collections.singletonList(question(300L, "相似题", null, null, null, "Java基础"))));

        List<Question> result = wrongQuestionService.recommendRelatedQuestions(1L);

        assertEquals(1, result.size());
        assertEquals(300L, result.get(0).getId());

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(questionService).list(captor.capture());
        Collection<Object> params = wrapperParams(captor.getValue());
        // 已掌握的 201 与当前错题 200 一并排除
        assertTrue(params.contains(201L), "应排除已掌握题目 201，实际=" + params);
        assertTrue(params.contains(200L), "应排除当前错题 200，实际=" + params);
        // like 条件由 MyBatis-Plus 包装成 %Java基础%
        assertTrue(params.stream().anyMatch(v -> v instanceof String s && s.contains("Java基础")),
                "应按知识点模糊匹配，实际=" + params);
    }

    // ===================== addWrongQuestion =====================

    @Test
    @DisplayName("添加错题 - 填写错因时回写 reason 并返回更新后的记录")
    void addWrongQuestion_withReasonUpdatesExisting() {
        WrongQuestion reread = new WrongQuestion();
        reread.setId(55L);
        reread.setStudentId(100L);
        reread.setQuestionId(200L);
        // recordWrongAnswer 查不到 -> 新建；addWrongQuestion 复查命中 -> 回写错因
        when(wrongQuestionMapper.selectOne(any(), anyBoolean())).thenReturn(null, reread);
        when(wrongQuestionMapper.insert(any(WrongQuestion.class))).thenReturn(1);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);

        WrongQuestion result = wrongQuestionService.addWrongQuestion(100L, 200L, 10L, "A", "MANUAL", "概念不清");

        assertSame(reread, result);
        assertEquals("概念不清", result.getReason());
        assertEquals(55L, result.getId());
        assertEquals(100L, result.getStudentId());
        verify(wrongQuestionMapper).insert(any(WrongQuestion.class));
        verify(wrongQuestionMapper).updateById(reread);
    }

    @Test
    @DisplayName("添加错题 - 错因为空串时不回写，直接返回收录结果")
    void addWrongQuestion_emptyReasonSkipsUpdate() {
        when(wrongQuestionMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(wrongQuestionMapper.insert(any(WrongQuestion.class))).thenReturn(1);

        WrongQuestion result = wrongQuestionService.addWrongQuestion(100L, 200L, 10L, "A", "MANUAL", "");

        assertEquals(100L, result.getStudentId());
        assertEquals(1, result.getPracticeCount());
        assertNull(result.getReason());
        verify(wrongQuestionMapper, never()).updateById(any(WrongQuestion.class));
    }

    @Test
    @DisplayName("添加错题 - 错因为 null 时跳过回写；复查落空时保留原结果")
    void addWrongQuestion_nullReasonAndRereadMiss() {
        when(wrongQuestionMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(wrongQuestionMapper.insert(any(WrongQuestion.class))).thenReturn(1);

        WrongQuestion result = wrongQuestionService.addWrongQuestion(100L, 200L, 10L, "A", "MANUAL", null);

        assertNull(result.getReason());
        verify(wrongQuestionMapper).insert(any(WrongQuestion.class));
        verify(wrongQuestionMapper, never()).updateById(any(WrongQuestion.class));
    }

    @Test
    @DisplayName("添加错题 - 已有记录且错因非空但复查为空时保留 recordWrongAnswer 结果")
    void addWrongQuestion_reasonProvidedButRereadMisses() {
        WrongQuestion existing = wrongQuestion(1L, 100L, 200L);
        existing.setPracticeCount(2);
        // 第一次命中（走更新分支），第二次复查落空 -> reason 不写入，返回更新后的原记录
        when(wrongQuestionMapper.selectOne(any(), anyBoolean())).thenReturn(existing, (WrongQuestion) null);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);

        WrongQuestion result = wrongQuestionService.addWrongQuestion(100L, 200L, 10L, "C", "PRACTICE", "粗心");

        assertSame(existing, result);
        assertEquals(3, result.getPracticeCount());
        assertNull(result.getReason());
        verify(wrongQuestionMapper, times(1)).updateById(any(WrongQuestion.class));
        verify(wrongQuestionMapper, never()).insert(any(WrongQuestion.class));
    }

    // ===================== 测试数据与断言辅助 =====================

    /**
     * 取出 wrapper 的实际查询条件值。
     * <p>MyBatis-Plus 把 lambda 条件参数延迟到 {@code getSqlSegment()} 时才落入
     * {@code paramNameValuePairs}，故先触发构建再读取。</p>
     */
    private static Collection<Object> wrapperParams(Wrapper<?> wrapper) {
        assertNotNull(wrapper, "查询条件 wrapper 不得为 null");
        AbstractWrapper<?, ?, ?> aw = (AbstractWrapper<?, ?, ?>) wrapper;
        aw.getSqlSegment();
        return aw.getParamNameValuePairs().values();
    }

    private WrongQuestion wrongQuestion(Long id, Long studentId, Long questionId) {
        WrongQuestion wq = new WrongQuestion();
        wq.setId(id);
        wq.setStudentId(studentId);
        wq.setQuestionId(questionId);
        wq.setCourseId(10L);
        wq.setMastered(false);
        wq.setPracticeCount(1);
        return wq;
    }

    private Question question(Long id, String content, String options, String correctAnswers,
                              String explanation, String knowledgePoints) {
        Question q = new Question();
        q.setId(id);
        q.setContent(content);
        q.setOptions(options);
        q.setCorrectAnswers(correctAnswers);
        q.setExplanation(explanation);
        q.setKnowledgePoints(knowledgePoints);
        q.setIsActive(true);
        q.setCourseId(10L);
        q.setScore(new BigDecimal("5"));
        return q;
    }

    private KnowledgePoint knowledgePoint(Long id, String name) {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setId(id);
        kp.setName(name);
        kp.setCourseId(10L);
        return kp;
    }
}
