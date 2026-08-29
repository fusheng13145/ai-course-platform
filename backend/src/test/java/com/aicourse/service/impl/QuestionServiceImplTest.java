package com.aicourse.service.impl;

import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.QuestionMapper;
import com.aicourse.service.AIService;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * QuestionServiceImpl 单元测试（B 主战场 P0，缺口 1017）。
 * 自身 extends ServiceImpl<QuestionMapper, Question>：手动构造 + 注入 baseMapper(mock)；
 * QuestionServiceImpl 构造器注入 @Lazy AIService（可 mock），但 objectMapper = new ObjectMapper()
 * （真实、不可注入）—— 因此 AI 生成/解析路径必须提供真实合法 JSON 字符串桩，不能 mock ObjectMapper。
 * 实体 lambda wrapper 涉及 Question → 注册 TableInfo；save/updateById/getById/page/list/listObjs/listByIds
 * 分别锚定到 baseMapper 的 insert/updateById/selectById/selectPage/selectList/selectObjs/selectBatchIds。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionServiceImplTest {

    @Mock private AIService aiService;
    @Mock private QuestionMapper questionMapper;

    @InjectMocks
    private QuestionServiceImpl service;

    private Question sample;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Question.class);

        service = new QuestionServiceImpl(aiService);
        ReflectionTestUtils.setField(service, "baseMapper", questionMapper);

        sample = q(100L, "SINGLE", new BigDecimal("5"), "函数");

        when(questionMapper.selectById(anyLong())).thenReturn(sample);
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(new ArrayList<>());
        when(questionMapper.selectObjs(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(questionMapper.insert(any(Question.class))).thenReturn(1);
        when(questionMapper.updateById(any(Question.class))).thenReturn(1);
    }

    private Question q(Long id, String type, BigDecimal score, String kp) {
        Question x = new Question();
        x.setId(id);
        x.setType(type);
        x.setContent("题干" + id);
        x.setOptions("[]");
        x.setCorrectAnswers("A");
        x.setDifficulty("MEDIUM");
        x.setScore(score);
        x.setKnowledgePoints(kp);
        x.setExplanation("解析");
        x.setUsageCount(0);
        x.setCorrectRate(BigDecimal.ZERO);
        x.setCourseId(10L);
        x.setIsActive(true);
        return x;
    }

    // ===================== createQuestion =====================

    @Test
    @DisplayName("createQuestion 默认值填充并落库")
    void create_okDefaults() {
        Question in = new Question();
        in.setContent("新问题");
        Question r = service.createQuestion(in);
        assertEquals(0, r.getUsageCount());
        assertEquals(BigDecimal.ZERO, r.getCorrectRate());
        assertTrue(r.getIsActive());
        verify(questionMapper).insert(any(Question.class));
    }

    @Test
    @DisplayName("createQuestion 保留传入默认值")
    void create_keepProvided() {
        Question in = new Question();
        in.setContent("x");
        in.setUsageCount(3);
        in.setCorrectRate(new BigDecimal("0.5"));
        in.setIsActive(false);
        Question r = service.createQuestion(in);
        assertEquals(3, r.getUsageCount());
        assertEquals(new BigDecimal("0.5"), r.getCorrectRate());
        assertFalse(r.getIsActive());
    }

    // ===================== generateByAI =====================

    @Test
    @DisplayName("generateByAI 解析 JSON 并落库")
    void ai_ok() {
        String json = "{\"content\":\"什么是函数?\",\"options\":[\"A\",\"B\"],\"correctAnswers\":\"A\",\"explanation\":\"说明\",\"score\":10}";
        when(aiService.generateQuestion(1L, "single", "easy", 1)).thenReturn(json);

        Question r = service.generateByAI(1L, "single", "easy", "函数");
        assertNotNull(r);
        assertEquals("什么是函数?", r.getContent());
        assertEquals("[\"A\",\"B\"]", r.getOptions());
        assertEquals("A", r.getCorrectAnswers());
        assertEquals("说明", r.getExplanation());
        assertEquals(new BigDecimal("10"), r.getScore());
        assertEquals("函数", r.getKnowledgePoints());
        assertEquals(BigDecimal.ZERO, r.getCorrectRate());
        verify(questionMapper).insert(any(Question.class));
    }

    @Test
    @DisplayName("generateByAI 缺省字段回退默认")
    void ai_defaults() {
        String json = "{\"content\":\"仅题干\"}";
        when(aiService.generateQuestion(2L, "single", "easy", 1)).thenReturn(json);

        Question r = service.generateByAI(2L, "single", "easy", "代数");
        assertEquals("仅题干", r.getContent());
        assertNull(r.getOptions());
        assertEquals("", r.getCorrectAnswers());
        assertEquals("", r.getExplanation());
        assertEquals(new BigDecimal("5"), r.getScore());
    }

    @Test
    @DisplayName("generateByAI AI 返回非法 JSON 抛业务异常")
    void ai_parseError() {
        when(aiService.generateQuestion(3L, "single", "easy", 1)).thenReturn("not-json");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateByAI(3L, "single", "easy", "x"));
        assertTrue(ex.getMessage().contains("解析失败"));
    }

    // ===================== generateBatchByAI =====================

    @Test
    @DisplayName("generateBatchByAI 解析 JSON 数组批量落库")
    void batch_array() {
        String json = "[{\"content\":\"q1\",\"options\":[\"A\"],\"correctAnswers\":\"A\",\"score\":5},"
                + "{\"content\":\"q2\",\"options\":[\"B\"],\"correctAnswers\":\"B\",\"score\":5}]";
        when(aiService.generateQuestion(1L, "single", "easy", 2)).thenReturn(json);

        List<Question> r = service.generateBatchByAI(1L, "single", "easy", "函数", 2, 99L);
        assertEquals(2, r.size());
        assertEquals("q1", r.get(0).getContent());
        assertEquals(99L, r.get(0).getCreatedBy());
        verify(questionMapper, times(2)).insert(any(Question.class));
    }

    @Test
    @DisplayName("generateBatchByAI 解析 {questions:[...]} 嵌套结构")
    void batch_nested() {
        String json = "{\"questions\":[{\"content\":\"q1\",\"score\":5},{\"content\":\"q2\",\"score\":5}]}";
        when(aiService.generateQuestion(1L, "single", "easy", 2)).thenReturn(json);

        List<Question> r = service.generateBatchByAI(1L, "single", "easy", "函数", 2, 99L);
        assertEquals(2, r.size());
    }

    @Test
    @DisplayName("generateBatchByAI 解析单对象包装为列表")
    void batch_single() {
        String json = "{\"content\":\"单题\",\"score\":5}";
        when(aiService.generateQuestion(1L, "single", "easy", 1)).thenReturn(json);

        List<Question> r = service.generateBatchByAI(1L, "single", "easy", "函数", 1, 99L);
        assertEquals(1, r.size());
        assertEquals("单题", r.get(0).getContent());
    }

    @Test
    @DisplayName("generateBatchByAI 非法 JSON 抛业务异常")
    void batch_error() {
        when(aiService.generateQuestion(1L, "single", "easy", 2)).thenReturn("garbage");
        assertThrows(BusinessException.class,
                () -> service.generateBatchByAI(1L, "single", "easy", "x", 2, 99L));
    }

    @Test
    @DisplayName("generateBatchByAI options 为字符串直接采用")
    void batch_optionsString() {
        String json = "[{\"content\":\"q1\",\"options\":\"A\",\"correctAnswers\":\"A\",\"score\":5}]";
        when(aiService.generateQuestion(1L, "single", "easy", 1)).thenReturn(json);

        List<Question> r = service.generateBatchByAI(1L, "single", "easy", "函数", 1, 99L);
        assertEquals("A", r.get(0).getOptions());
    }

    // ===================== batchImport =====================

    @Test
    @DisplayName("batchImport 跳过空白题干并设置默认")
    void batchImport_ok() {
        Question valid = new Question();
        valid.setContent("导入题");
        Question blank = new Question();
        blank.setContent("   ");
        Question partial = new Question();
        partial.setContent("带默认");
        partial.setType(""); // 触发默认 single
        partial.setDifficulty(""); // 触发默认 medium

        int n = service.batchImport(List.of(blank, valid, partial), 88L);
        assertEquals(2, n); // blank 被跳过
        assertEquals("single", partial.getType());
        assertEquals("medium", partial.getDifficulty());
        assertEquals(new BigDecimal("5"), partial.getScore());
        assertEquals(88L, partial.getCreatedBy());
        verify(questionMapper, times(2)).insert(any(Question.class));
    }

    @Test
    @DisplayName("batchImport 空列表返回 0")
    void batchImport_empty() {
        assertEquals(0, service.batchImport(new ArrayList<>(), 1L));
        verify(questionMapper, never()).insert(any(Question.class));
    }

    // ===================== reviewQuestion =====================

    @Test
    @DisplayName("reviewQuestion 通过置启用并更新")
    void review_pass() {
        service.reviewQuestion(100L, true);
        assertTrue(sample.getIsActive());
        verify(questionMapper).updateById(any(Question.class));
    }

    @Test
    @DisplayName("reviewQuestion 拒绝置禁用")
    void review_reject() {
        service.reviewQuestion(100L, false);
        assertFalse(sample.getIsActive());
        verify(questionMapper).updateById(any(Question.class));
    }

    @Test
    @DisplayName("reviewQuestion 题目不存在抛异常")
    void review_null() {
        when(questionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.reviewQuestion(1L, true));
    }

    // ===================== copyQuestion =====================

    @Test
    @DisplayName("copyQuestion 复制全部字段并重置统计")
    void copy_ok() {
        Question r = service.copyQuestion(100L);
        assertNotNull(r);
        assertNull(r.getId()); // 新 ID
        assertEquals(sample.getContent(), r.getContent());
        assertEquals(sample.getCourseId(), r.getCourseId());
        assertEquals(0, r.getUsageCount());
        assertEquals(BigDecimal.ZERO, r.getCorrectRate());
        assertTrue(r.getIsActive());
        verify(questionMapper).insert(any(Question.class));
    }

    @Test
    @DisplayName("copyQuestion 源题目不存在抛异常")
    void copy_null() {
        when(questionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.copyQuestion(1L));
    }

    // ===================== searchQuestions =====================

    @Test
    @DisplayName("searchQuestions 正常分页返回")
    void search_ok() {
        Question a = q(1L, "SINGLE", new BigDecimal("5"), "函数");
        Question b = q(2L, "SINGLE", new BigDecimal("5"), "函数");
        when(questionMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<Question> p = inv.getArgument(0);
            p.setRecords(List.of(a, b));
            p.setTotal(2);
            return p;
        });

        Page<Question> r = service.searchQuestions(10L, "SINGLE", null, "函数", 1, 10);
        assertEquals(2, r.getRecords().size());
        assertEquals(2, r.getTotal());
    }

    // ===================== getQuestionDetail =====================

    @Test
    @DisplayName("getQuestionDetail 解析选项 JSON")
    void detail_ok() {
        sample.setOptions("[{\"key\":\"A\",\"text\":\"选项A\"}]");
        Map<String, Object> r = service.getQuestionDetail(100L);
        assertEquals(100L, r.get("id"));
        assertTrue(r.get("options") instanceof List);
        assertTrue(r.containsKey("courseId")); // 详情仅含 id/type/content/.../courseId，无 knowledgePoints
    }

    @Test
    @DisplayName("getQuestionDetail 选项为空则不解析")
    void detail_noOptions() {
        sample.setOptions(null);
        Map<String, Object> r = service.getQuestionDetail(100L);
        assertFalse(r.containsKey("options"));
    }

    @Test
    @DisplayName("getQuestionDetail 选项非法 JSON 回退原字符串")
    void detail_badOptions() {
        sample.setOptions("破损的json");
        Map<String, Object> r = service.getQuestionDetail(100L);
        assertEquals("破损的json", r.get("options"));
    }

    @Test
    @DisplayName("getQuestionDetail 题目不存在抛异常")
    void detail_null() {
        when(questionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getQuestionDetail(1L));
    }

    // ===================== randomSelect =====================

    @Test
    @DisplayName("randomSelect count<=0 返回空")
    void random_nocount() {
        assertTrue(service.randomSelect(10L, "SINGLE", "easy", 0).isEmpty());
    }

    @Test
    @DisplayName("randomSelect 候选为空返回空")
    void random_emptyCandidates() {
        when(questionMapper.selectObjs(any(Wrapper.class))).thenReturn(new ArrayList<>());
        assertTrue(service.randomSelect(10L, "SINGLE", "easy", 5).isEmpty());
    }

    @Test
    @DisplayName("randomSelect 正常按 id 回查")
    void random_ok() {
        when(questionMapper.selectObjs(any(Wrapper.class))).thenReturn(List.of(101L, 102L));
        Question q1 = q(101L, "SINGLE", new BigDecimal("5"), "函数");
        Question q2 = q(102L, "SINGLE", new BigDecimal("5"), "函数");
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(new ArrayList<>(List.of(q1, q2)));

        List<Question> r = service.randomSelect(10L, "SINGLE", "easy", 5);
        assertEquals(2, r.size());
        verify(questionMapper).selectBatchIds(anyCollection());
    }

    // ===================== getByKnowledgePoint =====================

    @Test
    @DisplayName("getByKnowledgePoint like 命中返回列表")
    void byKp_ok() {
        Question a = q(1L, "SINGLE", new BigDecimal("5"), "函数");
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a));
        List<Question> r = service.getByKnowledgePoint(7L);
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("getByKnowledgePoint 无命中返回空")
    void byKp_none() {
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
        assertTrue(service.getByKnowledgePoint(7L).isEmpty());
    }

    // ===================== updateCorrectRate =====================

    @Test
    @DisplayName("updateCorrectRate 答对后正确率上升")
    void rate_correct() {
        sample.setUsageCount(0);
        sample.setCorrectRate(BigDecimal.ZERO);
        service.updateCorrectRate(100L, true);
        assertEquals(1, sample.getUsageCount());
        assertEquals(new BigDecimal("1.0000"), sample.getCorrectRate());
        verify(questionMapper).updateById(any(Question.class));
    }

    @Test
    @DisplayName("updateCorrectRate 答错后正确率下降")
    void rate_wrong() {
        sample.setUsageCount(1);
        sample.setCorrectRate(BigDecimal.ONE);
        service.updateCorrectRate(100L, false);
        assertEquals(2, sample.getUsageCount());
        // 原正确数 = 1*1 = 1，答错不变 -> 1/2 = 0.5
        assertEquals(new BigDecimal("0.5000"), sample.getCorrectRate());
    }

    @Test
    @DisplayName("updateCorrectRate 题目不存在抛异常")
    void rate_null() {
        when(questionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.updateCorrectRate(1L, true));
    }

    // ===================== getQuestionStats =====================

    @Test
    @DisplayName("getQuestionStats 返回统计字段")
    void stats_ok() {
        Map<String, Object> r = service.getQuestionStats(100L);
        assertEquals(100L, r.get("questionId"));
        assertEquals(sample.getUsageCount(), r.get("usageCount"));
        assertEquals("MEDIUM", r.get("difficulty"));
    }

    @Test
    @DisplayName("getQuestionStats 题目不存在抛异常")
    void stats_null() {
        when(questionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getQuestionStats(1L));
    }

    // ===================== getErrorProneQuestions =====================

    @Test
    @DisplayName("getErrorProneQuestions 正常 limit")
    void errorProne_ok() {
        Question a = q(1L, "SINGLE", new BigDecimal("5"), "函数");
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a));
        List<Question> r = service.getErrorProneQuestions(10L, 5);
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("getErrorProneQuestions 钳制负 limit 为 1")
    void errorProne_clampLow() {
        Question a = q(1L, "SINGLE", new BigDecimal("5"), "函数");
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a));
        List<Question> r = service.getErrorProneQuestions(10L, 0);
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("getErrorProneQuestions 钳制超大 limit 为 100")
    void errorProne_clampHigh() {
        Question a = q(1L, "SINGLE", new BigDecimal("5"), "函数");
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a));
        List<Question> r = service.getErrorProneQuestions(10L, 500);
        assertEquals(1, r.size());
    }
}
