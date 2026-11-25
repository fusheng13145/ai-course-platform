package com.aicourse.application.question;

import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Question;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.QuestionAccessSupport;
import com.aicourse.service.QuestionPracticeService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.QuestionBankStatisticsVO;
import com.aicourse.vo.QuestionStatsVO;
import com.aicourse.vo.QuestionVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 题库读面用例编排（3E-03C）。
 *
 * <p>盯四件事：筛选条件真的进了 wrapper；可读课程集合为空时**不查库**；
 * {@code /random} 的 ACL 先于取题、且学生拿到的是脱敏 VO；统计/易错题的
 * 「可读集合非空才下推」两种重载不被写混。</p>
 */
class QuestionBankApplicationServiceTest {

    private QuestionService questionService;
    private CourseAccessControl courseAccessControl;
    private QuestionPracticeService questionPracticeService;
    private QuestionAccessSupport questionAccessSupport;

    private QuestionBankApplicationService service;
    private Authentication teacher;
    private Authentication student;

    @BeforeEach
    void setUp() {
        questionService = mock(QuestionService.class);
        courseAccessControl = mock(CourseAccessControl.class);
        questionPracticeService = mock(QuestionPracticeService.class);
        questionAccessSupport = mock(QuestionAccessSupport.class);
        service = new QuestionBankApplicationService(questionService, courseAccessControl,
                questionPracticeService, questionAccessSupport);
        teacher = new UsernamePasswordAuthenticationToken(7L, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
        student = new UsernamePasswordAuthenticationToken(8L, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        // LambdaQueryWrapper 的列名与参数在 getSqlSegment() 时才落地，先注册实体元数据
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Question.class);
    }

    // ------------------------------------------------ 列表

    @Test
    @DisplayName("列表：type/difficulty/keyword 进 wrapper，keyword 去首尾空白后进 LIKE")
    void pageQuestions_buildsFilters() {
        // Mockito 对 List 返回值默认给空集合，故「不限课程」必须显式打桩 null
        when(courseAccessControl.readableCourseIds(teacher)).thenReturn(null);
        when(questionService.page(any(), ArgumentMatchers.<Wrapper<Question>>any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Question>());

        PagedResponse<Question> response = service.pageQuestions(1, 10, 3L, "SINGLE", "EASY", " 闭包 ", teacher);

        assertEquals(0, response.getList().size());
        ArgumentCaptor<Wrapper<Question>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(questionService).page(any(), captor.capture());
        Collection<Object> params = wrapperParams(captor.getValue());
        assertTrue(params.contains("SINGLE"), "题型需进条件，实际=" + params);
        assertTrue(params.contains("EASY"), "难度需进条件，实际=" + params);
        assertTrue(params.stream().anyMatch(v -> v != null && v.toString().contains("闭包")),
                "关键词需去空白后进 LIKE，实际=" + params);
        verify(courseAccessControl).assertCanReadCourse(eq(3L), any());
    }

    @Test
    @DisplayName("列表：教师名下无可读课程时直接返回空页，不查库")
    void pageQuestions_emptyReadableCourses_skipsQuery() {
        when(courseAccessControl.readableCourseIds(teacher)).thenReturn(List.of());

        PagedResponse<Question> response = service.pageQuestions(1, 10, null, null, null, null, teacher);

        assertEquals(0, response.getTotal());
        assertEquals(0, response.getList().size());
        verify(questionService, never()).page(any(), ArgumentMatchers.<Wrapper<Question>>any());
    }

    @Test
    @DisplayName("列表：可读集合非空时进 IN 条件（教师只见自己课程）")
    void pageQuestions_readableCoursesBecomeInFilter() {
        when(courseAccessControl.readableCourseIds(teacher)).thenReturn(List.of(3L, 4L));
        when(questionService.page(any(), ArgumentMatchers.<Wrapper<Question>>any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Question>());

        service.pageQuestions(1, 10, null, null, null, null, teacher);

        ArgumentCaptor<Wrapper<Question>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(questionService).page(any(), captor.capture());
        Collection<Object> params = wrapperParams(captor.getValue());
        assertTrue(params.contains(3L) && params.contains(4L), "可读课程需进 IN，实际=" + params);
    }

    // ------------------------------------------------ 详情 / 搜索

    @Test
    @DisplayName("详情：课程为空的历史题不做课程校验；有课程才校验可读")
    void questionDetail_skipsAclForLegacyQuestion() {
        Question legacy = new Question();
        legacy.setId(1L);
        when(questionAccessSupport.requireQuestion(1L)).thenReturn(legacy);

        assertEquals(1L, service.questionDetail(1L, teacher).getId().longValue());
        verify(courseAccessControl, never()).assertCanReadCourse(any(), any());

        Question owned = new Question();
        owned.setId(2L);
        owned.setCourseId(3L);
        when(questionAccessSupport.requireQuestion(2L)).thenReturn(owned);
        service.questionDetail(2L, teacher);
        verify(courseAccessControl).assertCanReadCourse(eq(3L), any());
    }

    @Test
    @DisplayName("搜索：可读集合为 null 走全校重载，非 null 走带集合重载")
    void searchQuestions_choosesOverloadByReadableScope() {
        when(courseAccessControl.readableCourseIds(teacher)).thenReturn(null);
        when(questionService.searchQuestions(1L, "SINGLE", "EASY", "闭包", 1, 10))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Question>());
        service.searchQuestions(1L, "SINGLE", "EASY", "闭包", 1, 10, teacher);
        verify(questionService).searchQuestions(1L, "SINGLE", "EASY", "闭包", 1, 10);

        when(courseAccessControl.readableCourseIds(teacher)).thenReturn(List.of(1L));
        when(questionService.searchQuestions(1L, "SINGLE", "EASY", "闭包", 1, 10, List.of(1L)))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Question>());
        service.searchQuestions(1L, "SINGLE", "EASY", "闭包", 1, 10, teacher);
        verify(questionService).searchQuestions(1L, "SINGLE", "EASY", "闭包", 1, 10, List.of(1L));
    }

    // ------------------------------------------------ 随机取题（学生可达）

    @Test
    @DisplayName("随机取题：先做刷题开放校验，再取题；学生拿到脱敏 VO")
    void randomSelect_studentGetsDesensitizedVo() {
        Question q = new Question();
        q.setId(1L);
        q.setContent("题干");
        q.setCorrectAnswers("\"A\"");
        when(questionService.randomSelect(3L, "SINGLE", null, 5)).thenReturn(List.of(q));

        List<?> rows = service.randomSelect(3L, "SINGLE", null, 5, student);

        assertEquals(1, rows.size());
        assertInstanceOf(QuestionVO.class, rows.get(0));
        InOrder order = inOrder(questionPracticeService, questionService);
        order.verify(questionPracticeService).assertPracticeAllowed(eq(3L), any());
        order.verify(questionService).randomSelect(3L, "SINGLE", null, 5);
    }

    @Test
    @DisplayName("随机取题：教师拿到实体（组卷需要正确答案）")
    void randomSelect_teacherGetsEntity() {
        Question q = new Question();
        q.setId(1L);
        when(questionService.randomSelect(3L, null, null, 5)).thenReturn(List.of(q));

        List<?> rows = service.randomSelect(3L, null, null, 5, teacher);

        assertInstanceOf(Question.class, rows.get(0));
    }

    @Test
    @DisplayName("随机取题：服务层返回 null 时给空集合，不把 null 甩给前端")
    void randomSelect_nullBecomesEmptyList() {
        when(questionService.randomSelect(3L, null, null, 5)).thenReturn(null);

        assertTrue(service.randomSelect(3L, null, null, 5, student).isEmpty());
    }

    // ------------------------------------------------ 知识点 / 统计

    @Test
    @DisplayName("知识点查题：按 courseId 与可读集合双重过滤，且不查可读集合外的题")
    void questionsByKnowledgePoint_filtersInMemory() {
        Question inCourse = question(1L, 3L);
        Question otherCourse = question(2L, 9L);
        Question noCourse = question(3L, null);
        when(questionService.getByKnowledgePoint(5L)).thenReturn(List.of(inCourse, otherCourse, noCourse));
        when(courseAccessControl.readableCourseIds(teacher)).thenReturn(List.of(3L));

        List<Question> rows = service.questionsByKnowledgePoint(5L, null, teacher);

        assertEquals(1, rows.size());
        assertEquals(1L, rows.get(0).getId().longValue());
    }

    @Test
    @DisplayName("题库统计：可读集合非空按课程统计，null 走全校统计")
    void bankStatistics_choosesOverloadByReadableScope() {
        QuestionBankStatisticsVO dbWide = new QuestionBankStatisticsVO();
        when(courseAccessControl.readableCourseIds(teacher)).thenReturn(null);
        when(questionService.getBankStatistics()).thenReturn(dbWide);
        assertEquals(dbWide, service.bankStatistics(teacher));

        when(courseAccessControl.readableCourseIds(teacher)).thenReturn(List.of(3L));
        QuestionBankStatisticsVO scoped = new QuestionBankStatisticsVO();
        when(questionService.getBankStatistics(List.of(3L))).thenReturn(scoped);
        assertEquals(scoped, service.bankStatistics(teacher));
    }

    @Test
    @DisplayName("易错题：可读集合非空才下推；返回 null 给空集合")
    void errorProne_scopedAndNullSafe() {
        when(courseAccessControl.readableCourseIds(teacher)).thenReturn(null);
        when(questionService.getErrorProneQuestions(3L, 20)).thenReturn(null);
        assertTrue(service.errorProne(3L, 20, teacher).isEmpty());

        Question q = question(1L, 3L);
        when(courseAccessControl.readableCourseIds(teacher)).thenReturn(List.of(3L));
        when(questionService.getErrorProneQuestions(3L, 20, List.of(3L))).thenReturn(List.of(q));
        assertEquals(1, service.errorProne(3L, 20, teacher).size());
        // 本用例连跑两档（全校 / 按课程），课程校验各发生一次
        verify(courseAccessControl, org.mockito.Mockito.times(2)).assertCanReadCourse(eq(3L), any());
    }

    @Test
    @DisplayName("题目统计：详情校验走 requireQuestion + 课程可读")
    void questionStats_checksQuestionScope() {
        Question q = question(1L, 3L);
        when(questionAccessSupport.requireQuestion(1L)).thenReturn(q);
        QuestionStatsVO stats = new QuestionStatsVO();
        when(questionService.getQuestionStats(1L)).thenReturn(stats);

        assertEquals(stats, service.questionStats(1L, teacher));
        verify(courseAccessControl).assertCanReadCourse(eq(3L), any());
    }

    // ------------------------------------------------ helpers

    private static Question question(Long id, Long courseId) {
        Question q = new Question();
        q.setId(id);
        q.setCourseId(courseId);
        return q;
    }

    /** 触发 wrapper 生成 SQL 片段并取出参数值（等价于真实 SQL 的过滤条件）。 */
    private static Collection<Object> wrapperParams(Wrapper<?> wrapper) {
        AbstractWrapper<?, ?, ?> aw = (AbstractWrapper<?, ?, ?>) wrapper;
        aw.getSqlSegment();
        return aw.getParamNameValuePairs().values();
    }
}
