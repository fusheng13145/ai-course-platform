package com.aicourse.application.exam;

import com.aicourse.common.ApiCode;
import com.aicourse.dto.AutoComposeDTO;
import com.aicourse.dto.ExamPaperSaveDTO;
import com.aicourse.dto.ExamQuestionBatchDTO;
import com.aicourse.entity.Course;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.ActivityVisibilityGuard;
import com.aicourse.security.ExamPaperAccessControl;
import com.aicourse.service.ActivityTargetService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.ExamPaperQuestionVO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * 试卷域用例编排（3E-03A）。
 *
 * <p>盯三件事：① 建卷/改卷的动作顺序（授权先于落库、可见范围归一化在落库之前）；
 * ② 删卷的 409 保护；③ 题目读取的答案裁剪与批量取题（原 N+1 已改批量，但顺序与缺失跳过语义不变）。</p>
 */
class ExamPaperApplicationServiceTest {

    private ExamPaperService examPaperService;
    private ExamQuestionService examQuestionService;
    private QuestionService questionService;
    private ExamSessionService examSessionService;
    private ExamPaperAccessControl examPaperAccessControl;
    private CourseService courseService;
    private ActivityVisibilityGuard activityVisibilityGuard;
    private ActivityTargetService activityTargetService;

    private ExamPaperApplicationService service;
    private Authentication teacher;
    private Authentication student;

    @BeforeEach
    void setUp() {
        // LambdaQueryWrapper 的列名与参数在 getSqlSegment() 时才落地，先注册实体元数据
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExamPaper.class);
        examPaperService = mock(ExamPaperService.class);
        examQuestionService = mock(ExamQuestionService.class);
        questionService = mock(QuestionService.class);
        examSessionService = mock(ExamSessionService.class);
        examPaperAccessControl = mock(ExamPaperAccessControl.class);
        courseService = mock(CourseService.class);
        activityVisibilityGuard = mock(ActivityVisibilityGuard.class);
        activityTargetService = mock(ActivityTargetService.class);
        service = new ExamPaperApplicationService(examPaperService, examQuestionService, questionService,
                examSessionService, examPaperAccessControl, courseService, activityVisibilityGuard,
                activityTargetService);
        teacher = new UsernamePasswordAuthenticationToken(20L, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
        student = new UsernamePasswordAuthenticationToken(10L, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    // ------------------------------------------------ 建卷

    @Test
    @DisplayName("建卷：授权 → 创建者取登录身份 → 归一化可见范围 → 落库 → 回写目标教学班")
    void create_runsOrchestrationInOrder() {
        ExamPaperSaveDTO dto = saveDTO(9L, "ALL");
        when(activityTargetService.normalizeAndValidate(isNull(), eq(9L), eq("ALL"), isNull(), eq(true)))
                .thenReturn("COURSE");

        ExamPaper paper = service.create(dto, teacher);

        verify(examPaperAccessControl).assertCanCompose(9L, teacher);
        assertEquals(20L, paper.getCreatorId());
        assertEquals("COURSE", paper.getVisibility(), "落库前必须用服务端归一化后的可见范围");
        verify(examPaperService).save(any(ExamPaper.class));
        verify(activityTargetService).replaceExamPaperTargets(isNull(), eq("COURSE"), isNull());
    }

    @Test
    @DisplayName("建卷：授权失败时不得落库、不得回写目标")
    void create_authorizationFailure_stopsBeforeWrite() {
        ExamPaperSaveDTO dto = saveDTO(9L, "ALL");
        org.mockito.Mockito.doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权组卷"))
                .when(examPaperAccessControl).assertCanCompose(9L, teacher);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto, teacher));

        assertEquals(ApiCode.FORBIDDEN, ex.getCode());
        verify(examPaperService, never()).save(any());
        verify(activityTargetService, never()).replaceExamPaperTargets(any(), any(), any());
    }

    @Test
    @DisplayName("建卷：管理员指定教学班可见范围时被拦（WP6）")
    void create_adminCannotScopeTeachingClasses() {
        ExamPaperSaveDTO dto = saveDTO(9L, "CLASSES");
        dto.setTeachingClassIds(List.of(3L));

        service.create(dto, teacher);

        verify(activityVisibilityGuard).assertNotAdmin(teacher);
    }

    // ------------------------------------------------ 改卷

