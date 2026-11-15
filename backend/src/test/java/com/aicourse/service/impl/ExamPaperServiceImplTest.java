package com.aicourse.service.impl;

import com.aicourse.dto.AutoComposeDTO;
import com.aicourse.dto.ExamQuestionBatchDTO;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamPaperMapper;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.ExamPaperStatsVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.isNull;
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
    @Mock private ExamSessionMapper examSessionMapper;

    private ExamPaperServiceImpl service;

    private ExamPaper paper;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExamPaper.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExamQuestion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Question.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExamSession.class);

        service = new ExamPaperServiceImpl(examQuestionService, questionService, notificationService, examSessionMapper);
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

        com.aicourse.vo.ExamPaperDetailVO r = service.getExamPaperDetail(1L);
        assertEquals(1, r.getQuestions().size());
        assertEquals(1, r.getQuestionCount());
        assertNotNull(r.getPaper());
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
        com.aicourse.vo.ExamPaperDetailVO r = service.getExamPaperDetail(1L);
        assertEquals(0, r.getQuestions().size());
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

    @Test
    @DisplayName("publishExamPaper 待审核态不得绕过审核")
    void publish_pendingReview_rejected() {
        paper.setStatus("PENDING_REVIEW");
        assertThrows(BusinessException.class, () -> service.publishExamPaper(1L));
        verify(notificationService, never()).onEvent(anyString(), any());
    }

    // ===================== 审核流 =====================

    @Test
    @DisplayName("submitExamPaperForReview 草稿进入待审核且不通知学生")
    void submitReview_ok() {
        paper.setStatus("DRAFT");
        service.submitExamPaperForReview(1L);
        verify(examPaperMapper).updateById(any(ExamPaper.class));
        verify(notificationService, never()).onEvent(anyString(), any());
    }

    @Test
    @DisplayName("submitExamPaperForReview 已发布/已待审均拒绝")
    void submitReview_conflicts() {
        paper.setStatus("PUBLISHED");
        assertThrows(BusinessException.class, () -> service.submitExamPaperForReview(1L));
        paper.setStatus("PENDING_REVIEW");
        assertThrows(BusinessException.class, () -> service.submitExamPaperForReview(1L));
    }

    @Test
    @DisplayName("approveExamPaper 待审核通过并推送发布通知")
    void approve_ok() {
        paper.setStatus("PENDING_REVIEW");
        service.approveExamPaper(1L);
        verify(notificationService).onEvent(eq("EXAM_PUBLISHED"), any());
    }

    @Test
    @DisplayName("approveExamPaper 非待审核态拒绝")
    void approve_wrongStatus() {
        paper.setStatus("DRAFT");
        assertThrows(BusinessException.class, () -> service.approveExamPaper(1L));
    }

    @Test
    @DisplayName("rejectExamPaper 待审核驳回并通知创建人")
    void reject_ok() {
        paper.setStatus("PENDING_REVIEW");
        service.rejectExamPaper(1L, "题干超纲");
        verify(examPaperMapper).updateById(any(ExamPaper.class));
        verify(notificationService).sendNotification(eq(99L), eq("试卷审核未通过"), anyString(),
                eq("EXAM"), eq("EXAM_REJECTED"), eq("MEDIUM"), eq("/teacher/exams"), isNull(),
                eq(1L), eq("exam_paper"));
    }

    @Test
    @DisplayName("rejectExamPaper 非待审核态拒绝")
    void reject_wrongStatus() {
        paper.setStatus("PUBLISHED");
        assertThrows(BusinessException.class, () -> service.rejectExamPaper(1L, "x"));
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

    // ===================== updateExamPaper（A-02 #5 / #8） =====================

    @Test
    @DisplayName("updateExamPaper 白名单：status/isActive/creatorId 不经请求体透传（A-02 #5）")
    void update_stripsStatusFields() {
        ExamPaper patch = new ExamPaper();
        patch.setTitle("改标题");
        patch.setStatus("PUBLISHED");     // 试图绕审核直发
        patch.setIsActive(Boolean.FALSE);
        patch.setCreatorId(777L);         // 试图改归属
        patch.setTotalScore(new BigDecimal("100")); // paper 无题目 → 跳过总分校验

        service.updateExamPaper(1L, patch);

        ArgumentCaptor<ExamPaper> captor = ArgumentCaptor.forClass(ExamPaper.class);
        verify(examPaperMapper).updateById(captor.capture());
        assertNull(captor.getValue().getStatus(), "status 必须被白名单剔除，审核流转只走专用端点");
        assertNull(captor.getValue().getIsActive());
        assertNull(captor.getValue().getCreatorId());
        assertEquals(1L, captor.getValue().getId());
        assertEquals("改标题", captor.getValue().getTitle(), "普通业务字段照常更新");
    }

    @Test
    @DisplayName("updateExamPaper 已配置题目时总分与分值合计不一致抛 400（A-02 #8）")
    void update_totalScoreMismatch_rejected() {
        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(9L);
        eq.setPoints(new BigDecimal("30"));
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq));

        ExamPaper patch = new ExamPaper();
        patch.setTotalScore(new BigDecimal("100")); // 题目合计只有 30

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateExamPaper(1L, patch));
        assertTrue(ex.getMessage().contains("总分与题目分值合计不一致"),
                "拒绝信息应给出两侧分值，实际=" + ex.getMessage());
        verify(examPaperMapper, never()).updateById(any(ExamPaper.class));
    }

    @Test
    @DisplayName("updateExamPaper 总分与分值合计一致（或卷面未配题）时正常更新")
    void update_totalScoreMatch_ok() {
        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(9L);
        eq.setPoints(new BigDecimal("30"));
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq));

        ExamPaper patch = new ExamPaper();
        patch.setTotalScore(new BigDecimal("30"));

        assertDoesNotThrow(() -> service.updateExamPaper(1L, patch));
        verify(examPaperMapper).updateById(any(ExamPaper.class));
    }

    // ===================== replaceExamQuestions 状态门禁（A-02 #7） =====================

    @Test
    @DisplayName("replaceExamQuestions PUBLISHED 试卷拒绝换题（A-02 #7：防篡改在考学生题目与判分口径）")
    void replaceQuestions_published_rejected() {
        paper.setStatus("PUBLISHED");
        ExamQuestionBatchDTO.ExamQuestionItem item = new ExamQuestionBatchDTO.ExamQuestionItem();
        item.setQuestionId(9L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.replaceExamQuestions(1L, List.of(item)));
        assertTrue(ex.getMessage().contains("不允许替换题目"), "实际=" + ex.getMessage());
        verify(examQuestionService, never()).remove(any(Wrapper.class));
    }

    @Test
    @DisplayName("replaceExamQuestions PENDING_REVIEW 试卷拒绝换题（待审内容冻结）")
    void replaceQuestions_pendingReview_rejected() {
        paper.setStatus("PENDING_REVIEW");
        ExamQuestionBatchDTO.ExamQuestionItem item = new ExamQuestionBatchDTO.ExamQuestionItem();
        item.setQuestionId(9L);

        assertThrows(BusinessException.class, () -> service.replaceExamQuestions(1L, List.of(item)));
        verify(examQuestionService, never()).remove(any(Wrapper.class));
    }

    @Test
    @DisplayName("replaceExamQuestions REJECTED 试卷允许换题并回填题目数与总分")
    void replaceQuestions_rejected_allowed() {
        paper.setStatus("REJECTED");
        when(questionService.listByIds(anyCollection()))
                .thenReturn(List.of(q(9L, "SINGLE", new BigDecimal("10"), null)));
        doReturn(true).when(examQuestionService).saveBatch(anyCollection());

        ExamQuestionBatchDTO.ExamQuestionItem item = new ExamQuestionBatchDTO.ExamQuestionItem();
        item.setQuestionId(9L);
        item.setPoints(new BigDecimal("10"));

        List<ExamQuestion> rows = service.replaceExamQuestions(1L, List.of(item));

        assertEquals(1, rows.size());
        assertEquals(new BigDecimal("10"), rows.get(0).getPoints());
        ArgumentCaptor<ExamPaper> captor = ArgumentCaptor.forClass(ExamPaper.class);
        verify(examPaperMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getQuestionCount());
        assertEquals(new BigDecimal("10"), captor.getValue().getTotalScore());
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

        ExamPaperStatsVO r = service.getPaperStatistics(1L);
        assertEquals(4, r.getQuestionCount());
        Map<String, Integer> td = r.getTypeDistribution();
        assertEquals(1, td.get("SINGLE"));
        assertEquals(1, td.get("MULTI"));
        Map<String, Integer> sd = r.getScoreDistribution();
        assertEquals(1, sd.get("1-5分"));
        assertEquals(1, sd.get("6-10分"));
        assertEquals(1, sd.get("11-20分"));
        assertEquals(1, sd.get("20分以上"));
        assertTrue(r.getTotalPoints().compareTo(BigDecimal.ZERO) > 0);
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
        ExamPaperStatsVO r = service.getPaperStatistics(1L);
        assertEquals(1, r.getQuestionCount());
        Map<String, Integer> td = r.getTypeDistribution();
        assertTrue(td.isEmpty());
    }

    @Test
    @DisplayName("getPaperStatistics 成绩聚合：均分与及格率按百分制，1 位小数")
    void stats_scoreAggregates() {
        ExamQuestion eq = new ExamQuestion(); eq.setQuestionId(1L); eq.setPoints(new BigDecimal("5"));
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(anyCollection())).thenReturn(List.of(q(1L, "SINGLE", new BigDecimal("5"), null)));
        when(examSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                session(new BigDecimal("80")), session(new BigDecimal("70")), session(new BigDecimal("50"))));

        ExamPaperStatsVO r = service.getPaperStatistics(1L);
        assertEquals(3, r.getSubmittedCount());
        assertEquals(new BigDecimal("66.7"), r.getAverageScore());
        assertEquals(new BigDecimal("66.7"), r.getPassRate());
        assertEquals(60, r.getPassLine());
    }

    @Test
    @DisplayName("getPaperStatistics 无已阅卷样本时均分/及格率为 null，不伪造 0")
    void stats_noSamples() {
        ExamQuestion eq = new ExamQuestion(); eq.setQuestionId(1L); eq.setPoints(new BigDecimal("5"));
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(anyCollection())).thenReturn(List.of(q(1L, "SINGLE", new BigDecimal("5"), null)));
        when(examSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        ExamPaperStatsVO r = service.getPaperStatistics(1L);
        assertEquals(0, r.getSubmittedCount());
        assertNull(r.getAverageScore());
        assertNull(r.getPassRate());
        assertEquals(60, r.getPassLine());
    }

    private ExamSession session(BigDecimal percentage) {
        ExamSession s = new ExamSession();
        s.setPercentage(percentage);
        return s;
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

    // ===================== autoComposeExamPaper 总分校验（A-02 #8） =====================

    @Test
    @DisplayName("autoCompose 显式传入总分与题目分值合计不一致抛 400（A-02 #8：拒绝静默覆盖）")
    void compose_totalScoreMismatch_rejected() {
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(q(1L, "SINGLE", new BigDecimal("5"), null),
                        q(2L, "SINGLE", new BigDecimal("5"), null)));
        AutoComposeDTO d = baseDto();
        d.setTotalScore(new BigDecimal("100")); // 规则选出 2 题 × 5 分，合计只有 10

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.autoComposeExamPaper(d, 99L));
        assertTrue(ex.getMessage().contains("总分与题目分值合计不一致"),
                "实际=" + ex.getMessage());
        verify(examPaperMapper, never()).insert(any(ExamPaper.class));
    }

    @Test
    @DisplayName("autoCompose 显式传入总分与合计一致时按合计落库")
    void compose_totalScoreMatch_ok() {
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(q(1L, "SINGLE", new BigDecimal("5"), null),
                        q(2L, "SINGLE", new BigDecimal("5"), null)));
        AutoComposeDTO d = baseDto();
        d.setTotalScore(new BigDecimal("10")); // 与 2 × 5 合计一致

        ExamPaper r = service.autoComposeExamPaper(d, 99L);

        assertEquals(new BigDecimal("10"), r.getTotalScore());
        verify(examPaperMapper).insert(any(ExamPaper.class));
    }
}
