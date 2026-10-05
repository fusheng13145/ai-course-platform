package com.aicourse.service.impl;

import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.WrongQuestionMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.WrongQuestionStatsVO;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
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

    /**
     * D3 回归（事务隔离）：错题收录的独立事务版本必须声明 {@code REQUIRES_NEW}，
     * 否则外层测验判分事务被共享——submitQuiz 吞异常后仍尝试提交会抛
     * {@code UnexpectedRollbackException}。
     *
     * <p>Spring 事务代理在纯单测下不生效（无容器），故此处以反射断言注解真实存在，
     * 与 {@code StudentLearningServiceImplTest} 的行为断言（错题收录只走 InNewTx 方法）
     * 互为补充，共同锁死 D3 修复。</p>
     */
    @Test
    @DisplayName("D3 - recordWrongAnswerInNewTx 必须声明 REQUIRES_NEW 独立事务")
    void recordWrongAnswerInNewTx_requiresNew() throws NoSuchMethodException {
        Method impl = WrongQuestionServiceImpl.class.getMethod(
                "recordWrongAnswerInNewTx", Long.class, Long.class, Long.class, String.class, String.class);
        Transactional tx = impl.getAnnotation(Transactional.class);
        assertNotNull(tx, "recordWrongAnswerInNewTx 必须带 @Transactional，否则根本不会开启独立事务");
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation(),
                "传播属性必须是 REQUIRES_NEW，以挂起外层事务、隔离错题收录的失败回滚");
        assertTrue(tx.rollbackFor().length > 0
                        && Arrays.asList(tx.rollbackFor()).contains(Exception.class),
                "必须 rollbackFor = Exception.class，受检异常同样触发本事务回滚而不外溢");

        // 接口契约同样声明，保证经代理调用（Spring AOP 基于接口）时注解语义不丢失
        Method api = com.aicourse.service.WrongQuestionService.class.getMethod(
                "recordWrongAnswerInNewTx", Long.class, Long.class, Long.class, String.class, String.class);
        assertNotNull(api, "接口须暴露 recordWrongAnswerInNewTx，供 StudentTaskComponent 经代理调用");

        // 原 recordWrongAnswer 保持默认传播（REQUIRED），仅新方法是独立事务，避免误伤既有语义
        Method legacy = WrongQuestionServiceImpl.class.getMethod(
                "recordWrongAnswer", Long.class, Long.class, Long.class, String.class, String.class);
        Transactional legacyTx = legacy.getAnnotation(Transactional.class);
        if (legacyTx != null) {
            assertEquals(Propagation.REQUIRED, legacyTx.propagation(),
                    "recordWrongAnswer 应沿用外层事务（REQUIRED），REQUIRES_NEW 只加在独立事务版本上");
        }
    }

    /**
     * D3 回归（委托正确性）：独立事务方法须原样委托 recordWrongAnswer 的落库逻辑，
     * 保证 REQUIRES_NEW 事务内真正写入错题，而非变成空操作。
     */
    @Test
    @DisplayName("D3 - recordWrongAnswerInNewTx 委托到实际落库逻辑")
    void recordWrongAnswerInNewTx_delegatesToPersistence() {
        when(wrongQuestionMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(wrongQuestionMapper.insert(any(WrongQuestion.class))).thenReturn(1);

        WrongQuestion result = wrongQuestionService.recordWrongAnswerInNewTx(
                100L, 200L, 10L, "C", "QUIZ");

        assertNotNull(result, "独立事务版本仍须返回落库后的实体，而非 null 空操作");
        assertEquals(200L, result.getQuestionId());
        verify(wrongQuestionMapper).insert(any(WrongQuestion.class));
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

        WrongQuestionStatsVO stats = wrongQuestionService.getWrongQuestionStats(100L, 10L);

        assertNotNull(stats);
        assertEquals(2L, stats.getTotal());
        assertEquals(1L, stats.getMastered());
        assertEquals(1L, stats.getUnmastered());
        assertEquals(50.0, stats.getMasteryRate());
        assertNotNull(stats.getSourceTypeStats());
        assertNotNull(stats.getReasonDistribution());
        assertNotNull(stats.getPracticeDistribution());
    }

    @Test
    @DisplayName("错题统计 - 无错题时掌握率为0")
    void testGetWrongQuestionStats_Empty() {
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());

        WrongQuestionStatsVO stats = wrongQuestionService.getWrongQuestionStats(100L, 10L);

        assertEquals(0L, stats.getTotal());
        assertEquals(0.0, stats.getMasteryRate());
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