    @Test
    @DisplayName("改卷：未触碰目标教学班时不做范围校验、不回写")
    void update_withoutTargets_skipsScopeWork() {
        ExamPaperSaveDTO dto = saveDTO(9L, null);
        ExamPaper existing = paper(1L, 9L);
        when(examPaperService.getById(1L)).thenReturn(existing);
        when(examPaperService.updateExamPaper(eq(1L), any())).thenReturn(existing);

        service.update(1L, dto, teacher);

        verify(activityTargetService, never()).normalizeAndValidate(any(), any(), any(), any(), any(Boolean.class));
        verify(activityTargetService, never()).replaceExamPaperTargets(any(), any(), any());
        verify(examPaperService).updateExamPaper(eq(1L), any());
    }

    @Test
    @DisplayName("改卷：触碰可见范围时用「旧卷可见范围」作为兜底再归一化")
    void update_touchingTargets_normalizesAgainstExistingVisibility() {
        ExamPaperSaveDTO dto = saveDTO(9L, null);
        dto.setTeachingClassIds(List.of(3L));
        ExamPaper existing = paper(1L, 9L);
        existing.setVisibility("COURSE");
        when(examPaperService.getById(1L)).thenReturn(existing);
        when(activityTargetService.normalizeAndValidate(eq(1L), eq(9L), eq("COURSE"), eq(List.of(3L)), eq(true)))
                .thenReturn("CLASSES");
        when(examPaperService.updateExamPaper(eq(1L), any())).thenReturn(existing);

        service.update(1L, dto, teacher);

        verify(activityVisibilityGuard).assertNotAdmin(teacher);
        verify(activityTargetService).replaceExamPaperTargets(eq(1L), eq("CLASSES"), eq(List.of(3L)));
    }

    // ------------------------------------------------ 删卷 / 归档

