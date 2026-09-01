package com.aicourse.service.impl;

import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.WrongQuestionMapper;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.WrongQuestionStatsVO;
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

import java.io.Serializable;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 纯 Mockito 单元测试：补充覆盖 WrongQuestionServiceImpl 的记录/掌握/统计/练习卷/推荐等分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WrongQuestionServiceImplBoostTest {

    @Mock
    private WrongQuestionMapper wrongQuestionMapper;
    @Mock
    private QuestionService questionService;

    private WrongQuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), WrongQuestion.class);
        service = new WrongQuestionServiceImpl(questionService);
        ReflectionTestUtils.setField(service, "baseMapper", wrongQuestionMapper);
    }

    private WrongQuestion wq(Long id, Long studentId, Boolean mastered, Integer practice) {
        WrongQuestion w = new WrongQuestion();
        w.setId(id);
        w.setStudentId(studentId);
        w.setQuestionId(100L);
        w.setCourseId(1L);
        w.setMastered(mastered);
        w.setPracticeCount(practice);
        return w;
    }

    @Test
    @DisplayName("recordWrongAnswer 新建错题")
    void record_new() {
        when(wrongQuestionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(wrongQuestionMapper.insert(any(WrongQuestion.class))).thenReturn(1);
        WrongQuestion r = service.recordWrongAnswer(2L, 100L, 1L, "A", "EXAM");
        assertFalse(r.getMastered());
        assertEquals(1, r.getPracticeCount());
    }

    @Test
    @DisplayName("recordWrongAnswer 已有记录则更新并取消掌握")
    void record_existing() {
        WrongQuestion existing = wq(5L, 2L, true, 3);
        when(wrongQuestionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(existing);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);
        WrongQuestion r = service.recordWrongAnswer(2L, 100L, 1L, "B", "EXAM");
        assertFalse(r.getMastered());
        assertEquals(4, r.getPracticeCount());
    }

    @Test
    @DisplayName("recordWrongAnswer 已有记录 practiceCount 为 null")
    void record_existingNullPractice() {
        WrongQuestion existing = wq(5L, 2L, true, null);
        when(wrongQuestionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(existing);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);
        WrongQuestion r = service.recordWrongAnswer(2L, 100L, 1L, "B", "EXAM");
        assertEquals(1, r.getPracticeCount());
    }

    @Test
    @DisplayName("markMastered 记录不存在抛异常")
    void markMastered_notFound() {
        when(wrongQuestionMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.markMastered(1L));
    }

    @Test
    @DisplayName("markMastered 成功")
    void markMastered_ok() {
        WrongQuestion w = wq(1L, 2L, false, 1);
        when(wrongQuestionMapper.selectById(any())).thenReturn(w);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);
        service.markMastered(1L);
        assertTrue(w.getMastered());
    }

    @Test
    @DisplayName("markMasteredForStudent 归属正确")
    void markMasteredForStudent_ok() {
        WrongQuestion w = wq(1L, 2L, false, 1);
        when(wrongQuestionMapper.selectById(any())).thenReturn(w);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);
        service.markMasteredForStudent(1L, 2L);
        assertTrue(w.getMastered());
    }

    @Test
    @DisplayName("markMasteredForStudent 记录不存在抛异常")
    void markMasteredForStudent_notFound() {
        when(wrongQuestionMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.markMasteredForStudent(1L, 2L));
    }

    @Test
    @DisplayName("markMasteredForStudent studentId 为 null 触发 403")
    void markMasteredForStudent_studentNull() {
        WrongQuestion w = wq(1L, 2L, false, 1);
        when(wrongQuestionMapper.selectById(any())).thenReturn(w);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markMasteredForStudent(1L, null));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("markMasteredForStudent 记录 studentId 为 null 触发 403")
    void markMasteredForStudent_recordStudentNull() {
        WrongQuestion w = wq(1L, null, false, 1);
        when(wrongQuestionMapper.selectById(any())).thenReturn(w);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markMasteredForStudent(1L, 2L));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("markMasteredForStudent 不匹配触发 403")
    void markMasteredForStudent_mismatch() {
        WrongQuestion w = wq(1L, 9L, false, 1);
        when(wrongQuestionMapper.selectById(any())).thenReturn(w);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markMasteredForStudent(1L, 2L));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("deleteForStudent 成功")
    void deleteForStudent_ok() {
        WrongQuestion w = wq(1L, 2L, false, 1);
        when(wrongQuestionMapper.selectById(any())).thenReturn(w);
        when(wrongQuestionMapper.deleteById(any(Serializable.class))).thenReturn(1);
        service.deleteForStudent(1L, 2L);
        verify(wrongQuestionMapper).deleteById(any(Serializable.class));
    }

    @Test
    @DisplayName("deleteForStudent 无权限触发 403")
    void deleteForStudent_forbidden() {
        WrongQuestion w = wq(1L, 9L, false, 1);
        when(wrongQuestionMapper.selectById(any())).thenReturn(w);
        assertThrows(BusinessException.class, () -> service.deleteForStudent(1L, 2L));
    }

    @Test
    @DisplayName("unmarkMastered 记录不存在抛异常")
    void unmarkMastered_notFound() {
        when(wrongQuestionMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.unmarkMastered(1L));
    }

    @Test
    @DisplayName("unmarkMastered 取消掌握并增加练习次数")
    void unmarkMastered_ok() {
        WrongQuestion w = wq(1L, 2L, true, 2);
        when(wrongQuestionMapper.selectById(any())).thenReturn(w);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);
        service.unmarkMastered(1L);
        assertFalse(w.getMastered());
        assertEquals(3, w.getPracticeCount());
    }

    @Test
    @DisplayName("incrementPracticeCount 记录不存在抛异常")
    void increment_notFound() {
        when(wrongQuestionMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.incrementPracticeCount(1L));
    }

    @Test
    @DisplayName("incrementPracticeCount 增加次数")
    void increment_ok() {
        WrongQuestion w = wq(1L, 2L, false, 2);
        when(wrongQuestionMapper.selectById(any())).thenReturn(w);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);
        service.incrementPracticeCount(1L);
        assertEquals(3, w.getPracticeCount());
    }

    @Test
    @DisplayName("getWrongQuestionBook 分页")
    void getBook_ok() {
        Page<WrongQuestion> page = new Page<>(1, 10);
        page.setRecords(List.of(wq(1L, 2L, false, 1)));
        when(wrongQuestionMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        Page<WrongQuestion> r = service.getWrongQuestionBook(2L, 1L, 1, 10);
        assertEquals(1, r.getRecords().size());
    }

    @Test
    @DisplayName("getUnmastered 返回列表")
    void getUnmastered_ok() {
        when(wrongQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(wq(1L, 2L, false, 1)));
        assertEquals(1, service.getUnmastered(2L, 1L).size());
    }

    @Test
    @DisplayName("getByKnowledgePoints 题目知识点为空归类未知")
    void getByKnowledgePoints_unknown() {
        WrongQuestion w = wq(1L, 2L, false, 1);
        w.setQuestionId(100L);
        when(wrongQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(w));
        Question q = new Question();
        q.setId(100L); // knowledgePoints null
        when(questionService.getById(100L)).thenReturn(q);
        Map<String, List<WrongQuestion>> r = service.getByKnowledgePoints(2L, 1L);
        assertTrue(r.containsKey("未知知识点"));
    }

    @Test
    @DisplayName("getByKnowledgePoints 按知识点分组")
    void getByKnowledgePoints_grouped() {
        WrongQuestion w = wq(1L, 2L, false, 1);
        w.setQuestionId(100L);
        when(wrongQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(w));
        Question q = new Question();
        q.setId(100L);
        q.setKnowledgePoints("代数");
        when(questionService.getById(100L)).thenReturn(q);
        Map<String, List<WrongQuestion>> r = service.getByKnowledgePoints(2L, 1L);
        assertTrue(r.containsKey("代数"));
    }

    @Test
    @DisplayName("getWrongQuestionStats 总数为 0")
    void stats_totalZero() {
        when(wrongQuestionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(wrongQuestionMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        WrongQuestionStatsVO r = service.getWrongQuestionStats(2L, 1L);
        assertEquals(0L, r.getTotal());
        assertEquals(0.0, r.getMasteryRate());
    }

    @Test
    @DisplayName("getWrongQuestionStats 包含来源/错因/练习分布")
    void stats_full() {
        when(wrongQuestionMapper.selectCount(any(Wrapper.class))).thenReturn(2L, 1L);
        WrongQuestion mastered = wq(1L, 2L, true, 5);
        mastered.setSourceType("EXAM");
        mastered.setReason("粗心");
        WrongQuestion unmastered = wq(2L, 2L, false, 1);
        unmastered.setSourceType("QUIZ");
        unmastered.setReason("");
        when(wrongQuestionMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(mastered, unmastered));
        WrongQuestionStatsVO r = service.getWrongQuestionStats(2L, 1L);
        assertEquals(1L, r.getMastered());
        assertEquals(1L, r.getUnmastered());
        Map<String, Long> practice = r.getPracticeDistribution();
        assertEquals(1L, practice.get("neverOrOnce"));
        assertEquals(1L, practice.get("multipleTimes"));
    }

    @Test
    @DisplayName("generatePracticePaper 无未掌握返回空")
    void practice_empty() {
        when(wrongQuestionMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
        assertTrue(service.generatePracticePaper(2L, 1L, 5).isEmpty());
    }

    @Test
    @DisplayName("generatePracticePaper 生成练习卷")
    void practice_ok() {
        WrongQuestion w = wq(1L, 2L, false, 1);
        w.setQuestionId(100L);
        when(wrongQuestionMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>(List.of(w)));
        Question q = new Question();
        q.setId(100L);
        q.setIsActive(true);
        when(questionService.list(any(Wrapper.class))).thenReturn(List.of(q));
        List<Question> r = service.generatePracticePaper(2L, 1L, 5);
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("recommendRelatedQuestions 记录不存在抛异常")
    void recommend_notFound() {
        when(wrongQuestionMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.recommendRelatedQuestions(1L));
    }

    @Test
    @DisplayName("recommendRelatedQuestions 题目无知识点返回空")
    void recommend_noKnowledge() {
        WrongQuestion w = wq(1L, 2L, false, 1);
        when(wrongQuestionMapper.selectById(any())).thenReturn(w);
        Question q = new Question();
        q.setId(100L);
        q.setKnowledgePoints(null);
        when(questionService.getById(100L)).thenReturn(q);
        assertTrue(service.recommendRelatedQuestions(1L).isEmpty());
    }

    @Test
    @DisplayName("recommendRelatedQuestions 推荐相关题目")
    void recommend_ok() {
        WrongQuestion w = wq(1L, 2L, false, 1);
        w.setQuestionId(100L);
        when(wrongQuestionMapper.selectById(any())).thenReturn(w);
        Question q = new Question();
        q.setId(100L);
        q.setKnowledgePoints("代数");
        when(questionService.getById(100L)).thenReturn(q);
        when(wrongQuestionMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        Question related = new Question();
        related.setId(200L);
        related.setIsActive(true);
        when(questionService.list(any(Wrapper.class))).thenReturn(List.of(related));
        assertEquals(1, service.recommendRelatedQuestions(1L).size());
    }

    @Test
    @DisplayName("addWrongQuestion reason 为 null 直接返回")
    void addWrong_nullReason() {
        when(wrongQuestionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(wrongQuestionMapper.insert(any(WrongQuestion.class))).thenReturn(1);
        WrongQuestion r = service.addWrongQuestion(2L, 100L, 1L, "A", "EXAM", null);
        assertNotNull(r);
    }

    @Test
    @DisplayName("addWrongQuestion 带 reason 更新记录")
    void addWrong_withReason() {
        when(wrongQuestionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(wrongQuestionMapper.insert(any(WrongQuestion.class))).thenReturn(1);
        // 第二次 recordWrongAnswer 内 getOne 返回刚插入的
        WrongQuestion inserted = wq(5L, 2L, false, 1);
        when(wrongQuestionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null).thenReturn(inserted);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);
        WrongQuestion r = service.addWrongQuestion(2L, 100L, 1L, "A", "EXAM", "计算错误");
        assertEquals("计算错误", r.getReason());
    }
}
