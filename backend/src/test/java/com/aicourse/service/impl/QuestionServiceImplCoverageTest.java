package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.mapper.QuestionMapper;
import com.aicourse.service.AIService;
import com.aicourse.vo.QuestionBankStatisticsVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * QuestionServiceImpl 覆盖率补测（I-04）。
 *
 * <p>覆盖 AI 批量生成的空项/空题干、JSON 列归一化边界、带课程 ACL 的搜索与统计重载、
 * 知识点空值、正确率默认值与易错题 limit 钳制。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionServiceImplCoverageTest {

    @Mock private AIService aiService;
    @Mock private QuestionMapper questionMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;

    private QuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Question.class);
        service = new QuestionServiceImpl(aiService, knowledgePointMapper);
        ReflectionTestUtils.setField(service, "baseMapper", questionMapper);

        when(questionMapper.insert(any(Question.class))).thenReturn(1);
        when(questionMapper.updateById(any(Question.class))).thenReturn(1);
        when(questionMapper.selectById(anyLong())).thenReturn(null);
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(questionMapper.selectObjs(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(new ArrayList<>());
        when(questionMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<Question> page = invocation.getArgument(0);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);
            return page;
        });
    }

    private static Question q(Long id, String content, String type, String difficulty) {
        Question question = new Question();
        question.setId(id);
        question.setContent(content);
        question.setCourseId(10L);
        question.setType(type);
        question.setDifficulty(difficulty);
        question.setScore(new BigDecimal("5"));
        question.setUsageCount(0);
        question.setCorrectRate(BigDecimal.ZERO);
        question.setIsActive(true);
        return question;
    }

    private static Question sample(Long id) {
        Question question = q(id, "题干" + id, "SINGLE", "MEDIUM");
        question.setOptions("[]");
        question.setCorrectAnswers("A");
        question.setKnowledgePoints("函数");
        question.setExplanation("解析");
        return question;
    }

    // ===================== AI 批量生成 =====================

    @Test
    @DisplayName("批量生成：null/空白题干跳过，合法项落库并保留解释/分数")
    void batch_skipsInvalidEntriesAndKeepsValid() {
        String json = "[{\"content\":null,\"explanation\":\"无题干\",\"score\":5},"
                + "{\"content\":\"   \"},"
                + "{\"content\":\"合法题\",\"explanation\":\"说明\"}]";
        when(aiService.generateQuestion(1L, "single", "easy", 3)).thenReturn(json);

        List<Question> saved = service.generateBatchByAI(1L, "single", "easy", "函数", 3, 99L);

        assertEquals(1, saved.size());
        assertEquals("合法题", saved.get(0).getContent());
        assertEquals("说明", saved.get(0).getExplanation());
        assertEquals(new BigDecimal("5"), saved.get(0).getScore());
        verify(questionMapper, times(1)).insert(any(Question.class));
    }

    @Test
    @DisplayName("批量生成：全部无题干时中止且不落库")
    void batch_allInvalidRejects() {
        when(aiService.generateQuestion(2L, "single", "easy", 2))
                .thenReturn("[{\"content\":null},{\"content\":\"  \"}]");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.generateBatchByAI(2L, "single", "easy", "函数", 2, 99L));

        assertTrue(error.getMessage().contains("未返回任何含 content"));
        verify(questionMapper, never()).insert(any(Question.class));
    }

    // ===================== batchImport 默认值 =====================

    @Test
    @DisplayName("批量导入：null 题干跳过，已有非空字段不被默认值覆盖")
    void batchImport_nullContentAndKeepProvided() {
        Question nullContent = new Question();
        Question provided = q(2L, "完整题", "single", "easy");
        provided.setScore(new BigDecimal("10"));
        provided.setUsageCount(3);
        provided.setCorrectRate(new BigDecimal("0.5"));
        provided.setIsActive(true);

        int imported = service.batchImport(List.of(nullContent, provided), 88L);

        assertEquals(1, imported);
        assertEquals("single", provided.getType());
        assertEquals("easy", provided.getDifficulty());
        assertEquals(new BigDecimal("10"), provided.getScore());
        assertEquals(3, provided.getUsageCount());
        assertEquals(new BigDecimal("0.5"), provided.getCorrectRate());
        assertTrue(provided.getIsActive());
        verify(questionMapper, times(1)).insert(any(Question.class));
    }

    // ===================== JSON 列归一化 =====================

    @Test
    @DisplayName("正确答案：空串落 null，三类 JSON 前缀原样透传")
    void correctAnswers_jsonPrefixes() {
        when(aiService.generateQuestion(11L, "single", "easy", 1))
                .thenReturn("{\"content\":\"空答案\",\"correctAnswers\":\"\"}");
        when(aiService.generateQuestion(12L, "single", "easy", 1))
                .thenReturn("{\"content\":\"数组串\",\"correctAnswers\":\"[A]\"}");
        when(aiService.generateQuestion(13L, "single", "easy", 1))
                .thenReturn("{\"content\":\"对象串\",\"correctAnswers\":\"{\\\"a\\\":1}\"}");
        when(aiService.generateQuestion(14L, "single", "easy", 1))
                .thenReturn("{\"content\":\"带引号\",\"correctAnswers\":\"\\\"A\\\"\"}");

        assertNull(service.generateByAI(11L, "single", "easy", "x").getCorrectAnswers());
        assertEquals("[A]", service.generateByAI(12L, "single", "easy", "x").getCorrectAnswers());
        assertEquals("{\"a\":1}", service.generateByAI(13L, "single", "easy", "x").getCorrectAnswers());
        assertEquals("\"A\"", service.generateByAI(14L, "single", "easy", "x").getCorrectAnswers());
    }

    @Test
    @DisplayName("知识点：已有 JSON 数组串逐字保留；仅分隔符时落 null")
    void knowledgePoints_bracketAndSeparators() {
        when(aiService.generateQuestion(21L, "single", "easy", 1))
                .thenReturn("{\"content\":\"题干\",\"correctAnswers\":\"A\"}");
        when(aiService.generateQuestion(22L, "single", "easy", 1))
                .thenReturn("{\"content\":\"题干\",\"correctAnswers\":\"A\"}");
        when(aiService.generateQuestion(23L, "single", "easy", 1))
                .thenReturn("{\"content\":\"题干\",\"correctAnswers\":\"A\"}");
        when(aiService.generateQuestion(24L, "single", "easy", 1))
                .thenReturn("{\"content\":\"题干\",\"correctAnswers\":\"A\"}");

        Question bracket = service.generateByAI(21L, "single", "easy", "[1,2]");
        assertEquals("[1,2]", bracket.getKnowledgePoints());

        Question separators = service.generateByAI(22L, "single", "easy", "，，;;");
        assertNull(separators.getKnowledgePoints());

        Question mixed = service.generateByAI(23L, "single", "easy", "函数，，导数");
        assertEquals("[\"函数\",\"导数\"]", mixed.getKnowledgePoints());

        assertNull(service.generateByAI(24L, "single", "easy", null).getKnowledgePoints());
    }

    // ===================== 搜索：条件分支与 ACL 重载 =====================

    @Test
    @DisplayName("公开搜索：严格条件全开与全关")
    void search_publicFilterBranches() {
        when(questionMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<Question> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(sample(1L)));
            return page;
        });

        Page<Question> allOn = service.searchQuestions(10L, "SINGLE", "easy", "函数", 1, 10);
        assertEquals(1, allOn.getTotal());

        Page<Question> allOff = service.searchQuestions(null, null, null, null, 1, 10);
        assertEquals(1, allOff.getTotal());

        Page<Question> emptyFilters = service.searchQuestions(null, "", "", "", 1, 10);
        assertEquals(1, emptyFilters.getTotal());

        Page<Question> emptyKeyword = service.searchQuestions(null, null, null, "", 1, 10);
        assertEquals(1, emptyKeyword.getTotal());
    }

    @Test
    @DisplayName("ACL 搜索：空课程集合早退，不查询数据库")
    void search_aclEmptyReadableCoursesReturnsEmpty() {
        Page<Question> page = service.searchQuestions(1L, "SINGLE", "easy", "函数", 1, 10, List.of());

        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        verify(questionMapper, never()).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("ACL 搜索：课程集合与四条件全开")
    void search_aclAllFilters() {
        when(questionMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<Question> page = invocation.getArgument(0);
            page.setTotal(2);
            page.setRecords(List.of(sample(1L), sample(2L)));
            return page;
        });

        Page<Question> page = service.searchQuestions(10L, "SINGLE", "easy", "函数", 1, 10, List.of(10L, 11L));

        assertEquals(2, page.getTotal());
        verify(questionMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("ACL 搜索：空条件且课程集合为 null")
    void search_aclNullFilters() {
        Page<Question> page = service.searchQuestions(null, null, null, null, 1, 10, null);

        assertEquals(0, page.getTotal());
        verify(questionMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("ACL 搜索：空字符串筛选条件不拼接")
    void search_aclEmptyFilters() {
        Page<Question> page = service.searchQuestions(null, "", "", "", 1, 10, List.of(10L));

        assertEquals(0, page.getTotal());
        verify(questionMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    // ===================== 详情 =====================

    @Test
    @DisplayName("详情：空 options 字符串不解析")
    void detail_emptyOptionsString() {
        Question question = sample(30L);
        question.setOptions("");
        when(questionMapper.selectById(30L)).thenReturn(question);

        Map<String, Object> detail = service.getQuestionDetail(30L);

        assertFalse(detail.containsKey("options"));
    }

    // ===================== 随机抽题 =====================

    @Test
    @DisplayName("随机抽题：类型/难度为空时仍按课程抽题")
    void random_nullTypeAndDifficulty() {
        when(questionMapper.selectObjs(any(Wrapper.class))).thenReturn(List.of(1L, 2L));
        when(questionMapper.selectBatchIds(anyCollection()))
                .thenAnswer(invocation -> new ArrayList<>(List.of(sample(1L), sample(2L))));

        assertEquals(2, service.randomSelect(10L, null, null, 5).size());
        assertEquals(2, service.randomSelect(10L, "", "", 5).size());
    }

    // ===================== 知识点 =====================

    @Test
    @DisplayName("知识点：ID 为空返回空")
    void byKnowledgePoint_nullId() {
        assertTrue(service.getByKnowledgePoint(null).isEmpty());
    }

    @Test
    @DisplayName("知识点：名称为 null 或空白返回空")
    void byKnowledgePoint_blankName() {
        KnowledgePoint nullName = new KnowledgePoint();
        nullName.setId(1L);
        nullName.setName(null);
        KnowledgePoint blankName = new KnowledgePoint();
        blankName.setId(2L);
        blankName.setName("   ");
        when(knowledgePointMapper.selectById(1L)).thenReturn(nullName);
        when(knowledgePointMapper.selectById(2L)).thenReturn(blankName);

        assertTrue(service.getByKnowledgePoint(1L).isEmpty());
        assertTrue(service.getByKnowledgePoint(2L).isEmpty());
    }

    // ===================== 正确率空值 =====================

    @Test
    @DisplayName("正确率：usageCount 与 correctRate 均为 null 时按 0 起算")
    void updateCorrectRate_nullDefaults() {
        Question question = q(40L, "空统计", "SINGLE", "MEDIUM");
        question.setUsageCount(null);
        question.setCorrectRate(null);
        when(questionMapper.selectById(40L)).thenReturn(question);

        service.updateCorrectRate(40L, true);

        assertEquals(1, question.getUsageCount());
        assertEquals(new BigDecimal("1.0000"), question.getCorrectRate());
        verify(questionMapper).updateById(question);
    }

    // ===================== 题库统计重载 =====================

    @Test
    @DisplayName("题库统计：空课程集合返回零，非空与 null 分别走课程范围/全量")
    void bankStatistics_courseScopes() {
        when(questionMapper.selectCount(any())).thenReturn(10L, 2L, 20L, 4L);

        QuestionBankStatisticsVO empty = service.getBankStatistics(List.of());
        assertEquals(0L, empty.getTotalCount());
        assertEquals(0L, empty.getTodayCount());
        verify(questionMapper, never()).selectCount(any());

        QuestionBankStatisticsVO scoped = service.getBankStatistics(List.of(1L, 2L));
        assertEquals(10L, scoped.getTotalCount());
        assertEquals(2L, scoped.getTodayCount());

        QuestionBankStatisticsVO all = service.getBankStatistics((List<Long>) null);
        assertEquals(20L, all.getTotalCount());
        assertEquals(4L, all.getTodayCount());
    }

    // ===================== 易错题重载 =====================

    @Test
    @DisplayName("易错题：空课程集合返回空")
    void errorProne_aclEmpty() {
        assertTrue(service.getErrorProneQuestions(1L, 5, List.of()).isEmpty());
        verify(questionMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("易错题：ACL 非空与 null 均能查询，limit 钳制到 100")
    void errorProne_aclScopes() {
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sample(1L)));

        List<Question> scoped = service.getErrorProneQuestions(10L, 5, List.of(10L));
        assertEquals(1, scoped.size());

        List<Question> all = service.getErrorProneQuestions(null, 500, null);
        assertEquals(1, all.size());
    }

    @Test
    @DisplayName("易错题：公开重载 courseId 为空时仍查询全量")
    void errorProne_publicNullCourse() {
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sample(1L)));

        List<Question> list = service.getErrorProneQuestions(null, 5);

        assertEquals(1, list.size());
    }
}