    @Test
    @DisplayName("删卷：已有考试记录时 409，不物理删除")
    void delete_withSessions_conflicts() {
        when(examPaperService.getById(1L)).thenReturn(paper(1L, 9L));
        when(examSessionService.count(any(Wrapper.class))).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L, teacher));

        assertEquals(ApiCode.CONFLICT, ex.getCode());
        assertTrue(ex.getMessage().contains("3 条考试记录"));
        verify(examPaperService, never()).removeById(anyLong());
    }

    @Test
    @DisplayName("删卷：无考试记录时执行物理删除")
    void delete_withoutSessions_removes() {
        when(examPaperService.getById(1L)).thenReturn(paper(1L, 9L));
        when(examSessionService.count(any(Wrapper.class))).thenReturn(0L);

        service.delete(1L, teacher);

        verify(examPaperService).removeById(1L);
    }

    @Test
    @DisplayName("归档：归属校验后调服务层归档，并回读最新实体")
    void archive_reloadsAfterTransition() {
        ExamPaper archived = paper(1L, 9L);
        when(examPaperService.getById(1L)).thenReturn(paper(1L, 9L), archived);

        ExamPaper result = service.archive(1L, teacher);

        assertNotNull(result);
        verify(examPaperService).archiveExamPaper(1L);
    }

    // ------------------------------------------------ 审核流转

    @Test
    @DisplayName("提交审核：管理员被拦（管理员只能走 /approve）")
    void publish_adminBlocked() {
        Authentication admin = new UsernamePasswordAuthenticationToken(1L, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        org.mockito.Mockito.doThrow(new BusinessException(ApiCode.FORBIDDEN, "管理员不得直接发布"))
                .when(activityVisibilityGuard).assertNotAdmin(admin);

        assertThrows(BusinessException.class, () -> service.publish(1L, admin));
        verify(examPaperService, never()).submitExamPaperForReview(anyLong());
    }

    // ------------------------------------------------ 题目读取

    @Test
    @DisplayName("题目读取：学生只拿作答字段（去标准答案/解析），且批量取题不吃 N+1")
    void listQuestions_studentGetsMaskedQuestions() {
        ExamPaper paper = paper(1L, 9L);
        when(examPaperService.getById(1L)).thenReturn(paper);
        ExamQuestion row = new ExamQuestion();
        row.setId(11L);
        row.setQuestionId(9L);
        row.setOrderIndex(1);
        row.setPoints(new java.math.BigDecimal("5"));
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(row));
        Question q = question(9L);
        q.setCorrectAnswers("A");
        q.setExplanation("因为 A");
        when(questionService.listByIds(any())).thenReturn(List.of(q));

        List<ExamPaperQuestionVO> result = service.listQuestions(1L, student);

        assertEquals(1, result.size());
        Question projected = result.get(0).getQuestion();
        assertEquals(9L, projected.getId());
        org.junit.jupiter.api.Assertions.assertNull(projected.getCorrectAnswers(), "学生不得拿到标准答案");
        org.junit.jupiter.api.Assertions.assertNull(projected.getExplanation(), "学生不得拿到解析");
        verify(questionService).listByIds(any());
        verify(questionService, never()).getById(anyLong());
    }

    @Test
    @DisplayName("题目读取：教师拿到完整题目；题目缺失的行被跳过")
    void listQuestions_teacherGetsFullQuestionsAndSkipsMissing() {
        when(examPaperService.getById(1L)).thenReturn(paper(1L, 9L));
        ExamQuestion present = new ExamQuestion();
        present.setId(11L);
        present.setQuestionId(9L);
        present.setOrderIndex(1);
        present.setPoints(new java.math.BigDecimal("5"));
        ExamQuestion missing = new ExamQuestion();
        missing.setId(12L);
        missing.setQuestionId(404L);
        missing.setOrderIndex(2);
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(present, missing));
        Question q = question(9L);
        q.setCorrectAnswers("A");
        when(questionService.listByIds(any())).thenReturn(List.of(q));

        List<ExamPaperQuestionVO> result = service.listQuestions(1L, teacher);

        assertEquals(1, result.size(), "题目缺失的行必须跳过而不是抛异常");
        assertEquals("A", result.get(0).getQuestion().getCorrectAnswers());
    }

    @Test
    @DisplayName("题目读取：试卷不存在时 404")
    void listQuestions_paperMissing_404() {
        when(examPaperService.getById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.listQuestions(1L, teacher));

        assertEquals(ApiCode.NOT_FOUND, ex.getCode());
        verify(examQuestionService, never()).list(any(Wrapper.class));
    }

    // ------------------------------------------------ 列表

    @Test
    @DisplayName("列表：学生强制只看已发布；courseName 批量回填")
    void listByParams_studentOnlyPublished_andCourseNamesFilled() {
        ExamPaper p = paper(1L, 9L);
        Page<ExamPaper> page = new Page<>(1, 10);
        page.setRecords(List.of(p));
        page.setTotal(1);
        when(examPaperService.page(any(), any())).thenReturn(page);
        Course course = new Course();
        course.setId(9L);
        course.setName("数据结构");
        when(courseService.listByIds(any())).thenReturn(List.of(course));

        Page<ExamPaper> result = service.listByParams(1, 10, 9L, null, "DRAFT", "数据", student);

        assertEquals("数据结构", result.getRecords().get(0).getCourseName(), "courseName 必须回填");
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(examPaperService).page(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        java.util.Collection<Object> params = captor.getValue() instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper
                ? ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) captor.getValue())
                    .getParamNameValuePairs().values()
                : java.util.List.of();
        assertTrue(sql.contains("status"), "学生列表必须带 status 过滤，实际 SQL 片段=" + sql);
        assertTrue(params.contains("PUBLISHED"), "学生列表的状态过滤值必须是 PUBLISHED，实际参数=" + params);
    }

    // ------------------------------------------------ 组卷 / 复制

    @Test
    @DisplayName("自动组卷：按目标课程授权后调服务层")
    void autoCompose_checksComposePermission() {
        AutoComposeDTO dto = new AutoComposeDTO();
        dto.setCourseId(9L);
        when(examPaperService.autoComposeExamPaper(eq(dto), eq(20L))).thenReturn(paper(2L, 9L));

        ExamPaper result = service.autoCompose(dto, 20L, teacher);

        assertEquals(2L, result.getId());
        verify(examPaperAccessControl).assertCanCompose(eq(9L), eq(teacher));
    }

    @Test
    @DisplayName("复制：源卷不可管理时拒绝复制（否则可借复制拿到他人标准答案）")
    void copy_requiresManageOnSource() {
        when(examPaperService.getById(1L)).thenReturn(paper(1L, 9L));
        org.mockito.Mockito.doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权复制"))
                .when(examPaperAccessControl).assertCanManage(any(), eq(teacher));

        assertThrows(BusinessException.class, () -> service.copy(1L, "副本", teacher));
        verify(examPaperService, never()).copyExamPaper(anyLong(), anyString());
    }

    // ------------------------------------------------ helpers

    private static ExamPaperSaveDTO saveDTO(Long courseId, String visibility) {
        ExamPaperSaveDTO dto = new ExamPaperSaveDTO();
        dto.setTitle("期中测验");
        dto.setCourseId(courseId);
        dto.setVisibility(visibility);
        return dto;
    }

    private static ExamPaper paper(Long id, Long courseId) {
        ExamPaper paper = new ExamPaper();
        paper.setId(id);
        paper.setCourseId(courseId);
        return paper;
    }

    private static Question question(Long id) {
        Question q = new Question();
        q.setId(id);
        q.setContent("题干");
        return q;
    }
}
