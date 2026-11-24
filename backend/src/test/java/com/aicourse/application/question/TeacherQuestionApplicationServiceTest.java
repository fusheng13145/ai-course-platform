package com.aicourse.application.question;

import com.aicourse.common.PagedResponse;
import com.aicourse.dto.QuestionSaveDTO;
import com.aicourse.entity.Course;
import com.aicourse.entity.Question;
import com.aicourse.security.TeacherAccessGuard;
import com.aicourse.service.CourseService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.TeacherQuestionVO;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 教师端题库写面用例编排（3E-03C）。
 *
 * <p>盯三条改造前就存在的口径，避免迁移时被「顺手优化」掉：</p>
 * <ul>
 *   <li>{@code createdBy} 取登录身份，不信任请求体；</li>
 *   <li>更新时 {@code courseId} 忽略请求值、沿用原题目课程（否则可把题搬进别人课程）；</li>
 *   <li>无 {@code courseId} 且非管理员时，范围是「本人课程 ∪ 本人创建的历史题」。</li>
 * </ul>
 */
class TeacherQuestionApplicationServiceTest {

    private QuestionService questionService;
    private CourseService courseService;
    private TeacherAccessGuard teacherAccessGuard;

    private TeacherQuestionApplicationService service;
    private Authentication teacher;
    private Authentication admin;

    @BeforeEach
    void setUp() {
        questionService = mock(QuestionService.class);
        courseService = mock(CourseService.class);
        teacherAccessGuard = mock(TeacherAccessGuard.class);
        service = new TeacherQuestionApplicationService(questionService, courseService, teacherAccessGuard);
        teacher = new UsernamePasswordAuthenticationToken(7L, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
        admin = new UsernamePasswordAuthenticationToken(9L, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        // LambdaQueryWrapper 的列名与参数在 getSqlSegment() 时才落地，先注册实体元数据
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Question.class);
    }

    @Test
    @DisplayName("创建：先做课程归属校验，createdBy 取登录身份而非请求体")
    void createQuestion_stampsCreatedByFromPrincipal() {
        QuestionSaveDTO dto = new QuestionSaveDTO();
        dto.setContent("题干");
        dto.setCourseId(3L);
        dto.setType("SINGLE");

        TeacherQuestionVO vo = service.createQuestion(dto, teacher);

        assertEquals(7L, vo.getCreatedBy().longValue());
        InOrder order = inOrder(teacherAccessGuard, questionService);
        order.verify(teacherAccessGuard).assertCourseOwner(eq(3L), any());
        order.verify(questionService).createQuestion(ArgumentMatchers.any(Question.class));
    }

    @Test
    @DisplayName("列表：非管理员且不指定课程时，范围收敛为「本人课程 ∪ 本人创建」")
    void pageQuestions_nonAdminWithoutCourse_scopesToOwnCoursesOrOwnCreated() {
        Course own = new Course();
        own.setId(3L);
        when(teacherAccessGuard.isAdmin(teacher)).thenReturn(false);
        when(courseService.list(ArgumentMatchers.<Wrapper<Course>>any())).thenReturn(List.of(own));
        when(questionService.page(any(), ArgumentMatchers.<Wrapper<Question>>any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Question>());

        PagedResponse<TeacherQuestionVO> response = service.pageQuestions(1, 10, null, null, null, teacher);

        assertEquals(0, response.getTotal());
        ArgumentCaptor<Wrapper<Question>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(questionService).page(any(), captor.capture());
        Collection<Object> params = wrapperParams(captor.getValue());
        assertTrue(params.contains(3L), "本人课程需进 IN，实际=" + params);
        assertTrue(params.contains(7L), "createdBy 需进条件，实际=" + params);
    }

    @Test
    @DisplayName("列表：管理员不指定课程时不加任何范围条件")
    void pageQuestions_adminHasNoScope() {
        when(teacherAccessGuard.isAdmin(admin)).thenReturn(true);
        when(questionService.page(any(), ArgumentMatchers.<Wrapper<Question>>any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Question>());

        service.pageQuestions(1, 10, null, null, null, admin);

        verify(courseService, never()).list(ArgumentMatchers.<Wrapper<Course>>any());
        ArgumentCaptor<Wrapper<Question>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(questionService).page(any(), captor.capture());
        assertTrue(wrapperParams(captor.getValue()).isEmpty(), "管理员不应带任何过滤值");
    }

    @Test
    @DisplayName("列表：指定课程时先校验归属，且范围不再叠加 createdBy")
    void pageQuestions_withCourse_checksOwnerOnly() {
        when(questionService.page(any(), ArgumentMatchers.<Wrapper<Question>>any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Question>());

        service.pageQuestions(1, 10, 3L, "SINGLE", null, teacher);

        verify(teacherAccessGuard).assertCourseOwner(eq(3L), any());
        verify(courseService, never()).list(ArgumentMatchers.<Wrapper<Course>>any());
        verify(teacherAccessGuard, never()).isAdmin(any());
    }

    @Test
    @DisplayName("更新：courseId 忽略请求值，沿用原题目课程；归属校验先于读原题")
    void updateQuestion_keepsOriginalCourse() {
        QuestionSaveDTO dto = new QuestionSaveDTO();
        dto.setContent("新题干");
        dto.setCourseId(99L);
        Question existing = new Question();
        existing.setId(1L);
        existing.setCourseId(3L);
        when(questionService.getById(1L)).thenReturn(existing);

        service.updateQuestion(1L, dto, teacher);

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionService).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getId().longValue());
        assertEquals(3L, captor.getValue().getCourseId().longValue());
        InOrder order = inOrder(teacherAccessGuard, questionService);
        order.verify(teacherAccessGuard).assertQuestionOwner(eq(1L), any());
        order.verify(questionService).getById(1L);
    }

    @Test
    @DisplayName("删除：先做题目归属校验，再删除")
    void deleteQuestion_authorizesBeforeRemoving() {
        service.deleteQuestion(1L, teacher);

        InOrder order = inOrder(teacherAccessGuard, questionService);
        order.verify(teacherAccessGuard).assertQuestionOwner(eq(1L), any());
        order.verify(questionService).removeById(1L);
    }

    @Test
    @DisplayName("导出：指定课程时校验归属；不指定时全校导出不加校验")
    void exportQuestions_checksOwnerOnlyWhenCourseGiven() {
        when(questionService.list(ArgumentMatchers.<Wrapper<Question>>any())).thenReturn(List.of());

        service.exportQuestions(3L, teacher);
        verify(teacherAccessGuard).assertCourseOwner(eq(3L), any());
        verify(questionService).list(ArgumentMatchers.<Wrapper<Question>>any());

        service.exportQuestions(null, teacher);
        verify(teacherAccessGuard).assertCourseOwner(eq(3L), any());  // 仍只有一次（未新增）
    }

    @Test
    @DisplayName("详情：先做题目归属校验，再取题")
    void questionDetail_authorizesFirst() {
        Question q = new Question();
        q.setId(1L);
        when(questionService.getById(1L)).thenReturn(q);

        assertEquals(1L, service.questionDetail(1L, teacher).getId().longValue());
        verify(teacherAccessGuard).assertQuestionOwner(eq(1L), any());
    }

    // ------------------------------------------------ helpers

    /** 触发 wrapper 生成 SQL 片段并取出参数值（等价于真实 SQL 的过滤条件）。 */
    private static Collection<Object> wrapperParams(Wrapper<?> wrapper) {
        AbstractWrapper<?, ?, ?> aw = (AbstractWrapper<?, ?, ?>) wrapper;
        aw.getSqlSegment();
        return aw.getParamNameValuePairs().values();
    }
}
