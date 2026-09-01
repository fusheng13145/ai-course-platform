package com.aicourse.service.component;

import com.aicourse.constant.ExamSessionStatus;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * ExamSessionQueryComponent 单元测试（纯 Mockito）。
 * <p>覆盖成绩报告（会话不存在 / 基础字段 / 题目得分解析 / 等级分档）、
 * 活跃会话、会话题目映射、我的会话课程排名（并列占位）与 JSON 容错解析。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamSessionQueryComponentTest {

    @Mock
    private ExamPaperService examPaperService;

    @Mock
    private ExamQuestionService examQuestionService;

    @Mock
    private ExamSessionMapper examSessionMapper;

    private ExamSessionQueryComponent component;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ExamSession.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ExamPaper.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ExamQuestion.class);
        component = new ExamSessionQueryComponent(examPaperService, examQuestionService,
                examSessionMapper, new ObjectMapper());
    }

    // ===================== generateScoreReport =====================
    @Test
    @DisplayName("generateScoreReport 会话不存在抛异常")
    void generateScoreReport_sessionMissing() {
        when(examSessionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> component.generateScoreReport(1L));
    }

    @Test
    @DisplayName("generateScoreReport 输出基础字段与等级（试卷缺失显示未知试卷）")
    void generateScoreReport_normal() {
        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setExamPaperId(10L);
        session.setStudentId(5L);
        session.setStatus(ExamSessionStatus.GRADED);
        session.setPercentage(new BigDecimal("85"));
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examPaperService.getById(10L)).thenReturn(null);

        Map<String, Object> report = component.generateScoreReport(1L);
        assertEquals(1L, report.get("sessionId"));
        assertEquals("未知试卷", report.get("examPaperTitle"));
        assertEquals(5L, report.get("studentId"));
        assertEquals(ExamSessionStatus.GRADED, report.get("status"));
        assertEquals("良好", report.get("gradeLevel"));
        assertFalse(report.containsKey("questionDetails"));
    }

    @Test
    @DisplayName("generateScoreReport 解析题目得分并完成等级分档边界")
    void generateScoreReport_withScoresAndGrades() {
        assertGrade(90, "优秀");
        assertGrade(80, "良好");
        assertGrade(70, "中等");
        assertGrade(60, "及格");
        assertGrade(59, "不及格");
    }

    private void assertGrade(int pct, String expectedLevel) {
        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setExamPaperId(10L);
        session.setStudentId(5L);
        session.setPercentage(new BigDecimal(pct));
        session.setQuestionScores("{\"1\": 5, \"2\": 3.5}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examPaperService.getById(10L)).thenReturn(null);

        Map<String, Object> report = component.generateScoreReport(1L);
        assertEquals(expectedLevel, report.get("gradeLevel"), "得分率 " + pct + " 等级应为 " + expectedLevel);
        Map<Long, BigDecimal> details = (Map<Long, BigDecimal>) report.get("questionDetails");
        assertEquals(2, details.size());
        assertEquals(0, new BigDecimal("5").compareTo(details.get(1L)));
        assertEquals(0, new BigDecimal("3.5").compareTo(details.get(2L)));
    }

    // ===================== getActiveExams =====================
    @Test
    @DisplayName("getActiveExams 查询进行中会话")
    void getActiveExams() {
        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setStatus(ExamSessionStatus.STARTED);
        when(examSessionMapper.selectList(any())).thenReturn(Collections.singletonList(session));

        List<ExamSession> list = component.getActiveExams();
        assertEquals(1, list.size());
        assertEquals(ExamSessionStatus.STARTED, list.get(0).getStatus());
    }

    // ===================== getSessionQuestions =====================
    @Test
    @DisplayName("getSessionQuestions 会话不存在返回空列表")
    void getSessionQuestions_missing() {
        when(examSessionMapper.selectById(1L)).thenReturn(null);
        assertTrue(component.getSessionQuestions(1L).isEmpty());
    }

    @Test
    @DisplayName("getSessionQuestions 映射题目字段为 questionId/orderIndex/score")
    void getSessionQuestions_ok() {
        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setExamPaperId(10L);
        when(examSessionMapper.selectById(1L)).thenReturn(session);

        ExamQuestion question = new ExamQuestion();
        question.setQuestionId(55L);
        question.setOrderIndex(2);
        question.setPoints(new BigDecimal("5"));
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(Collections.singletonList(question));

        List<Map<String, Object>> list = component.getSessionQuestions(1L);
        assertEquals(1, list.size());
        assertEquals(55L, list.get(0).get("questionId"));
        assertEquals(2, list.get(0).get("orderIndex"));
        assertEquals(0, new BigDecimal("5").compareTo((BigDecimal) list.get(0).get("score")));
    }

    // ===================== getMySessionsWithCourseRank =====================
    @Test
    @DisplayName("getMySessionsWithCourseRank 无会话返回空列表")
    void rank_noSessions() {
        when(examSessionMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertTrue(component.getMySessionsWithCourseRank(5L).isEmpty());
    }

    @Test
    @DisplayName("getMySessionsWithCourseRank 计算课程内排名（并列占位）")
    void rank_normal() {
        ExamSession mine = new ExamSession();
        mine.setId(1L);
        mine.setStudentId(5L);
        mine.setExamPaperId(10L);
        mine.setPercentage(new BigDecimal("80"));

        ExamPaper paper = new ExamPaper();
        paper.setId(10L);
        paper.setCourseId(100L);
        when(examPaperService.listByIds(anyCollection())).thenReturn(Collections.singletonList(paper));

        // 课程内全部含得分率会话：学生5(80%) / 学生6(95%) / 学生7(80%) -> 学生5 平均 80 并列第 2（占位）
        ExamSession other1 = new ExamSession();
        other1.setStudentId(5L);
        other1.setExamPaperId(10L);
        other1.setPercentage(new BigDecimal("80"));
        ExamSession other2 = new ExamSession();
        other2.setStudentId(6L);
        other2.setExamPaperId(10L);
        other2.setPercentage(new BigDecimal("95"));
        ExamSession other3 = new ExamSession();
        other3.setStudentId(7L);
        other3.setExamPaperId(10L);
        other3.setPercentage(new BigDecimal("80"));
        // 第一次 selectList：我的会话；第二次：课程内全部会话（thenReturn 链，避免覆盖）
        when(examSessionMapper.selectList(any()))
                .thenReturn(Collections.singletonList(mine))
                .thenReturn(Arrays.asList(other1, other2, other3));

        List<Map<String, Object>> result = component.getMySessionsWithCourseRank(5L);
        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).get("courseId"));
        assertEquals(2, result.get(0).get("courseRank"));
        assertEquals(3, result.get(0).get("courseTotalStudents"));
    }

    @Test
    @DisplayName("getMySessionsWithCourseRank 学生无有效得分会话时课程排名为空")
    void rank_noValidScores() {
        ExamSession mine = new ExamSession();
        mine.setId(1L);
        mine.setStudentId(5L);
        mine.setExamPaperId(10L);
        mine.setPercentage(new BigDecimal("80"));

        ExamPaper paper = new ExamPaper();
        paper.setId(10L);
        paper.setCourseId(100L);
        when(examPaperService.listByIds(anyCollection())).thenReturn(Collections.singletonList(paper));

        // 课程内会话均无得分率 -> computeCourseRank 无有效数据 -> 排名为空
        ExamSession noScore = new ExamSession();
        noScore.setStudentId(6L);
        noScore.setExamPaperId(10L);
        noScore.setPercentage(null);
        when(examSessionMapper.selectList(any()))
                .thenReturn(Collections.singletonList(mine))
                .thenReturn(Collections.singletonList(noScore));

        List<Map<String, Object>> result = component.getMySessionsWithCourseRank(5L);
        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).get("courseId"));
        assertNull(result.get(0).get("courseRank"));
        assertNull(result.get(0).get("courseTotalStudents"));
    }

    // ===================== parseScoresJson =====================
    @Test
    @DisplayName("parseScoresJson 空值/空对象返回空 Map")
    void parseScores_empty() {
        assertTrue(component.parseScoresJson(null).isEmpty());
        assertTrue(component.parseScoresJson("").isEmpty());
        assertTrue(component.parseScoresJson("{}").isEmpty());
    }

    @Test
    @DisplayName("parseScoresJson 非法 JSON 容错返回空 Map")
    void parseScores_invalid() {
        assertTrue(component.parseScoresJson("{invalid").isEmpty());
    }

    @Test
    @DisplayName("parseScoresJson 正常解析为题目得分 Map")
    void parseScores_ok() {
        Map<Long, BigDecimal> result = component.parseScoresJson("{\"1\":10,\"2\":8.5}");
        assertEquals(2, result.size());
        assertEquals(0, new BigDecimal("10").compareTo(result.get(1L)));
        assertEquals(0, new BigDecimal("8.5").compareTo(result.get(2L)));
    }
}
