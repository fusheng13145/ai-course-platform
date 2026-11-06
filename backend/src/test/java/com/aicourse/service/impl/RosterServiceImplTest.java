package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.AdminClass;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.TeachingClassAdminClass;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.AdminClassMapper;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.mapper.TeachingClassAdminClassMapper;
import com.aicourse.mapper.UserMapper;
import com.aicourse.security.TeachingClassAccessControl;
import com.aicourse.vo.RosterSyncResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RosterServiceImpl 单测（V14 / B-12-P-08 WP5）。
 *
 * <p>覆盖：行政班导入生成 ADMIN_CLASS_IMPORT、重复导入幂等、跨班冲突不覆盖、
 * 容量 409，以及重同步只软移除导入来源、不动手工/自助记录。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RosterServiceImplTest {

    @Mock
    private TeachingClassAdminClassMapper teachingClassAdminClassMapper;
    @Mock
    private AdminClassMapper adminClassMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private CourseEnrollmentMapper courseEnrollmentMapper;
    @Mock
    private TeachingClassAccessControl accessControl;

    private RosterServiceImpl service;

    private TeachingClass teachingClass;
    private Course course;
    private AdminClass adminClass;

    @BeforeEach
    void setUp() {
        service = new RosterServiceImpl(teachingClassAdminClassMapper, adminClassMapper,
                userMapper, courseEnrollmentMapper, accessControl);

        teachingClass = new TeachingClass();
        teachingClass.setId(30L);
        teachingClass.setCourseId(10L);
        teachingClass.setStatus("ACTIVE");

        course = new Course();
        course.setId(10L);
        course.setTeacherId(100L);
        course.setEnrollmentType("OPEN");

        adminClass = new AdminClass();
        adminClass.setId(5L);
        adminClass.setStatus("ACTIVE");

        when(accessControl.assertRosterManage(anyLong(), anyLong())).thenReturn(teachingClass);
        when(accessControl.requireCourse(anyLong())).thenReturn(course);
        when(adminClassMapper.selectById(5L)).thenReturn(adminClass);
    }

    @Test
    @DisplayName("导入：行政班学生生成 ADMIN_CLASS_IMPORT 记录")
    void import_newStudents_createsAdminClassImport() {
        when(teachingClassAdminClassMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectList(any())).thenReturn(List.of(student(1L), student(2L)));
        when(courseEnrollmentMapper.selectList(any())).thenReturn(List.of());

        RosterSyncResultVO result = service.importFromAdminClass(30L, 5L, 100L);

        assertEquals(2, result.getAdded());
        assertEquals(0, result.getSkipped());
        assertEquals(0, result.getRemoved());
        assertEquals(0, result.getConflicted());
        verify(courseEnrollmentMapper, times(2)).insert(any(CourseEnrollment.class));
        verify(teachingClassAdminClassMapper).insert(any(TeachingClassAdminClass.class));
    }

    @Test
    @DisplayName("导入：重复导入幂等，已在本班有效不覆盖且计入 skipped")
    void import_duplicate_isIdempotent() {
        TeachingClassAdminClass link = new TeachingClassAdminClass();
        link.setId(1L);
        link.setStatus("ACTIVE");
        when(teachingClassAdminClassMapper.selectOne(any())).thenReturn(link);
        when(userMapper.selectList(any())).thenReturn(List.of(student(1L)));
        when(courseEnrollmentMapper.selectList(any()))
                .thenReturn(List.of(enrollment(1L, 30L, "ADMIN_CLASS_IMPORT", true)));

        RosterSyncResultVO result = service.importFromAdminClass(30L, 5L, 100L);

        assertEquals(0, result.getAdded());
        assertEquals(1, result.getSkipped());
        verify(courseEnrollmentMapper, never()).insert(any(CourseEnrollment.class));
        verify(courseEnrollmentMapper, never()).updateById(any(CourseEnrollment.class));
    }

    @Test
    @DisplayName("导入：学生已在其他教学班有效选课，不静默覆盖并计入 conflicted")
    void import_conflictOtherClass_notOverwritten() {
        when(teachingClassAdminClassMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectList(any())).thenReturn(List.of(student(1L)));
        CourseEnrollment other = enrollment(1L, 31L, "SELF_ENROLL", true);
        when(courseEnrollmentMapper.selectList(any())).thenReturn(List.of(other));

        RosterSyncResultVO result = service.importFromAdminClass(30L, 5L, 100L);

        assertEquals(0, result.getAdded());
        assertEquals(1, result.getConflicted());
        assertEquals(List.of(1L), result.getConflictedStudentIds());
        verify(courseEnrollmentMapper, never()).updateById(any(CourseEnrollment.class));
        verify(courseEnrollmentMapper, never()).insert(any(CourseEnrollment.class));
        assertEquals(31L, other.getTeachingClassId());
    }

    @Test
    @DisplayName("导入：超出教学班容量返回 409")
    void import_capacityExceeded_conflict() {
        teachingClass.setCapacity(1);
        when(teachingClassAdminClassMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectList(any())).thenReturn(List.of(student(1L), student(2L)));
        when(courseEnrollmentMapper.selectList(any())).thenReturn(List.of());
        when(courseEnrollmentMapper.countActiveByTeachingClassForUpdate(30L)).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importFromAdminClass(30L, 5L, 100L));
        assertEquals(ApiCode.CONFLICT, ex.getCode());
    }

    @Test
    @DisplayName("导入：软退课记录复用时改标 ADMIN_CLASS_IMPORT 并计入 added")
    void import_revivesInactiveRow() {
        when(teachingClassAdminClassMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectList(any())).thenReturn(List.of(student(1L)));
        CourseEnrollment inactive = enrollment(1L, null, "SELF_ENROLL", false);
        when(courseEnrollmentMapper.selectList(any())).thenReturn(List.of(inactive));
        when(courseEnrollmentMapper.updateById(any(CourseEnrollment.class))).thenReturn(1);

        RosterSyncResultVO result = service.importFromAdminClass(30L, 5L, 100L);

        assertEquals(1, result.getAdded());
        assertTrue(inactive.getIsActive());
        assertEquals(30L, inactive.getTeachingClassId());
        assertEquals("ADMIN_CLASS_IMPORT", inactive.getSource());
        assertEquals(5L, inactive.getSourceAdminClassId());
        verify(courseEnrollmentMapper, never()).insert(any(CourseEnrollment.class));
    }

    @Test
    @DisplayName("重同步：离开行政班的导入记录软移除，手工/自助记录不动")
    void resync_removesOnlyImportedRows() {
        TeachingClassAdminClass link = new TeachingClassAdminClass();
        link.setId(1L);
        link.setAdminClassId(5L);
        link.setStatus("ACTIVE");
        when(teachingClassAdminClassMapper.selectList(any())).thenReturn(List.of(link));
        // 行政班当前只剩学生 1
        when(userMapper.selectList(any())).thenReturn(List.of(student(1L)));

        CourseEnrollment importedLeft = enrollment(9L, 30L, "ADMIN_CLASS_IMPORT", true);
        CourseEnrollment selfLeft = enrollment(8L, 30L, "SELF_ENROLL", true);
        CourseEnrollment stillIn = enrollment(1L, 30L, "ADMIN_CLASS_IMPORT", true);
        when(courseEnrollmentMapper.selectList(any()))
                .thenReturn(List.of(importedLeft, selfLeft, stillIn));

        RosterSyncResultVO result = service.resync(30L, 5L, 100L);

        assertEquals(1, result.getRemoved());
        assertEquals(1, result.getSkipped());
        assertFalse(importedLeft.getIsActive());
        // 自助记录保持有效
        assertTrue(selfLeft.getIsActive());
        assertNull(selfLeft.getSourceAdminClassId());
    }

    @Test
    @DisplayName("重同步：行政班新增学生计入 added")
    void resync_addsNewStudent() {
        TeachingClassAdminClass link = new TeachingClassAdminClass();
        link.setId(1L);
        link.setAdminClassId(5L);
        link.setStatus("ACTIVE");
        when(teachingClassAdminClassMapper.selectList(any())).thenReturn(List.of(link));
        when(userMapper.selectList(any())).thenReturn(List.of(student(1L), student(2L)));
        when(courseEnrollmentMapper.selectList(any()))
                .thenReturn(List.of(enrollment(1L, 30L, "ADMIN_CLASS_IMPORT", true)));

        RosterSyncResultVO result = service.resync(30L, 5L, 100L);

        assertEquals(1, result.getAdded());
        assertEquals(1, result.getSkipped());
        verify(courseEnrollmentMapper).insert(any(CourseEnrollment.class));
    }

    @Test
    @DisplayName("重同步：未导入任何行政班返回 400")
    void resync_noSource_badRequest() {
        when(teachingClassAdminClassMapper.selectList(any())).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.resync(30L, null, 100L));
        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
    }

    @Test
    @DisplayName("来源列表读取委托访问控制")
    void listSources_delegatesToAcl() {
        when(accessControl.assertRosterRead(30L, 1L)).thenReturn(teachingClass);
        when(teachingClassAdminClassMapper.selectList(any())).thenReturn(List.of());
        assertNotNull(service.listSources(30L, 1L));
    }

    private static User student(Long id) {
        User user = new User();
        user.setId(id);
        user.setRole("STUDENT");
        user.setIsActive(true);
        return user;
    }

    private static CourseEnrollment enrollment(Long studentId, Long teachingClassId, String source, boolean active) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setId(studentId + 100);
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(10L);
        enrollment.setTeachingClassId(teachingClassId);
        enrollment.setSource(source);
        enrollment.setSourceAdminClassId("ADMIN_CLASS_IMPORT".equals(source) ? 5L : null);
        enrollment.setIsActive(active);
        return enrollment;
    }
}
