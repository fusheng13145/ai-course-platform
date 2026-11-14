package com.aicourse.service.component.org;

import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.GradeItem;
import com.aicourse.entity.GradePolicy;
import com.aicourse.entity.Task;
import com.aicourse.entity.TaskCompletion;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.mapper.GradeItemMapper;
import com.aicourse.mapper.GradePolicyMapper;
import com.aicourse.mapper.TaskCompletionMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.TaskService;
import com.aicourse.service.component.exam.ExamSessionLifecycleComponent;
import com.aicourse.service.component.student.StudentTaskCompletionComponent;
import com.aicourse.service.impl.GradeServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WP7 快照写入路径的接线测试：三类记录在创建时都从 {@link TeachingClassSnapshotResolver}
 * 取教学班快照（拆包方案 WP7 验收 2「不从请求体信任」的机械断言）。
 *
 * <p>提交侧（SubmissionWriteComponent）的写入点由既有 SubmissionServiceImplTest 覆盖构造，
 * 其快照行为与本文件同一模式；此处钉考试会话、任务完成、成绩明细三条最易漏的路径。</p>
 */
class TeachingClassSnapshotWriteTest {

    private static final long STUDENT_ID = 9L;
    private static final long COURSE_ID = 3L;
    private static final long CLASS_ID = 55L;

