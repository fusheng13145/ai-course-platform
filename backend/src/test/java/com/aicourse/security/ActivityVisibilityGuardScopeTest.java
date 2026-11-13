package com.aicourse.security;

import com.aicourse.common.ActivityVisibility;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamTeachingClass;
import com.aicourse.entity.Task;
import com.aicourse.entity.TaskTeachingClass;
import com.aicourse.entity.TeachingClass;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamTeachingClassMapper;
import com.aicourse.mapper.TaskTeachingClassMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link ActivityVisibilityGuard} 的**列表作用域与失败分支**单测（2026-11-06 验收批补）。
 * <p>
 * 与同包 {@code ActivityVisibilityGuardTest}（V15／WP6 验收第 3、4 条）的分工：那一组钉的是
 * 「师生 × COURSE/CLASSES 详情可见性矩阵」，本组钉的是它没覆盖的两块——
 * ① 列表收敛（{@code applyTaskScope}/{@code applyExamPaperScope}）：**无可见范围时必须落成
 * {@code 1 = 0}，绝不能退化成「不加条件」＝全表可见**；② 详情路径的失败分支
 * （实体不存在 404、任务未发布、选课行没有教学班ID、教师既无课又无班）。
 * 守卫本体只有一份实现，本类不复制其判定逻辑，只从外部驱动它。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("可见性守卫·列表作用域：ActivityVisibilityGuard")
class ActivityVisibilityGuardScopeTest {

    @Mock private CourseService courseService;
    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private TeachingClassMapper teachingClassMapper;
    @Mock private TaskTeachingClassMapper taskTeachingClassMapper;
    @Mock private ExamTeachingClassMapper examTeachingClassMapper;

