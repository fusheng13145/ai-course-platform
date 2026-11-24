package com.aicourse.application.course;

import com.aicourse.common.ApiCode;
import com.aicourse.dto.TaskSaveDTO;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.ActivityVisibilityGuard;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.ActivityTargetService;
import com.aicourse.service.TaskService;
import com.aicourse.vo.TaskVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 作业任务用例编排（3E-04A）。
 *
 * <p>本用例盯三件事，全部是改造前靠「每个端点自己记得写」维持的口径：</p>
 * <ol>
 *   <li>8 个写/查端点的守卫顺序固定为「取任务 404 → 课程管理权 403」，且不存在的任务不触达 ACL；</li>
 *   <li>归属与状态服务端写入（{@code creatorId/status/isPublished}）；</li>
 *   <li>更新时未触达目标教学班就<strong>不重写目标表</strong>（否则「只改标题」会静默清空教学班）。</li>
 * </ol>
 */
class TaskApplicationServiceTest {

    private TaskService taskService;
    private CourseAccessControl courseAccessControl;
    private ActivityVisibilityGuard activityVisibilityGuard;
    private ActivityTargetService activityTargetService;

    private TaskApplicationService service;
    private Authentication teacher;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        courseAccessControl = mock(CourseAccessControl.class);
        activityVisibilityGuard = mock(ActivityVisibilityGuard.class);
        activityTargetService = mock(ActivityTargetService.class);
        service = new TaskApplicationService(taskService, courseAccessControl,
                activityVisibilityGuard, activityTargetService);
        teacher = new UsernamePasswordAuthenticationToken(7L, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Task.class);
    }

    // ------------------------------------------------ 查询

    @Test
    @DisplayName("列表：学生条件进 wrapper（status=PUBLISHED），教学班范围交给守卫追加")
    void listByParams_studentSeesPublishedOnly() {
        when(courseAccessControl.isStudent(teacher)).thenReturn(true);
        when(taskService.page(any(), ArgumentMatchers.<Wrapper<Task>>any())).thenReturn(pageOf(new Task()));

        service.listByParams(1, 10, 1L, 2L, teacher);

        ArgumentCaptor<Wrapper<Task>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskService).page(any(), captor.capture());
        Collection<Object> params = wrapperParams(captor.getValue());
        assertTrue(params.contains("PUBLISHED"), "学生必须只看已发布，实际=" + params);
        verify(courseAccessControl).assertCanReadCourse(eq(1L), any());
        verify(activityVisibilityGuard).applyTaskScope(any(), any(), eq(2L));
    }

    @Test
    @DisplayName("列表：不传 courseId 时不加课程条件；教师不受 PUBLISHED 限制")
    void listByParams_teacherHasNoStatusFilter() {
        when(courseAccessControl.isStudent(teacher)).thenReturn(false);
        when(taskService.page(any(), ArgumentMatchers.<Wrapper<Task>>any())).thenReturn(pageOf(new Task()));

        service.listByParams(1, 10, null, null, teacher);

        verify(courseAccessControl, never()).assertCanReadCourse(any(), any());
        ArgumentCaptor<Wrapper<Task>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskService).page(any(), captor.capture());
        assertTrue(wrapperParams(captor.getValue()).isEmpty(), "教师列表不应带任何过滤值");
    }

    @Test
    @DisplayName("详情：走可见性守卫（学生可达）")
    void detail_usesVisibilityGuard() {
        Task task = task(1L, 1L);
        when(taskService.getById(1L)).thenReturn(task);

        assertEquals(1L, service.detail(1L, teacher).getId().longValue());
        verify(activityVisibilityGuard).assertVisibleTask(task, teacher);
        verify(courseAccessControl, never()).assertCanManageCourse(any(), any());
    }

    // ------------------------------------------------ 守卫顺序

    @Test
    @DisplayName("写面：任务不存在返回 404，且不触达课程管理权校验")
    void manage_guards_missingTaskIs404() {
        when(taskService.getById(1L)).thenReturn(null);

        BusinessException delete = assertThrows(BusinessException.class, () -> service.delete(1L, teacher));
        assertEquals(ApiCode.NOT_FOUND, delete.getCode());
        BusinessException publish = assertThrows(BusinessException.class, () -> service.publish(1L, teacher));
        assertEquals(ApiCode.NOT_FOUND, publish.getCode());
        BusinessException stats = assertThrows(BusinessException.class, () -> service.completionStats(1L, teacher));
        assertEquals(ApiCode.NOT_FOUND, stats.getCode());

        verify(courseAccessControl, never()).assertCanManageCourse(any(), any());
        verify(taskService, never()).removeById(1L);
        verify(taskService, never()).publishTask(1L);
        verify(taskService, never()).getTaskCompletionStats(1L);
    }

    @Test
    @DisplayName("发布：先判管理员只读，再取任务，再判课程管理权")
    void publish_rejectsAdminBeforeAnythingElse() {
        when(taskService.getById(1L)).thenReturn(task(1L, 3L));
        when(taskService.publishTask(1L)).thenReturn(task(1L, 3L));

        service.publish(1L, teacher);

        InOrder order = inOrder(activityVisibilityGuard, taskService, courseAccessControl);
        order.verify(activityVisibilityGuard).assertNotAdmin(teacher);
        order.verify(taskService).getById(1L);
        order.verify(courseAccessControl).assertCanManageCourse(eq(3L), any());
        order.verify(taskService).publishTask(1L);
    }

    @Test
    @DisplayName("目标教学班：管理员先 403，且不触达任务查询")
    void updateTargets_adminRejectedBeforeLoad() {
        org.mockito.Mockito.doThrow(new BusinessException(ApiCode.FORBIDDEN, "管理员只读"))
                .when(activityVisibilityGuard).assertNotAdmin(teacher);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.updateTargets(1L, new com.aicourse.dto.ActivityTargetDTO(), teacher));
        assertEquals(ApiCode.FORBIDDEN, e.getCode());
        verify(taskService, never()).getById(1L);
    }

    // ------------------------------------------------ 创建

    @Test
    @DisplayName("创建：creatorId 取登录身份、status 固定 DRAFT、isPublished 缺省 false")
    void create_stampsServerSideFields() {
        TaskSaveDTO dto = new TaskSaveDTO();
        dto.setTitle("作业1");
        dto.setCourseId(3L);
        when(activityTargetService.normalizeAndValidate(isNull(), eq(3L), any(), any(), eq(false)))
                .thenReturn("ALL");

        TaskVO vo = service.create(dto, teacher);

        assertEquals(7L, vo.getCreatorId().longValue());
        assertEquals("DRAFT", vo.getStatus());
        assertFalse(vo.getIsPublished());
        InOrder order = inOrder(courseAccessControl, taskService);
        order.verify(courseAccessControl).assertCanManageCourse(eq(3L), any());
        order.verify(taskService).save(any());
    }

    @Test
    @DisplayName("创建：指定 CLASSES 可见范围时管理员 403（不代教师配教学班）")
    void create_classesVisibilityRejectsAdmin() {
        TaskSaveDTO dto = new TaskSaveDTO();
        dto.setCourseId(3L);
        dto.setVisibility("CLASSES");
        when(activityTargetService.normalizeAndValidate(isNull(), eq(3L), any(), any(), eq(false)))
                .thenReturn("CLASSES");

        service.create(dto, teacher);

        verify(activityVisibilityGuard).assertNotAdmin(teacher);
    }

    // ------------------------------------------------ 更新

    @Test
    @DisplayName("更新：courseId 沿用原任务课程；未触达目标表时既不重算也不重写")
    void update_keepsCourseAndSkipsTargetsWhenUntouched() {
        TaskSaveDTO dto = new TaskSaveDTO();
        dto.setTitle("新标题");
        when(taskService.getById(1L)).thenReturn(task(1L, 3L));

        service.update(1L, dto, teacher);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getId().longValue());
        assertEquals(3L, captor.getValue().getCourseId().longValue());
        verify(activityTargetService, never()).replaceTaskTargets(any(), any(), any());
        verify(activityTargetService, never()).normalizeAndValidate(any(), any(), any(), any(), anyBoolean());
        // 目标教学班未触达 → 不触发「管理员不得改目标」的判定
        verify(activityVisibilityGuard, never()).assertNotAdmin(any());
    }

    @Test
    @DisplayName("更新：触及目标表时用「请求值优先、否则沿用原可见性」重算并重写")
    void update_touchesTargetsOnlyWhenRequested() {
        TaskSaveDTO dto = new TaskSaveDTO();
        dto.setTeachingClassIds(List.of(11L));
        Task existing = task(1L, 3L);
        existing.setVisibility("ALL");
        when(taskService.getById(1L)).thenReturn(existing);
        when(activityTargetService.normalizeAndValidate(eq(1L), eq(3L), eq("ALL"), eq(List.of(11L)), eq(false)))
                .thenReturn("CLASSES");

        service.update(1L, dto, teacher);

        verify(activityTargetService).replaceTaskTargets(1L, "CLASSES", List.of(11L));
        verify(activityVisibilityGuard).assertNotAdmin(teacher);
    }

    // ------------------------------------------------ 截止日期 / 复制

    @Test
    @DisplayName("延长截止日期：空值 400，且不触达任务查询")
    void extendDeadline_blankIs400() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.extendDeadline(1L, "  ", "理由", teacher));
        assertEquals(ApiCode.BAD_REQUEST, e.getCode());
        verify(taskService, never()).getById(1L);
    }

    @Test
    @DisplayName("延长截止日期：格式非法 400（不是 500），且不触达任务查询")
    void extendDeadline_badFormatIs400() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.extendDeadline(1L, "不是日期", "理由", teacher));
        assertEquals(ApiCode.BAD_REQUEST, e.getCode());
        verify(taskService, never()).extendDeadline(any(), any(), any());
    }

    @Test
    @DisplayName("延长截止日期：宽松格式可解析（与全局反序列化同口径）")
    void extendDeadline_acceptsLenientFormat() {
        when(taskService.getById(1L)).thenReturn(task(1L, 3L));
        when(taskService.extendDeadline(eq(1L), any(), eq("设备故障"))).thenReturn(task(1L, 3L));

        service.extendDeadline(1L, "2026-11-30 23:59:59", "设备故障", teacher);

        verify(taskService).extendDeadline(eq(1L), any(), eq("设备故障"));
        verify(courseAccessControl).assertCanManageCourse(eq(3L), any());
    }

    @Test
    @DisplayName("复制：先判可管理，再复制")
    void copy_authorizesBeforeCopying() {
        when(taskService.getById(1L)).thenReturn(task(1L, 3L));
        when(taskService.copyTask(eq(1L), eq("副本"))).thenReturn(task(2L, 3L));

        assertEquals(2L, service.copy(1L, "副本", teacher).getId().longValue());
        InOrder order = inOrder(taskService, courseAccessControl);
        order.verify(taskService).getById(1L);
        order.verify(courseAccessControl).assertCanManageCourse(eq(3L), any());
        order.verify(taskService).copyTask(1L, "副本");
    }

    @Test
    @DisplayName("待批阅：按登录身份查，不信任任何请求参数")
    void pendingGrading_usesPrincipalId() {
        when(taskService.getPendingGradingTasks(7L)).thenReturn(List.of(task(1L, 3L)));

        assertEquals(1, service.pendingGrading(teacher).size());
        verify(taskService, times(1)).getPendingGradingTasks(7L);
    }

    // ------------------------------------------------ helpers

    private static Task task(Long id, Long courseId) {
        Task t = new Task();
        t.setId(id);
        t.setCourseId(courseId);
        return t;
    }

    private static Page<Task> pageOf(Task record) {
        Page<Task> page = new Page<>(1, 10);
        page.setRecords(List.of(record));
        page.setTotal(1);
        return page;
    }

    private static Collection<Object> wrapperParams(Wrapper<?> wrapper) {
        AbstractWrapper<?, ?, ?> aw = (AbstractWrapper<?, ?, ?>) wrapper;
        aw.getSqlSegment();
        return aw.getParamNameValuePairs().values();
    }
}
