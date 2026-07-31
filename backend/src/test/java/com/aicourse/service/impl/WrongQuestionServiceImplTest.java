package com.aicourse.service.impl;

import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.WrongQuestionMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.QuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 错题本服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class WrongQuestionServiceImplTest {

    @Mock private QuestionService questionService;
    @Mock private WrongQuestionMapper wrongQuestionMapper;
    @Mock private AIService aiService;

    @InjectMocks private WrongQuestionServiceImpl wrongQuestionService;

    private WrongQuestion testWrongQuestion;
    private Question testQuestion;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(wrongQuestionService, "baseMapper", wrongQuestionMapper);

        testWrongQuestion = new WrongQuestion();
        testWrongQuestion.setId(1L);
        testWrongQuestion.setStudentId(100L);
        testWrongQuestion.setQuestionId(200L);
        testWrongQuestion.setCourseId(10L);
        testWrongQuestion.setWrongAnswer("A");
        testWrongQuestion.setSourceType("EXAM");
        testWrongQuestion.setMastered(false);
        testWrongQuestion.setPracticeCount(1);

        testQuestion = new Question();
        testQuestion.setId(200L);
        testQuestion.setType("SINGLE_CHOICE");
        testQuestion.setContent("题目内容");
        testQuestion.setCorrectAnswers("B");
        testQuestion.setKnowledgePoints("Java基础");
        testQuestion.setIsActive(true);
    }

    // ==================== recordWrongAnswer ====================

    @Test
    @DisplayName("记录错题 - 新建错题记录")
    void testRecordWrongAnswer_NewRecord() {
        when(wrongQuestionMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(wrongQuestionMapper.insert(any(WrongQuestion.class))).thenReturn(1);

        WrongQuestion result = wrongQuestionService.recordWrongAnswer(100L, 200L, 10L, "A", "EXAM");

        assertNotNull(result);
        assertEquals(100L, result.getStudentId());
        assertEquals(200L, result.getQuestionId());
        assertEquals("A", result.getWrongAnswer());
        assertEquals("EXAM", result.getSourceType());
        assertFalse(result.getMastered());
        assertEquals(1, result.getPracticeCount());
        assertNotNull(result.getLastPracticedAt());
        verify(wrongQuestionMapper).insert(any(WrongQuestion.class));
    }

    @Test
    @DisplayName("记录错题 - 更新已有记录")
    void testRecordWrongAnswer_UpdateExisting() {
        WrongQuestion existing = new WrongQuestion();
        existing.setId(1L);
        existing.setStudentId(100L);
        existing.setQuestionId(200L);
        existing.setWrongAnswer("A");
        existing.setMastered(true);
        existing.setPracticeCount(2);

        when(wrongQuestionMapper.selectOne(any(), anyBoolean())).thenReturn(existing);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);

        WrongQuestion result = wrongQuestionService.recordWrongAnswer(100L, 200L, 10L, "C", "PRACTICE");

        assertNotNull(result);
        assertEquals("C", result.getWrongAnswer());
        assertEquals(3, result.getPracticeCount());
        assertFalse(result.getMastered());
        verify(wrongQuestionMapper).updateById(any(WrongQuestion.class));
        verify(wrongQuestionMapper, never()).insert(any(WrongQuestion.class));
    }

    // ==================== markMastered ====================

    @Test
    @DisplayName("标记已掌握 - 成功标记")
    void testMarkMastered_Success() {
        WrongQuestion wq = new WrongQuestion();
        wq.setId(1L);
        wq.setMastered(false);

        when(wrongQuestionMapper.selectById(1L)).thenReturn(wq);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);

        assertDoesNotThrow(() -> wrongQuestionService.markMastered(1L));
        assertTrue(wq.getMastered());
        verify(wrongQuestionMapper).updateById(wq);
    }

    @Test
    @DisplayName("标记已掌握 - 记录不存在抛出异常")
    void testMarkMastered_NotFound() {
        when(wrongQuestionMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> wrongQuestionService.markMastered(999L));
        assertEquals("错题记录不存在", exception.getMessage());
    }

    // ==================== unmarkMastered ====================

    @Test
    @DisplayName("取消掌握标记 - 成功取消")
    void testUnmarkMastered_Success() {
        WrongQuestion wq = new WrongQuestion();
        wq.setId(1L);
        wq.setMastered(true);
        wq.setPracticeCount(3);

        when(wrongQuestionMapper.selectById(1L)).thenReturn(wq);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);

        assertDoesNotThrow(() -> wrongQuestionService.unmarkMastered(1L));
        assertFalse(wq.getMastered());
        assertEquals(4, wq.getPracticeCount());
        assertNotNull(wq.getLastPracticedAt());
    }

    @Test
    @DisplayName("取消掌握标记 - 记录不存在抛出异常")
    void testUnmarkMastered_NotFound() {
        when(wrongQuestionMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> wrongQuestionService.unmarkMastered(999L));
        assertEquals("错题记录不存在", exception.getMessage());
    }

    // ==================== getWrongQuestionBook ====================

    @Test
    @DisplayName("获取错题本 - 分页查询")
    @SuppressWarnings("unchecked")
    void testGetWrongQuestionBook_Pagination() {
        Page<WrongQuestion> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(testWrongQuestion));
        mockPage.setTotal(1);

        when(wrongQuestionMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        Page<WrongQuestion> result = wrongQuestionService.getWrongQuestionBook(100L, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("获取错题本 - 按课程ID过滤")
    @SuppressWarnings("unchecked")
    void testGetWrongQuestionBook_FilterByCourseId() {
        Page<WrongQuestion> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(testWrongQuestion));
        mockPage.setTotal(1);

        when(wrongQuestionMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        Page<WrongQuestion> result = wrongQuestionService.getWrongQuestionBook(100L, 10L, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
    }

    // ==================== getUnmastered ====================

    @Test
    @DisplayName("获取未掌握错题 - 返回未掌握列表")
    void testGetUnmastered_ReturnsOnlyUnmastered() {
        WrongQuestion unmastered = new WrongQuestion();
        unmastered.setMastered(false);

        when(wrongQuestionMapper.selectList(any())).thenReturn(List.of(unmastered));

        List<WrongQuestion> result = wrongQuestionService.getUnmastered(100L, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertFalse(result.get(0).getMastered());
    }

    @Test
    @DisplayName("获取未掌握错题 - 空列表")
    void testGetUnmastered_Empty() {
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<WrongQuestion> result = wrongQuestionService.getUnmastered(100L, 10L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getWrongQuestionStats ====================

    @Test
    @DisplayName("错题统计 - 完整统计计算")
    void testGetWrongQuestionStats_Comprehensive() {
        WrongQuestion wq1 = new WrongQuestion();
        wq1.setMastered(true);
        wq1.setSourceType("EXAM");
        wq1.setPracticeCount(3);
        wq1.setReason("概念不清");

        WrongQuestion wq2 = new WrongQuestion();
        wq2.setMastered(false);
        wq2.setSourceType("PRACTICE");
        wq2.setPracticeCount(1);

        // count() 两次调用：先 total(2) 再 mastered(1)；list() 用于分组统计
        when(wrongQuestionMapper.selectCount(any())).thenReturn(2L, 1L);
        when(wrongQuestionMapper.selectList(any())).thenReturn(List.of(wq1, wq2));

        Map<String, Object> stats = wrongQuestionService.getWrongQuestionStats(100L, 10L);

        assertNotNull(stats);
        assertEquals(2L, stats.get("total"));
        assertEquals(1L, stats.get("mastered"));
        assertEquals(1L, stats.get("unmastered"));
        assertEquals(50.0, stats.get("masteryRate"));
        assertNotNull(stats.get("sourceTypeStats"));
        assertNotNull(stats.get("reasonDistribution"));
        assertNotNull(stats.get("practiceDistribution"));
    }

    @Test
    @DisplayName("错题统计 - 无错题时掌握率为0")
    void testGetWrongQuestionStats_Empty() {
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> stats = wrongQuestionService.getWrongQuestionStats(100L, 10L);

        assertEquals(0L, stats.get("total"));
        assertEquals(0.0, stats.get("masteryRate"));
    }

    // ==================== generatePracticePaper ====================

    @Test
    @DisplayName("生成练习卷 - 生成指定数量题目")
    void testGeneratePracticePaper_GeneratesCorrectCount() {
        WrongQuestion wq1 = new WrongQuestion();
        wq1.setQuestionId(201L);
        wq1.setMastered(false);
        wq1.setPracticeCount(1);

        WrongQuestion wq2 = new WrongQuestion();
        wq2.setQuestionId(202L);
        wq2.setMastered(false);
        wq2.setPracticeCount(2);

        when(wrongQuestionMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(wq1, wq2)));

        Question q1 = new Question();
        q1.setId(201L);
        q1.setIsActive(true);
        Question q2 = new Question();
        q2.setId(202L);
        q2.setIsActive(true);

        when(questionService.list(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>(List.of(q1, q2)));

        List<Question> result = wrongQuestionService.generatePracticePaper(100L, 10L, 5);

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("生成练习卷 - 全部已掌握时返回空")
    void testGeneratePracticePaper_AllMastered() {
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<Question> result = wrongQuestionService.generatePracticePaper(100L, 10L, 5);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== recommendRelatedQuestions ====================

    @Test
    @DisplayName("推荐相关题目 - 有推荐结果")
    void testRecommendRelatedQuestions_HasRecommendations() {
        WrongQuestion wq = new WrongQuestion();
        wq.setId(1L);
        wq.setStudentId(100L);
        wq.setQuestionId(200L);

        when(wrongQuestionMapper.selectById(1L)).thenReturn(wq);
        when(questionService.getById(200L)).thenReturn(testQuestion);
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());

        Question related = new Question();
        related.setId(300L);
        related.setKnowledgePoints("Java基础");
        related.setIsActive(true);

        when(questionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(related));

        List<Question> result = wrongQuestionService.recommendRelatedQuestions(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(300L, result.get(0).getId());
    }

    @Test
    @DisplayName("推荐相关题目 - 无相关题目返回空")
    void testRecommendRelatedQuestions_Empty() {
        WrongQuestion wq = new WrongQuestion();
        wq.setId(1L);
        wq.setStudentId(100L);
        wq.setQuestionId(200L);

        Question q = new Question();
        q.setId(200L);
        q.setKnowledgePoints("");

        when(wrongQuestionMapper.selectById(1L)).thenReturn(wq);
        when(questionService.getById(200L)).thenReturn(q);

        List<Question> result = wrongQuestionService.recommendRelatedQuestions(1L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}