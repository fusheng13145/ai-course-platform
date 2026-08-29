package com.aicourse.service.impl;

import com.aicourse.dto.AutoComposeDTO;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamPaperMapper;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.QuestionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ExamPaperServiceImpl 单元测试（B 主战场 P1，缺口 1144）。
 * 自身 extends ServiceImpl<ExamPaperMapper, ExamPaper>：手动构造 + 注入 baseMapper(mock)；
 * 实体 lambda wrapper 涉及 ExamPaper/ExamQuestion/Question → 统一注册 TableInfo；
 * examQuestionService/questionService 均为 mock，其 list/getById 直接 stub；
 * selectById/selectList/insert/updateById 分别按需锚定。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamPaperServiceImplTest {

    @Mock private ExamQuestionService examQuestionService;
    @Mock private QuestionService questionService;
    @Mock private NotificationService notificationService;
    @Mock private ExamPaperMapper examPaperMapper;

    private ExamPaperServiceImpl service;

    private ExamPaper paper;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExamPaper.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExamQuestion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Question.class);

        service = new ExamPaperServiceImpl(examQuestionService, questionService, notificationService);
        ReflectionTestUtils.setField(service, "baseMapper", examPaperMapper);

        paper = new ExamPaper();
        paper.setId(1L); paper.setTitle("期中卷"); paper.setStatus("DRAFT");
        paper.setCourseId(10L); paper.setDescription("desc"); paper.setCreatorId(99L);
        paper.setTotalScore(new BigDecimal("100")); paper.setDuration(60);
        paper.setQuestionCount(0);

        when(examPaperMapper.selectById(anyLong())).thenReturn(paper);
        when(examPaperMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(examPaperMapper.insert(any(ExamPaper.class))).thenReturn(1);
        when(examPaperMapper.updateById(any(ExamPaper.class))).thenReturn(1);

        when(examQuestionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(examQuestionService.save(any(ExamQuestion.class))).thenReturn(true);
        when(questionService.getById(anyLong())).thenReturn(null);
        when(questionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        doNothing().when(notificationService).onEvent(anyString(), any());
    }

    private Question q(Long id, String type, BigDecimal score, String kp) {
        Question x = new Question();
        x.setId(id); x.setType(type); x.setContent("c" + id); x.setOptions("[]");
        x.setDifficulty("MEDIUM"); x.setScore(score); x.setKnowledgePoints(kp);
        x.setIsActive(true); x.setCourseId(10L);
        return x;
    }

    // ===================== getExamPaperDetail =====================

    @Test
    @DisplayName("getExamPaperDetail 正常返回题目列表")
    void detail_ok() {
        ExamQuestion eq = new ExamQuestion();
        eq.setId(5L); eq.setExamPaperId(1L); eq.setQuestionId(7L); eq.setOrderIndex(1); eq.setPoints(new BigDecimal("10"));
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(anyCollection())).thenReturn(List.of(q(7L, "SINGLE", new BigDecimal("10"), null)));

        Map<String, Object> r = service.getExamPaperDetail(1L);
        assertEquals(1, ((List<?>) r.get("questions")).size());
        assertEquals(1, r.get("questionCount"));
        assertNotNull(r.get("paper"));
    }

    @Test
    @DisplayName("getExamPaperDetail 试卷不存在抛异常")
    void detail_null() {
        when(examPaperMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getExamPaperDetail(1L));
    }

    @Test
    @DisplayName("getExamPaperDetail 题目缺失则跳过")
    void detail_skipNullQuestion() {
        ExamQuestion eq = new ExamQuestion();
        eq.setId(5L); eq.setQuestionId(7L);
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(anyCollection())).thenReturn(List.of()); // 题目查不到 -> 跳过
        Map<String, Object> r = service.getExamPaperDetail(1L);
        assertEquals(0, ((List<?>) r.get("questions")).size());
    }

    // ===================== publishExamPaper =====================

    @Test
    @DisplayName("publishExamPaper 正常发布并推送通知")
    void publish_ok() {
        paper.setStatus("DRAFT");
        service.publishExamPaper(1L);
        verify(examPaperMapper).updateById(any(ExamPaper.class));
        verify(notificationService).onEvent(eq("EXAM_PUBLISHED"), any());
    }

    @Test
    @DisplayName("publishExamPaper 试卷不存在抛异常")
    void publish_null() {
        when(examPaperMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.publishExamPaper(1L));
    }

    @Test
    @DisplayName("publishExamPaper 已发布抛异常")
    void publish_already() {
        paper.setStatus("PUBLISHED");
        assertThrows(BusinessException.class, () -> service.publishExamPaper(1L));
    }

    // ===================== copyExamPaper =====================

    @Test
    @DisplayName("copyExamPaper 正常复制并复制题目关联")
    void copy_ok() {
        ExamQuestion sq = new ExamQuestion();
        sq.setId(5L); sq.setExamPaperId(1L); sq.setQuestionId(7L); sq.setOrderIndex(0); sq.setPoints(new BigDecimal("10"));
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(sq));

        ExamPaper r = service.copyExamPaper(1L, "副本卷");
        assertNotNull(r);
        assertEquals("副本卷", r.getTitle());
        assertEquals(1, r.getQuestionCount());
        verify(examQuestionService).save(any(ExamQuestion.class));
        verify(examPaperMapper).updateById(any(ExamPaper.class));
    }

    @Test
    @DisplayName("copyExamPaper 不传新标题则用默认副本名")
    void copy_defaultTitle() {
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        ExamPaper r = service.copyExamPaper(1L, null);
        assertTrue(r.getTitle().contains("(副本)"));
        assertEquals(0, r.getQuestionCount());
    }

    @Test
    @DisplayName("copyExamPaper 源试卷不存在抛异常")
    void copy_null() {
        when(examPaperMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.copyExamPaper(1L, "x"));
    }

    // ===================== getPaperStatistics =====================

    @Test
    @DisplayName("getPaperStatistics 统计题型与分值分布")
    void stats_ok() {
        ExamQuestion eq1 = new ExamQuestion(); eq1.setQuestionId(1L); eq1.setPoints(new BigDecimal("3"));
        ExamQuestion eq2 = new ExamQuestion(); eq2.setQuestionId(2L); eq2.setPoints(new BigDecimal("8"));
        ExamQuestion eq3 = new ExamQuestion(); eq3.setQuestionId(3L); eq3.setPoints(new BigDecimal("15"));
        ExamQuestion eq4 = new ExamQuestion(); eq4.setQuestionId(4L); eq4.setPoints(new BigDecimal("30"));
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq1, eq2, eq3, eq4));
        when(questionService.listByIds(anyCollection())).thenReturn(List.of(
                q(1L, "SINGLE", new BigDecimal("3"), null),
                q(2L, "MULTI", new BigDecimal("8"), null),
                q(3L, "JUDGE", new BigDecimal("15"), null),
                q(4L, "ESSAY", new BigDecimal("30"), null)));

        Map<String, Object> r = service.getPaperStatistics(1L);
        assertEquals(4, r.get("questionCount"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> td = (Map<String, Integer>) r.get("typeDistribution");
        assertEquals(1, td.get("SINGLE"));
        assertEquals(1, td.get("MULTI"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> sd = (Map<String, Integer>) r.get("scoreDistribution");
        assertEquals(1, sd.get("1-5分"));
        assertEquals(1, sd.get("6-10分"));
        assertEquals(1, sd.get("11-20分"));
        assertEquals(1, sd.get("20分以上"));
        assertTrue(((BigDecimal) r.get("totalPoints")).compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("getPaperStatistics 试卷不存在抛异常")
    void stats_null() {
        when(examPaperMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getPaperStatistics(1L));
    }

    @Test
    @DisplayName("getPaperStatistics 题目缺失跳过不影响统计")
    void stats_skipNull() {
        ExamQuestion eq = new ExamQuestion(); eq.setQuestionId(1L); eq.setPoints(new BigDecimal("5"));
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(anyCollection())).thenReturn(List.of());
        Map<String, Object> r = service.getPaperStatistics(1L);
        assertEquals(1, r.get("questionCount"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> td = (Map<String, Integer>) r.get("typeDistribution");
        assertTrue(td.isEmpty());
    }

    // ===================== autoComposeExamPaper =====================

    private AutoComposeDTO baseDto() {
        AutoComposeDTO d = new AutoComposeDTO();
        d.setCourseId(10L);
        d.setTypeRules(Map.of("SINGLE", 2));
        return d;
    }

    @Test
    @DisplayName("autoCompose 未指定课程抛异常")
    void compose_noCourse() {
        AutoComposeDTO d = new AutoComposeDTO();
        assertThrows(BusinessException.class, () -> service.autoComposeExamPaper(d, 99L));
    }

    @Test
    @DisplayName("autoCompose 未设置题型规则抛异常")
    void compose_noRules() {
        AutoComposeDTO d = new AutoComposeDTO();
        d.setCourseId(10L);
        assertThrows(BusinessException.class, () -> service.autoComposeExamPaper(d, 99L));
    }

    @Test
    @DisplayName("autoCompose 题库为空抛异常")
    void compose_noQuestions() {
        when(questionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.autoComposeExamPaper(baseDto(), 99L));
    }

    @Test
    @DisplayName("autoCompose 正常组卷生成试卷与题目关联")
    void compose_ok() {
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(q(1L, "SINGLE", new BigDecimal("5"), null), q(2L, "SINGLE", new BigDecimal("5"), null)));
        ExamPaper r = service.autoComposeExamPaper(baseDto(), 99L);
        assertNotNull(r);
        assertEquals(2, r.getQuestionCount());
        verify(examPaperMapper).insert(any(ExamPaper.class));
        verify(examQuestionService, times(2)).save(any(ExamQuestion.class));
    }

    @Test
    @DisplayName("autoCompose 指定知识点优先选取")
    void compose_knowledgePoints() {
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(
                        q(1L, "SINGLE", new BigDecimal("5"), "函数"),
                        q(2L, "SINGLE", new BigDecimal("5"), "代数")));
        AutoComposeDTO d = baseDto();
        d.setKnowledgePointNames(List.of("函数"));
        ExamPaper r = service.autoComposeExamPaper(d, 99L);
        assertNotNull(r);
        assertEquals(2, r.getQuestionCount());
    }

    @Test
    @DisplayName("autoCompose 按难度比例分配")
    void compose_difficultyRatio() {
        Question easy = q(1L, "SINGLE", new BigDecimal("5"), null); easy.setDifficulty("EASY");
        Question hard = q(2L, "SINGLE", new BigDecimal("5"), null); hard.setDifficulty("HARD");
        when(questionService.list(any(Wrapper.class))).thenReturn(List.of(easy, hard));
        AutoComposeDTO d = baseDto();
        d.setDifficultyRatio(Map.of("EASY", 1, "HARD", 1));
        ExamPaper r = service.autoComposeExamPaper(d, 99L);
        assertNotNull(r);
        assertEquals(2, r.getQuestionCount());
    }

    @Test
    @DisplayName("autoCompose 无匹配题目抛异常")
    void compose_noMatch() {
        // 课程下无 SINGLE 类型题目，但规则只要 SINGLE -> 选不出 -> 抛异常
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(q(1L, "MULTI", new BigDecimal("5"), null)));
        assertThrows(BusinessException.class, () -> service.autoComposeExamPaper(baseDto(), 99L));
    }
}