    private ActivityVisibilityGuard guard;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Task.class);
        TableInfoHelper.initTableInfo(assistant, ExamPaper.class);
        TableInfoHelper.initTableInfo(assistant, TeachingClass.class);
        TableInfoHelper.initTableInfo(assistant, CourseEnrollment.class);
        TableInfoHelper.initTableInfo(assistant, TaskTeachingClass.class);
        TableInfoHelper.initTableInfo(assistant, ExamTeachingClass.class);
        guard = new ActivityVisibilityGuard(courseService, enrollmentService, teachingClassMapper,
                taskTeachingClassMapper, examTeachingClassMapper);
        when(courseService.getById(1L)).thenReturn(course(1L, 7L));
    }

    private static Authentication as(Long userId, String role) {
        return new UsernamePasswordAuthenticationToken(userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static Course course(Long id, Long teacherId) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        return c;
    }

    private static Task task(String visibility, String status) {
        Task t = new Task();
        t.setId(100L);
        t.setCourseId(1L);
        t.setStatus(status);
        t.setVisibility(visibility);
        return t;
    }

    private static ExamPaper paper(String visibility) {
        ExamPaper p = new ExamPaper();
        p.setId(200L);
        p.setCourseId(1L);
        p.setVisibility(visibility);
        return p;
    }

    private static CourseEnrollment enrollment(Long studentId, Long courseId, Long teachingClassId) {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(studentId);
        e.setCourseId(courseId);
        e.setTeachingClassId(teachingClassId);
        e.setApprovalStatus("APPROVED");
        e.setIsActive(true);
        return e;
    }

    private static TeachingClass ledClass(Long id, Long courseId, Long leadTeacherId) {
        TeachingClass t = new TeachingClass();
        t.setId(id);
        t.setCourseId(courseId);
        t.setLeadTeacherId(leadTeacherId);
        return t;
    }

    private static TaskTeachingClass taskTarget(Long taskId, Long classId) {
        TaskTeachingClass r = new TaskTeachingClass();
        r.setTaskId(taskId);
        r.setTeachingClassId(classId);
        return r;
    }

    private static ExamTeachingClass examTarget(Long paperId, Long classId) {
        ExamTeachingClass r = new ExamTeachingClass();
        r.setExamPaperId(paperId);
        r.setTeachingClassId(classId);
        return r;
    }

    // ==================== 详情路径的失败分支 ====================

    @Test
    @DisplayName("任务／试卷实体不存在：404，不是 403（不泄露「存在但无权」）")
    void missingActivityIsNotFoundNotForbidden() {
        assertThatThrownBy(() -> guard.assertVisibleTask(null, as(5001L, RoleConstants.STUDENT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务不存在");
        assertThatThrownBy(() -> guard.assertVisibleExamPaper(null, as(5001L, RoleConstants.STUDENT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("试卷不存在");
    }

    @Test
    @DisplayName("学生读未发布任务：403 且不查选课表（先判发布再判成员，顺序即判据）")
    void studentCannotReadUnpublishedTask() {
        Task draft = task(ActivityVisibility.COURSE, "DRAFT");

        assertThatThrownBy(() -> guard.assertVisibleTask(draft, as(5001L, RoleConstants.STUDENT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务尚未发布");

        verifyNoEnrollmentLookup();
    }

    @Test
    @DisplayName("学生：已选课且任务是 COURSE 范围 ⇒ 放行（不必命中教学班指派）")
    void studentSeesCourseScopedTaskWithEnrollment() {
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(5001L, 1L, 50L)));

        assertThatCode(() -> guard.assertVisibleTask(task(ActivityVisibility.COURSE, "PUBLISHED"),
                as(5001L, RoleConstants.STUDENT))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("选课行没有教学班ID 时，CLASSES 任务不可见（不得用 null 去匹配指派行）")
    void nullTeachingClassIdCannotSeeClassesTask() {
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(5001L, 1L, null)));

        assertThatThrownBy(() -> guard.assertVisibleTask(task(ActivityVisibility.CLASSES, "PUBLISHED"),
                as(5001L, RoleConstants.STUDENT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未对你所在的教学班开放");
    }

    @Test
    @DisplayName("教师：非负责人且在本课程没有任何主讲班 ⇒ 403")
    void teacherWithoutAnyLedClassIsRejected() {
        when(courseService.getById(1L)).thenReturn(course(1L, 8L));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> guard.assertVisibleTask(task(ActivityVisibility.COURSE, "PUBLISHED"),
                as(7L, RoleConstants.TEACHER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权查看其他教师课程的内容");
    }

    @Test
    @DisplayName("管理员只读放行（不受 WP6 第 4 条影响，只有写面被禁）")
    void adminReadsAnyActivity() {
        assertThatCode(() -> guard.assertVisibleTask(task(ActivityVisibility.CLASSES, "PUBLISHED"),
                as(1L, RoleConstants.ADMIN))).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertNotAdmin(as(7L, RoleConstants.TEACHER))).doesNotThrowAnyException();
    }

    // ==================== 列表作用域收敛 ====================

    @Test
    @DisplayName("列表：管理员不加任何范围条件（治理面看全量）")
    void adminListIsUnfiltered() {
        LambdaQueryWrapper<Task> tasks = new LambdaQueryWrapper<>();
        guard.applyTaskScope(tasks, as(1L, RoleConstants.ADMIN), null);
        assertThat(tasks.getTargetSql()).isEmpty();

        LambdaQueryWrapper<ExamPaper> papers = new LambdaQueryWrapper<>();
        guard.applyExamPaperScope(papers, as(1L, RoleConstants.ADMIN), null);
        assertThat(papers.getTargetSql()).isEmpty();
    }

    @Test
    @DisplayName("列表：学生无 APPROVED 选课 ⇒ 1 = 0，绝不允许退化成「不过滤＝全表」")
    void studentWithoutEnrollmentsSeesNothing() {
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of());

        LambdaQueryWrapper<Task> tasks = new LambdaQueryWrapper<>();
        guard.applyTaskScope(tasks, as(5001L, RoleConstants.STUDENT), null);
        assertThat(tasks.getTargetSql()).contains("1 = 0");

        LambdaQueryWrapper<ExamPaper> papers = new LambdaQueryWrapper<>();
        guard.applyExamPaperScope(papers, as(5001L, RoleConstants.STUDENT), null);
        assertThat(papers.getTargetSql()).contains("1 = 0");
    }

    @Test
    @DisplayName("列表：选课行有课程但没有教学班ID 时，仍按课程收敛而不是判空")
    void studentWithCourseButNoClassIdStillScopedByCourse() {
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(5001L, 1L, null)));

        LambdaQueryWrapper<Task> tasks = new LambdaQueryWrapper<>();
        guard.applyTaskScope(tasks, as(5001L, RoleConstants.STUDENT), null);

        assertThat(tasks.getTargetSql()).doesNotContain("1 = 0").contains("course_id");
    }

    @Test
    @DisplayName("列表：学生收敛到本人课程，并同时带出 COURSE 可见与指派到本班的行")
    void studentListCoversOwnCoursesAndTargetedClasses() {
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(5001L, 1L, 50L)));
        when(taskTeachingClassMapper.selectList(any())).thenReturn(List.of(taskTarget(100L, 50L)));
        when(examTeachingClassMapper.selectList(any())).thenReturn(List.of(examTarget(200L, 50L)));

        LambdaQueryWrapper<Task> tasks = new LambdaQueryWrapper<>();
        guard.applyTaskScope(tasks, as(5001L, RoleConstants.STUDENT), null);
        assertThat(tasks.getTargetSql()).contains("course_id").contains("visibility");

        LambdaQueryWrapper<ExamPaper> papers = new LambdaQueryWrapper<>();
        guard.applyExamPaperScope(papers, as(5001L, RoleConstants.STUDENT), null);
        assertThat(papers.getTargetSql()).contains("course_id").contains("visibility");
    }

    @Test
    @DisplayName("列表：教师既无自建课又无主讲班 ⇒ 1 = 0")
    void teacherWithoutCoursesOrClassesSeesNothing() {
        when(courseService.list(any(Wrapper.class))).thenReturn(List.of());
        when(teachingClassMapper.selectList(any())).thenReturn(List.of());

        LambdaQueryWrapper<Task> tasks = new LambdaQueryWrapper<>();
        guard.applyTaskScope(tasks, as(7L, RoleConstants.TEACHER), null);
        assertThat(tasks.getTargetSql()).contains("1 = 0");

        LambdaQueryWrapper<ExamPaper> papers = new LambdaQueryWrapper<>();
        guard.applyExamPaperScope(papers, as(7L, RoleConstants.TEACHER), null);
        assertThat(papers.getTargetSql()).contains("1 = 0");
    }

    @Test
    @DisplayName("列表：自有课程与带班课程并存时两段条件都进 SQL（OR），不丢带班那半")
    void teacherListUnionsOwnedAndLedCourses() {
        when(courseService.list(any(Wrapper.class))).thenReturn(List.of(course(1L, 7L)));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(ledClass(51L, 2L, 7L)));
        when(taskTeachingClassMapper.selectList(any())).thenReturn(List.of(taskTarget(101L, 51L)));
        when(examTeachingClassMapper.selectList(any())).thenReturn(List.of(examTarget(201L, 51L)));

        LambdaQueryWrapper<Task> tasks = new LambdaQueryWrapper<>();
        guard.applyTaskScope(tasks, as(7L, RoleConstants.TEACHER), null);
        assertThat(tasks.getTargetSql()).contains("OR").contains("course_id");

        LambdaQueryWrapper<ExamPaper> papers = new LambdaQueryWrapper<>();
        guard.applyExamPaperScope(papers, as(7L, RoleConstants.TEACHER), null);
        assertThat(papers.getTargetSql()).contains("OR").contains("course_id");
    }

    @Test
    @DisplayName("列表：按教学班筛选时，学生不在该班 403（不能用别人的教学班 ID 换一批数据）")
    void studentCannotFilterByForeignClass() {
        when(teachingClassMapper.selectById(50L)).thenReturn(ledClass(50L, 1L, 7L));
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of());

        LambdaQueryWrapper<Task> tasks = new LambdaQueryWrapper<>();
        assertThatThrownBy(() -> guard.applyTaskScope(tasks, as(5001L, RoleConstants.STUDENT), 50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("你不在该教学班");
    }

    @Test
    @DisplayName("列表：按教学班筛选时，非本班主讲且非课程负责人的教师 403")
    void teacherCannotFilterByForeignClass() {
        when(courseService.getById(1L)).thenReturn(course(1L, 8L));
        when(teachingClassMapper.selectById(50L)).thenReturn(ledClass(50L, 1L, 9L));

        LambdaQueryWrapper<Task> tasks = new LambdaQueryWrapper<>();
        assertThatThrownBy(() -> guard.applyTaskScope(tasks, as(7L, RoleConstants.TEACHER), 50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权按其他教师的教学班筛选");
    }

    @Test
    @DisplayName("列表：教学班 ID 指向不存在的班 ⇒ 404（不静默返回空列表）")
    void unknownTeachingClassIsNotFound() {
        when(teachingClassMapper.selectById(anyLong())).thenReturn(null);

        LambdaQueryWrapper<ExamPaper> papers = new LambdaQueryWrapper<>();
        assertThatThrownBy(() -> guard.applyExamPaperScope(papers, as(7L, RoleConstants.TEACHER), 404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("教学班不存在");
    }

    @Test
    @DisplayName("列表：按本班筛选时，学生拿到的是该班课程的收敛条件而不是全量")
    void studentFilterByOwnClassIsScoped() {
        when(teachingClassMapper.selectById(50L)).thenReturn(ledClass(50L, 1L, 7L));
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(enrollment(5001L, 1L, 50L)));

        LambdaQueryWrapper<Task> tasks = new LambdaQueryWrapper<>();
        guard.applyTaskScope(tasks, as(5001L, RoleConstants.STUDENT), 50L);

        assertThat(tasks.getTargetSql()).contains("course_id").contains("visibility");
    }

    private void verifyNoEnrollmentLookup() {
        org.mockito.Mockito.verify(enrollmentService, org.mockito.Mockito.never())
                .list(any(Wrapper.class));
    }
}
