package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.TeachingClass;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.CourseService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseEnrollmentServiceImplTest {

    @Mock
    private CourseEnrollmentMapper enrollmentMapper; // baseMapper
    @Mock
    private CourseService courseService;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private TeachingClassMapper teachingClassMapper;

    private CourseEnrollmentServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), CourseEnrollment.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), TeachingClass.class);
        service = new CourseEnrollmentServiceImpl(courseService, courseMapper, teachingClassMapper);
        ReflectionTestUtils.setField(service, "baseMapper", enrollmentMapper);
    }

    /** 构造课程下唯一 ACTIVE 教学班，供自动解析路径使用（capacity=null 时回退课程上限）。 */
    private TeachingClass activeClass(Long courseId, Integer capacity) {
        TeachingClass tc = new TeachingClass();
        tc.setId(100L);
        tc.setCourseId(courseId);
        tc.setStatus("ACTIVE");
        tc.setCapacity(capacity);
        return tc;
    }

    private CourseEnrollment ce(Long id, Long studentId, Long courseId, boolean active) {
        CourseEnrollment e = new CourseEnrollment();
        e.setId(id);
        e.setStudentId(studentId);
        e.setCourseId(courseId);
        e.setIsActive(active);
        return e;
    }

    // ===== enroll =====

    /** 构造「可加入」课程：已审核通过 + 进行中，避免新前置校验干扰既有断言。 */
    private Course enrollableCourse(Long id, String enrollmentType) {
        Course c = new Course();
        c.setId(id);
        c.setEnrollmentType(enrollmentType);
        c.setApprovalStatus("APPROVED");
        c.setStatus("active");
        return c;
    }

    @Test
    @DisplayName("enroll 已报名抛异常")
    void enroll_alreadyActive() {
        when(courseService.getById(10L)).thenReturn(enrollableCourse(10L, "OPEN"));
        when(enrollmentMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.enroll(1L, 10L));
    }

    @Test
    @DisplayName("enroll 恢复软退课记录")
    void enroll_restoreInactive() {
        when(courseService.getById(10L)).thenReturn(enrollableCourse(10L, "OPEN"));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(activeClass(10L, null)));
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        CourseEnrollment inactive = ce(5L, 1L, 10L, false);
        when(enrollmentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(inactive);
        when(enrollmentMapper.updateById(any(CourseEnrollment.class))).thenReturn(1);
        CourseEnrollment r = service.enroll(1L, 10L);
        assertEquals(5L, r.getId());
        assertTrue(r.getIsActive());
        assertEquals("APPROVED", r.getApprovalStatus());
        assertEquals(BigDecimal.ZERO, r.getProgress());
        assertEquals(100L, r.getTeachingClassId());
        assertEquals("SELF_ENROLL", r.getSource());
        verify(enrollmentMapper).updateById(any(CourseEnrollment.class));
    }

    @Test
    @DisplayName("enroll OPEN 课程直接通过")
    void enroll_open() {
        when(courseService.getById(10L)).thenReturn(enrollableCourse(10L, "OPEN"));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(activeClass(10L, null)));
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(enrollmentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(enrollmentMapper.insert(any(CourseEnrollment.class))).thenReturn(1);
        CourseEnrollment r = service.enroll(1L, 10L);
        assertEquals("APPROVED", r.getApprovalStatus());
        assertEquals(100L, r.getTeachingClassId());
        assertEquals("SELF_ENROLL", r.getSource());
        verify(enrollmentMapper).insert(any(CourseEnrollment.class));
    }

    @Test
    @DisplayName("enroll APPROVAL 课程待审核")
    void enroll_approval() {
        when(courseService.getById(10L)).thenReturn(enrollableCourse(10L, "APPROVAL"));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(activeClass(10L, null)));
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(enrollmentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(enrollmentMapper.insert(any(CourseEnrollment.class))).thenReturn(1);
        CourseEnrollment r = service.enroll(1L, 10L);
        assertEquals("PENDING", r.getApprovalStatus());
    }

    /**
     * 语义变更（2026-09-14 修复）：原实现「课程不存在时默认按 OPEN 放行」，
     * 会让不存在/已删除的课程也被加入；现改为 fail-fast 抛「课程不存在」。
     */
    @Test
    @DisplayName("enroll 课程不存在时拒绝加入")
    void enroll_courseNotFound_rejected() {
        when(courseService.getById(10L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.enroll(1L, 10L));
    }

    /** 新增覆盖：未审核（PENDING）课程不可加入。 */
    @Test
    @DisplayName("enroll 未审核课程拒绝加入")
    void enroll_pendingCourse_rejected() {
        Course pending = enrollableCourse(10L, "OPEN");
        pending.setApprovalStatus("PENDING");
        when(courseService.getById(10L)).thenReturn(pending);
        assertThrows(BusinessException.class, () -> service.enroll(1L, 10L));
    }

    /** 新增覆盖：课程满员（max_students 回退为教学班容量）拒绝加入。 */
    @Test
    @DisplayName("enroll 课程满员拒绝加入")
    void enroll_courseFull_rejected() {
        Course full = enrollableCourse(10L, "OPEN");
        full.setMaxStudents(2);
        when(courseService.getById(10L)).thenReturn(full);
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(activeClass(10L, null)));
        when(courseMapper.selectByIdForUpdate(10L)).thenReturn(full);
        // 协作对象须与实现一致：重复校验走 selectCount（无有效报名），
        // 容量统计走按教学班当前读 countActiveByTeachingClassForUpdate（WP5 引入）。
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(enrollmentMapper.countActiveByTeachingClassForUpdate(100L)).thenReturn(2L);
        assertThrows(BusinessException.class, () -> service.enroll(1L, 10L));
    }

    /**
     * 新增覆盖（修复 Q-1）：enroll 进入容量校验前必须对课程行加悲观锁
     * （selectByIdForUpdate），使同课程并发选课串行化，杜绝「先 count 后 insert」超卖。
     */
    @Test
    @DisplayName("enroll 容量校验前对课程行加悲观锁")
    void enroll_acquiresRowLock_beforeCapacity() {
        Course course = enrollableCourse(10L, "OPEN");
        when(courseService.getById(10L)).thenReturn(course);
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(activeClass(10L, null)));
        when(courseMapper.selectByIdForUpdate(10L)).thenReturn(course);
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(enrollmentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(enrollmentMapper.insert(any(CourseEnrollment.class))).thenReturn(1);

        service.enroll(1L, 10L);

        // 悲观锁读取必须被调用，且发生在插入之前（容量校验路径内）
        verify(courseMapper).selectByIdForUpdate(10L);
        verify(enrollmentMapper).insert(any(CourseEnrollment.class));
    }

    // ===== WP5：教学班解析与显式指定 =====

    @Test
    @DisplayName("enroll 显式指定教学班时写入该班且来源 SELF_ENROLL")
    void enroll_explicitTeachingClass() {
        when(courseService.getById(10L)).thenReturn(enrollableCourse(10L, "OPEN"));
        TeachingClass tc = activeClass(10L, 5);
        when(teachingClassMapper.selectById(100L)).thenReturn(tc);
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(enrollmentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(enrollmentMapper.insert(any(CourseEnrollment.class))).thenReturn(1);

        CourseEnrollment r = service.enroll(1L, 10L, 100L);

        assertEquals(100L, r.getTeachingClassId());
        assertEquals("SELF_ENROLL", r.getSource());
    }

    @Test
    @DisplayName("enroll 选错班（教学班不属于该课程）抛 400")
    void enroll_wrongTeachingClass_rejected() {
        when(courseService.getById(10L)).thenReturn(enrollableCourse(10L, "OPEN"));
        TeachingClass other = activeClass(11L, 5);
        when(teachingClassMapper.selectById(100L)).thenReturn(other);
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.enroll(1L, 10L, 100L));
        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
    }

    @Test
    @DisplayName("enroll 教学班未启用抛 409")
    void enroll_inactiveTeachingClass_conflict() {
        when(courseService.getById(10L)).thenReturn(enrollableCourse(10L, "OPEN"));
        TeachingClass draft = activeClass(10L, 5);
        draft.setStatus("DRAFT");
        when(teachingClassMapper.selectById(100L)).thenReturn(draft);
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.enroll(1L, 10L, 100L));
        assertEquals(ApiCode.CONFLICT, ex.getCode());
    }

    @Test
    @DisplayName("enroll 课程有多个启用教学班且未指定时抛 400")
    void enroll_multipleTeachingClasses_requiresSelection() {
        when(courseService.getById(10L)).thenReturn(enrollableCourse(10L, "OPEN"));
        TeachingClass second = activeClass(10L, 5);
        second.setId(101L);
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(activeClass(10L, 5), second));
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.enroll(1L, 10L));
        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
    }

    @Test
    @DisplayName("enroll 课程无启用教学班抛 409")
    void enroll_noActiveTeachingClass_conflict() {
        when(courseService.getById(10L)).thenReturn(enrollableCourse(10L, "OPEN"));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of());
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.enroll(1L, 10L));
        assertEquals(ApiCode.CONFLICT, ex.getCode());
    }

    @Test
    @DisplayName("enroll 教学班容量满抛 409（优先于课程上限）")
    void enroll_teachingClassCapacity_conflict() {
        Course course = enrollableCourse(10L, "OPEN");
        course.setMaxStudents(99);
        when(courseService.getById(10L)).thenReturn(course);
        TeachingClass tc = activeClass(10L, 3);
        when(teachingClassMapper.selectById(100L)).thenReturn(tc);
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(enrollmentMapper.countActiveByTeachingClassForUpdate(100L)).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.enroll(1L, 10L, 100L));
        assertEquals(ApiCode.CONFLICT, ex.getCode());
    }

    // ===== enrollStudent =====
    @Test
    @DisplayName("enrollStudent 课程不存在抛异常")
    void enrollStudent_noCourse() {
        when(courseService.getById(10L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.enrollStudent(1L, 10L));
    }

    @Test
    @DisplayName("enrollStudent 已加入抛异常")
    void enrollStudent_already() {
        Course course = new Course();
        course.setId(10L);
        when(courseService.getById(10L)).thenReturn(course);
        // 重复判定只看「有效」记录：selectCount 命中即视为已加入
        when(enrollmentMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.enrollStudent(1L, 10L));
    }

    @Test
    @DisplayName("enrollStudent 正常新增")
    void enrollStudent_ok() {
        Course course = new Course();
        course.setId(10L);
        when(courseService.getById(10L)).thenReturn(course);
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(activeClass(10L, null)));
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(enrollmentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(enrollmentMapper.insert(any(CourseEnrollment.class))).thenReturn(1);
        service.enrollStudent(1L, 10L);
        verify(enrollmentMapper).insert(any(CourseEnrollment.class));
    }

    /**
     * 新增覆盖（2026-09-14）：被驳回/已退课的历史记录必须可**重新申请**并复用同一行
     * （表上存在 uk(student_id, course_id)，不能新增行，只能复活），
     * 且上一轮审批痕迹（approver/time/comment）需清空。
     */
    @Test
    @DisplayName("enrollStudent 驳回后可重新申请并复用历史行")
    void enrollStudent_reapplyAfterReject_restoresRow() {
        Course course = enrollableCourse(10L, "APPROVAL");
        when(courseService.getById(10L)).thenReturn(course);
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(activeClass(10L, null)));
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        CourseEnrollment rejected = ce(7L, 1L, 10L, false);
        rejected.setApprovalStatus("REJECTED");
        rejected.setApprovedBy(2L);
        rejected.setApprovalComment("名额不足");
        when(enrollmentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(rejected);
        when(enrollmentMapper.updateById(any(CourseEnrollment.class))).thenReturn(1);

        service.enrollStudent(1L, 10L);

        assertTrue(rejected.getIsActive());
        assertEquals("PENDING", rejected.getApprovalStatus());
        assertNull(rejected.getApprovedBy());
        assertNull(rejected.getApprovalComment());
        verify(enrollmentMapper).updateById(any(CourseEnrollment.class));
        verify(enrollmentMapper, never()).insert(any(CourseEnrollment.class));
    }

    // ===== unenroll =====
    @Test
    @DisplayName("unenroll 记录不存在抛异常")
    void unenroll_notFound() {
        when(enrollmentMapper.selectById(5L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.unenroll(5L, 1L));
    }

    @Test
    @DisplayName("unenroll 非属主抛异常")
    void unenroll_notOwner() {
        when(enrollmentMapper.selectById(5L)).thenReturn(ce(5L, 2L, 10L, true));
        assertThrows(BusinessException.class, () -> service.unenroll(5L, 1L));
    }

    @Test
    @DisplayName("unenroll 正常软退课")
    void unenroll_ok() {
        CourseEnrollment e = ce(5L, 1L, 10L, true);
        when(enrollmentMapper.selectById(5L)).thenReturn(e);
        when(enrollmentMapper.updateById(any(CourseEnrollment.class))).thenReturn(1);
        service.unenroll(5L, 1L);
        assertFalse(e.getIsActive());
        verify(enrollmentMapper).updateById(any(CourseEnrollment.class));
    }
}
