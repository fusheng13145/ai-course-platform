package com.aicourse.service.impl;

import com.aicourse.entity.ExamQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamQuestionMapper;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 纯 Mockito 单元测试：覆盖 ExamQuestionServiceImpl 的批量添加与顺序调整逻辑及错误分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamQuestionServiceImplTest {

    @Mock
    private ExamQuestionMapper examQuestionMapper;

    private ExamQuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExamQuestion.class);
        service = new ExamQuestionServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", examQuestionMapper);
    }

    private Map<String, Object> q(Long questionId, String points) {
        Map<String, Object> m = new HashMap<>();
        m.put("questionId", questionId);
        m.put("points", points);
        return m;
    }

    // ===== batchAddQuestions =====

    @Test
    @DisplayName("batchAddQuestions 列表为 null 抛异常")
    void batchAdd_null() {
        assertThrows(BusinessException.class, () -> service.batchAddQuestions(1L, null));
    }

    @Test
    @DisplayName("batchAddQuestions 列表为空抛异常")
    void batchAdd_empty() {
        assertThrows(BusinessException.class, () -> service.batchAddQuestions(1L, List.of()));
    }

    @Test
    @DisplayName("batchAddQuestions 无历史最大序号，从 1 开始")
    void batchAdd_noExisting() {
        when(examQuestionMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(examQuestionMapper.insert(any(ExamQuestion.class))).thenReturn(1);

        service.batchAddQuestions(10L, List.of(q(100L, "5"), q(101L, "10")));
        verify(examQuestionMapper, times(2)).insert(any(ExamQuestion.class));
    }

    @Test
    @DisplayName("batchAddQuestions 历史序号存在则递增")
    void batchAdd_withExistingOrder() {
        ExamQuestion last = new ExamQuestion();
        last.setOrderIndex(3);
        when(examQuestionMapper.selectOne(any(Wrapper.class))).thenReturn(last);
        when(examQuestionMapper.insert(any(ExamQuestion.class))).thenReturn(1);

        service.batchAddQuestions(10L, List.of(q(100L, "5")));
        verify(examQuestionMapper).insert(any(ExamQuestion.class));
    }

    @Test
    @DisplayName("batchAddQuestions 历史序号为 null 则从 1 开始")
    void batchAdd_existingOrderNull() {
        ExamQuestion last = new ExamQuestion();
        last.setOrderIndex(null);
        when(examQuestionMapper.selectOne(any(Wrapper.class))).thenReturn(last);
        when(examQuestionMapper.insert(any(ExamQuestion.class))).thenReturn(1);

        service.batchAddQuestions(10L, List.of(q(100L, "5")));
        verify(examQuestionMapper).insert(any(ExamQuestion.class));
    }

    // ===== reorderQuestions =====

    @Test
    @DisplayName("reorderQuestions 列表为 null 抛异常")
    void reorder_null() {
        assertThrows(BusinessException.class, () -> service.reorderQuestions(1L, null));
    }

    @Test
    @DisplayName("reorderQuestions 列表为空抛异常")
    void reorder_empty() {
        assertThrows(BusinessException.class, () -> service.reorderQuestions(1L, List.of()));
    }

    @Test
    @DisplayName("reorderQuestions 存在不属于该试卷的题目抛异常")
    void reorder_notBelong() {
        // 一次 list 取回为空 -> 任一 ID 均不属于该试卷 -> 抛异常
        when(examQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertThrows(BusinessException.class,
                () -> service.reorderQuestions(1L, List.of(100L, 101L)));
    }

    @Test
    @DisplayName("reorderQuestions 全部属于该试卷则批量更新顺序")
    void reorder_ok() {
        ExamQuestion eq100 = new ExamQuestion();
        eq100.setId(100L);
        ExamQuestion eq101 = new ExamQuestion();
        eq101.setId(101L);
        when(examQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(eq100, eq101));
        when(examQuestionMapper.updateById(any(ExamQuestion.class))).thenReturn(1);

        service.reorderQuestions(1L, List.of(100L, 101L));
        verify(examQuestionMapper, times(2)).updateById(any(ExamQuestion.class));
    }

    @Test
    @DisplayName("reorderQuestions 题目缺失（不在取回集合）抛异常")
    void reorder_missingThrows() {
        // 仅取回 100，101 缺失 -> 更新 100 时发现 101 不属于试卷 -> 抛异常（事务回滚）
        ExamQuestion eq = new ExamQuestion();
        eq.setId(100L);
        when(examQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(eq));

        assertThrows(BusinessException.class,
                () -> service.reorderQuestions(1L, List.of(100L, 101L)));
        // 边遍历边更新：100 已更新，101 缺失时抛异常（@Transactional 回滚保证原子性）
        verify(examQuestionMapper, times(1)).updateById(any(ExamQuestion.class));
    }
}
