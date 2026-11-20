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
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
 * ExamPaperServiceImpl 剩余分支补测：空集合、缺省值、知识点/难度分流、总分兜底与换题异常路径。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamPaperServiceImplCoverageTest {

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
        when(examPaperMapper.insert(any(ExamPaper.class))).thenReturn(1);
        when(examPaperMapper.updateById(any(ExamPaper.class))).thenReturn(1);
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(examQuestionService.save(any(ExamQuestion.class))).thenReturn(true);
        when(examQuestionService.saveBatch(anyCollection())).thenReturn(true);
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

    @Test
    @DisplayName("updateExamPaper：试卷不存在；未传总分跳过校验；题目分值空值按 0 处理")
    void updateBranches() {
        when(examPaperMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.updateExamPaper(1L, new ExamPaper()));

        reset(examPaperMapper);
        when(examPaperMapper.selectById(anyLong())).thenReturn(paper);
        when(examPaperMapper.updateById(any(ExamPaper.class))).thenReturn(1);

        ExamPaper patch = new ExamPaper();
        patch.setTitle("只改标题");
        assertDoesNotThrow(() -> service.updateExamPaper(1L, patch));

        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(9L);
        eq.setPoints(null);
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq));
        ExamPaper zeroPatch = new ExamPaper();
        zeroPatch.setTotalScore(BigDecimal.ZERO);
        assertDoesNotThrow(() -> service.updateExamPaper(1L, zeroPatch));
        verify(examPaperMapper, times(2)).updateById(any(ExamPaper.class));
    }

    @Test
    @DisplayName("getExamPaperDetail：无关联题目或题目 ID 为空时返回空清单")
    void detailEmptyBranches() {
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of());
        assertEquals(0, service.getExamPaperDetail(1L).getQuestions().size());

        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(null);
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq));
        assertEquals(0, service.getExamPaperDetail(1L).getQuestions().size());
    }

    @Test
    @DisplayName("submit/approve/reject：试卷不存在均报 NOT_FOUND")
    void auditNullPapers() {
        when(examPaperMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.submitExamPaperForReview(1L));
        assertThrows(BusinessException.class, () -> service.approveExamPaper(1L));
        assertThrows(BusinessException.class, () -> service.rejectExamPaper(1L, "x"));
    }

    @Test
    @DisplayName("rejectExamPaper：无创建人、空白原因、有原因与非空原因的通知文案")
    void rejectReasonBranches() {
        paper.setStatus("PENDING_REVIEW");

        paper.setCreatorId(null);
        service.rejectExamPaper(1L, null);
        verify(notificationService, never()).sendNotification(anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(), anyLong(), anyString());

        paper.setCreatorId(99L);
        paper.setStatus("PENDING_REVIEW");
        service.rejectExamPaper(1L, "   ");
        ArgumentCaptor<String> blankMessage = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendNotification(eq(99L), eq("试卷审核未通过"), blankMessage.capture(),
                eq("EXAM"), eq("EXAM_REJECTED"), eq("MEDIUM"), eq("/teacher/exams"), isNull(),
                eq(1L), eq("exam_paper"));
        assertFalse(blankMessage.getValue().contains("："));

        paper.setStatus("PENDING_REVIEW");
        service.rejectExamPaper(1L, " 超纲 ");
        ArgumentCaptor<String> reasonMessage = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(2)).sendNotification(eq(99L), eq("试卷审核未通过"),
                reasonMessage.capture(), eq("EXAM"), eq("EXAM_REJECTED"), eq("MEDIUM"),
                eq("/teacher/exams"), isNull(), eq(1L), eq("exam_paper"));
        assertTrue(reasonMessage.getValue().contains("：超纲"));
    }

    @Test
    @DisplayName("getPaperStatistics：无关联题目时题型/分值分布为空")
    void statsEmptyBranches() {
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of());
        assertEquals(0, service.getPaperStatistics(1L).getQuestionCount());
        assertTrue(service.getPaperStatistics(1L).getTypeDistribution().isEmpty());
    }

    @Test
    @DisplayName("autoCompose：空题型规则拒绝")
    void composeEmptyRules() {
        AutoComposeDTO dto = new AutoComposeDTO();
        dto.setCourseId(10L);
        dto.setTypeRules(Map.of());
        assertThrows(BusinessException.class, () -> service.autoComposeExamPaper(dto, 99L));
    }

    @Test
    @DisplayName("autoCompose：知识点空白/缺失进入补充池，匹配项优先")
    void composeKnowledgePointBranches() {
        when(questionService.list(any(Wrapper.class))).thenReturn(List.of(
                q(1L, "SINGLE", new BigDecimal("5"), "函数"),
                q(2L, "SINGLE", new BigDecimal("5"), "代数"),
                q(3L, "SINGLE", new BigDecimal("5"), null),
                q(4L, "SINGLE", new BigDecimal("5"), "   ")));
        AutoComposeDTO dto = baseDto();
        dto.setTypeRules(Map.of("SINGLE", 4));
        dto.setKnowledgePointNames(List.of("函数"));
        ExamPaper result = service.autoComposeExamPaper(dto, 99L);
        assertEquals(4, result.getQuestionCount());
    }

    @Test
    @DisplayName("autoCompose：难度比例合计为 0 时不选任何题并拒绝空卷")
    void composeZeroDifficultyRatio() {
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(q(1L, "SINGLE", new BigDecimal("5"), null)));
        AutoComposeDTO dto = baseDto();
        dto.setTypeRules(Map.of("SINGLE", 1));
        dto.setDifficultyRatio(Map.of("MEDIUM", 0));
        assertThrows(BusinessException.class, () -> service.autoComposeExamPaper(dto, 99L));
    }

    @Test
    @DisplayName("autoCompose：难度分支优先使用 scoreRules，题目分值为空回落 5")
    void composeDifficultyScoreRules() {
        Question candidate = q(1L, "SINGLE", null, null);
        candidate.setDifficulty("EASY");
        when(questionService.list(any(Wrapper.class))).thenReturn(List.of(candidate));
        AutoComposeDTO dto = baseDto();
        dto.setTypeRules(Map.of("SINGLE", 1));
        dto.setDifficultyRatio(Map.of("EASY", 1));
        dto.setScoreRules(Map.of("SINGLE", new BigDecimal("7")));

        ExamPaper result = service.autoComposeExamPaper(dto, 99L);
        assertEquals(new BigDecimal("7"), result.getTotalScore());
    }

    @Test
    @DisplayName("autoCompose：非难度分支优先使用 scoreRules，题目分值为空回落 5")
    void composePlainScoreRules() {
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(q(1L, "SINGLE", null, null)));
        AutoComposeDTO dto = baseDto();
        dto.setTypeRules(Map.of("SINGLE", 1));
        dto.setScoreRules(Map.of("SINGLE", new BigDecimal("6")));

        ExamPaper result = service.autoComposeExamPaper(dto, 99L);
        assertEquals(new BigDecimal("6"), result.getTotalScore());
    }

    @Test
    @DisplayName("autoCompose：scoreRules 值为 null 时总分跳过该题")
    void composeNullScoreRule() {
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(q(1L, "SINGLE", new BigDecimal("5"), null)));
        Map<String, BigDecimal> scoreRules = new HashMap<>();
        scoreRules.put("SINGLE", null);
        AutoComposeDTO dto = baseDto();
        dto.setTypeRules(Map.of("SINGLE", 1));
        dto.setScoreRules(scoreRules);

        ExamPaper result = service.autoComposeExamPaper(dto, 99L);
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalScore()));
    }

    @Test
    @DisplayName("autoCompose：标题/描述/状态/布尔缺省与显式值分流")
    void composeDefaultFieldBranches() {
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(q(1L, "SINGLE", new BigDecimal("5"), null),
                        q(2L, "SINGLE", new BigDecimal("5"), null)));
        AutoComposeDTO dto = baseDto();
        dto.setTypeRules(Map.of("SINGLE", 2));
        dto.setTitle("自定义卷");
        dto.setDescription("说明");
        dto.setStatus("PENDING_REVIEW");
        dto.setShuffleQuestions(Boolean.FALSE);
        dto.setShuffleOptions(Boolean.FALSE);
        dto.setShowScore(Boolean.FALSE);
        dto.setShowCorrectAnswer(Boolean.TRUE);
        dto.setMaxAttempts(3);
        dto.setTimeLimit(20);
        dto.setAllowReview(Boolean.FALSE);
        dto.setTotalScore(new BigDecimal("10"));

        ExamPaper result = service.autoComposeExamPaper(dto, 99L);
        assertEquals("自定义卷", result.getTitle());
        assertEquals("说明", result.getDescription());
        assertEquals("PENDING_REVIEW", result.getStatus());
        assertFalse(result.getShuffleQuestions());
        assertFalse(result.getShuffleOptions());
        assertFalse(result.getShowScore());
        assertTrue(result.getShowCorrectAnswer());
        assertEquals(3, result.getMaxAttempts());
        assertEquals(20, result.getTimeLimit());
        assertFalse(result.getAllowReview());
    }

    @Test
    @DisplayName("replaceExamQuestions：试卷不存在、清单 null/空均拒绝")
    void replaceGuards() {
        when(examPaperMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.replaceExamQuestions(1L, List.of()));
        reset(examPaperMapper);
        when(examPaperMapper.selectById(anyLong())).thenReturn(paper);
        assertThrows(BusinessException.class, () -> service.replaceExamQuestions(1L, null));
        assertThrows(BusinessException.class, () -> service.replaceExamQuestions(1L, List.of()));
    }

    @Test
    @DisplayName("replaceExamQuestions：DRAFT 可换题，ID 为空/重复/不存在/跨课程均拒绝")
    void replaceValidationBranches() {
        paper.setStatus("DRAFT");
        when(questionService.listByIds(anyCollection()))
                .thenReturn(List.of(q(7L, "SINGLE", new BigDecimal("5"), null)));
        ExamQuestionBatchDTO.ExamQuestionItem item = item(7L, null, null);
        assertDoesNotThrow(() -> service.replaceExamQuestions(1L, List.of(item)));

        ExamQuestionBatchDTO.ExamQuestionItem nullId = item(null, null, null);
        assertThrows(BusinessException.class, () -> service.replaceExamQuestions(1L, List.of(nullId)));

        ExamQuestionBatchDTO.ExamQuestionItem duplicateA = item(7L, null, null);
        ExamQuestionBatchDTO.ExamQuestionItem duplicateB = item(7L, null, null);
        assertThrows(BusinessException.class, () -> service.replaceExamQuestions(1L, List.of(duplicateA, duplicateB)));

        when(questionService.listByIds(anyCollection())).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.replaceExamQuestions(1L, List.of(item)));

        Question otherCourse = q(8L, "SINGLE", new BigDecimal("5"), null);
        otherCourse.setCourseId(20L);
        when(questionService.listByIds(anyCollection())).thenReturn(List.of(otherCourse));
        assertThrows(BusinessException.class, () -> service.replaceExamQuestions(1L, List.of(item(8L, null, null))));
    }

    @Test
    @DisplayName("replaceExamQuestions：points 与 orderIndex 缺省回落，显式值保留")
    void replaceFallbackBranches() {
        paper.setStatus("REJECTED");
        Question noScore = q(7L, "SINGLE", null, null);
        Question scored = q(8L, "MULTI", new BigDecimal("3"), null);
        when(questionService.listByIds(anyCollection())).thenReturn(List.of(noScore, scored));

        ExamQuestionBatchDTO.ExamQuestionItem first = item(7L, null, null);
        ExamQuestionBatchDTO.ExamQuestionItem second = item(8L, new BigDecimal("9"), 5);
        List<ExamQuestion> rows = service.replaceExamQuestions(1L, List.of(first, second));

        assertEquals(new BigDecimal("5"), rows.get(0).getPoints());
        assertEquals(0, rows.get(0).getOrderIndex());
        assertEquals(new BigDecimal("9"), rows.get(1).getPoints());
        assertEquals(5, rows.get(1).getOrderIndex());
        assertEquals(new BigDecimal("14"), paper.getTotalScore());
    }

    private AutoComposeDTO baseDto() {
        AutoComposeDTO d = new AutoComposeDTO();
        d.setCourseId(10L);
        d.setTypeRules(Map.of("SINGLE", 1));
        return d;
    }

    private ExamQuestionBatchDTO.ExamQuestionItem item(Long questionId, BigDecimal points, Integer orderIndex) {
        ExamQuestionBatchDTO.ExamQuestionItem item = new ExamQuestionBatchDTO.ExamQuestionItem();
        item.setQuestionId(questionId);
        item.setPoints(points);
        item.setOrderIndex(orderIndex);
        return item;
    }
}