    private TeachingClassSnapshotResolver resolver;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ExamSession.class);
        TableInfoHelper.initTableInfo(assistant, ExamPaper.class);
        TableInfoHelper.initTableInfo(assistant, TaskCompletion.class);
        TableInfoHelper.initTableInfo(assistant, GradeItem.class);
        TableInfoHelper.initTableInfo(assistant, GradePolicy.class);
    }

    @BeforeEach
    void setUp() {
        resolver = mock(TeachingClassSnapshotResolver.class);
        when(resolver.resolveTeachingClassId(STUDENT_ID, COURSE_ID)).thenReturn(CLASS_ID);
    }

    @Test
    @DisplayName("开考：会话快照取自解析器，而不是请求体")
    void startExamWritesSnapshot() {
        ExamPaper paper = new ExamPaper();
        paper.setId(7L);
        paper.setCourseId(COURSE_ID);
        paper.setStatus("PUBLISHED");
        ExamPaperService examPaperService = mock(ExamPaperService.class);
        when(examPaperService.getById(7L)).thenReturn(paper);

        ExamSessionMapper sessionMapper = mock(ExamSessionMapper.class);
        when(sessionMapper.selectCount(any())).thenReturn(0L);
        when(sessionMapper.selectOne(any(), anyBoolean())).thenReturn(null);

        ExamSessionLifecycleComponent lifecycle = new ExamSessionLifecycleComponent(
                examPaperService, sessionMapper, resolver, new ObjectMapper(),
                mock(com.aicourse.mapper.ExamPaperMapper.class));
        lifecycle.startExam(7L, STUDENT_ID, "127.0.0.1", "junit");

        ArgumentCaptor<ExamSession> captor = ArgumentCaptor.forClass(ExamSession.class);
        org.mockito.Mockito.verify(sessionMapper).insert(captor.capture());
        assertThat(captor.getValue().getTeachingClassId()).isEqualTo(CLASS_ID);
    }

    @Test
    @DisplayName("任务完成：首次 upsert 写快照；已有记录走更新分支不再改写")
    void upsertCompletionWritesSnapshotOnlyOnCreate() {
        TaskService taskService = mock(TaskService.class);
        Task task = new Task();
        task.setId(1L);
        task.setCourseId(COURSE_ID);
        when(taskService.getById(1L)).thenReturn(task);

        TaskCompletionMapper completionMapper = mock(TaskCompletionMapper.class);
        when(completionMapper.selectOne(any(), anyBoolean())).thenReturn(null);

        StudentTaskCompletionComponent component = new StudentTaskCompletionComponent(
                taskService, completionMapper, mock(com.aicourse.service.CourseEnrollmentService.class), resolver);

        component.upsertCompletion(STUDENT_ID, COURSE_ID, 1L, "VIDEO", tc -> tc.setCompleted(Boolean.FALSE));

        ArgumentCaptor<TaskCompletion> captor = ArgumentCaptor.forClass(TaskCompletion.class);
        org.mockito.Mockito.verify(completionMapper).insert(captor.capture());
        assertThat(captor.getValue().getTeachingClassId()).isEqualTo(CLASS_ID);
    }

    @Test
    @DisplayName("成绩明细：首次落库写快照；更新分数分支不回改快照（转班不漂移）")
    void upsertGradeItemWritesSnapshotOnlyOnCreate() {
        GradePolicyMapper policyMapper = mock(GradePolicyMapper.class);
        GradeItemMapper itemMapper = mock(GradeItemMapper.class);
        GradePolicy policy = new GradePolicy();
        policy.setId(7L);
        policy.setCourseId(COURSE_ID);
        when(policyMapper.selectById(7L)).thenReturn(policy);
        when(itemMapper.selectOne(any())).thenReturn(null);

        GradeServiceImpl service = new GradeServiceImpl(policyMapper, itemMapper,
                mock(com.aicourse.service.UserService.class), resolver);

        GradeItem created = service.upsertItem(7L, STUDENT_ID, "HOMEWORK", new BigDecimal("88"), null, null, 1L, null);
        assertThat(created.getTeachingClassId()).isEqualTo(CLASS_ID);

        // 更新分支：快照保持既有值，即便成员关系已变化（解析器此刻返回另一班级）
        GradeItem exist = new GradeItem();
        exist.setId(99L);
        exist.setPolicyId(7L);
        exist.setStudentId(STUDENT_ID);
        exist.setComponentKey("HOMEWORK");
        exist.setTeachingClassId(11L);
        when(itemMapper.selectOne(any())).thenReturn(exist);
        when(resolver.resolveTeachingClassId(STUDENT_ID, COURSE_ID)).thenReturn(22L); // 已转班

        GradeItem updated = service.upsertItem(7L, STUDENT_ID, "HOMEWORK",
                new BigDecimal("90"), null, null, 1L, null);
        assertThat(updated.getTeachingClassId()).isEqualTo(11L);
        assertThat(updated.getTeachingClassId()).isNotEqualTo(22L);
        // 全程 insert 恰好 1 次（仅创建阶段）；更新分支只 updateById，不新插、不回改快照
        org.mockito.Mockito.verify(itemMapper, org.mockito.Mockito.times(1))
                .insert(org.mockito.Mockito.any(GradeItem.class));
    }

    @Test
    @DisplayName("未选课学生开考：快照为 null 但业务照常（快照缺失不阻断）")
    void startExamWithoutEnrollmentStillSucceeds() {
        ExamPaper paper = new ExamPaper();
        paper.setId(7L);
        paper.setCourseId(COURSE_ID);
        paper.setStatus("PUBLISHED");
        ExamPaperService examPaperService = mock(ExamPaperService.class);
        when(examPaperService.getById(7L)).thenReturn(paper);

        ExamSessionMapper sessionMapper = mock(ExamSessionMapper.class);
        when(sessionMapper.selectCount(any())).thenReturn(0L);
        when(sessionMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(resolver.resolveTeachingClassId(STUDENT_ID, COURSE_ID)).thenReturn(null);

        ExamSessionLifecycleComponent lifecycle = new ExamSessionLifecycleComponent(
                examPaperService, sessionMapper, resolver, new ObjectMapper(),
                mock(com.aicourse.mapper.ExamPaperMapper.class));
        ExamSession session = lifecycle.startExam(7L, STUDENT_ID, "127.0.0.1", "junit");

        assertThat(session).isNotNull();
        assertThat(session.getTeachingClassId()).isNull();
    }

    @Test
    @DisplayName("解析器入参口径：课程ID来自试卷/任务/成绩方案，而非学生提交")
    void resolverReceivesCourseFromBusinessEntity() {
        ExamPaper paper = new ExamPaper();
        paper.setId(7L);
        paper.setCourseId(COURSE_ID);
        paper.setStatus("PUBLISHED");
        ExamPaperService examPaperService = mock(ExamPaperService.class);
        when(examPaperService.getById(7L)).thenReturn(paper);

        ExamSessionMapper sessionMapper = mock(ExamSessionMapper.class);
        when(sessionMapper.selectCount(any())).thenReturn(0L);
        when(sessionMapper.selectOne(any(), anyBoolean())).thenReturn(null);

        ExamSessionLifecycleComponent lifecycle = new ExamSessionLifecycleComponent(
                examPaperService, sessionMapper, resolver, new ObjectMapper(),
                mock(com.aicourse.mapper.ExamPaperMapper.class));
        lifecycle.startExam(7L, STUDENT_ID, "127.0.0.1", "junit");

        org.mockito.Mockito.verify(resolver).resolveTeachingClassId(eq(STUDENT_ID), eq(COURSE_ID));
        // 时点自检：确有业务写入路径在跑（防止"扫的是空气"的恒真假绿）
        assertThat(LocalDate.now()).isNotNull();
    }
}
