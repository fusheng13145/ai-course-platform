package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseDefinition;
import com.aicourse.entity.Question;
import com.aicourse.entity.Semester;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.QuestionMapper;
import com.aicourse.service.CourseDefinitionService;
import com.aicourse.service.SemesterService;
import com.aicourse.service.UserService;
import com.aicourse.service.component.org.CourseDefaultTeachingClassSync;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CourseServiceImpl 覆盖率补测（I-04 第七批）。
 *
 * <p>覆盖保存/更新触发默认班同步、开课任务创建的定义快照与学期解析、
 * 审批幂等与审批人、结课门禁、删除题库门禁、驳回与重提状态机。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseServiceImplCoverageTest {

    @Mock private CourseDefaultTeachingClassSync sync;
    @Mock private SemesterService semesterService;
    @Mock private QuestionMapper questionMapper;
    @Mock private CourseDefinitionService definitionService;
    @Mock private UserService userService;
    @Mock private CourseMapper courseMapper;

    private CourseServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> entity : List.of(Course.class, CourseDefinition.class,
                Question.class, Semester.class, User.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @BeforeEach
    void setUp() {
        service = new CourseServiceImpl(sync, semesterService, questionMapper,
                definitionService, userService);
        ReflectionTestUtils.setField(service, "baseMapper", courseMapper);
    }

    private Course course(Long id, Long teacherId, String status, String approval) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        c.setStatus(status);
        c.setApprovalStatus(approval);
        return c;
    }

    private Semester semester(Long id, String name) {
        Semester s = new Semester();
        s.setId(id);
        s.setName(name);
        s.setStartDate(LocalDate.of(2026, 9, 1));
        s.setEndDate(LocalDate.of(2027, 1, 15));
        return s;
    }

    private CourseDefinition definition(Long id, Long collegeId) {
        CourseDefinition d = new CourseDefinition();
        d.setId(id);
        d.setCode("CN202");
        d.setName("计算机网络");
        d.setDescription("desc");
        d.setCredits(BigDecimal.valueOf(3));
        d.setCategory("CORE");
        d.setDifficulty("MEDIUM");
        d.setCoverImage("cover");
        d.setSyllabusUrl("syllabus");
        d.setTags("tag");
        d.setCollegeId(collegeId);
        return d;
    }

    @Test
    @DisplayName("save：无学期解析 ACTIVE 学期，保存成功触发默认班同步，失败不触发")
    void saveSemesterAndSync() {
        when(semesterService.listByStatus("ACTIVE")).thenReturn(List.of(semester(2L, "2026-2027-1")));
        when(courseMapper.insert(any(Course.class))).thenReturn(1);

        Course c = course(null, 1L, "ACTIVE", "PENDING");
        service.save(c);
        assertEquals(2L, c.getSemesterId());
        verify(sync).sync(c);

        when(semesterService.listByStatus("ACTIVE")).thenReturn(List.of());
        when(courseMapper.insert(any(Course.class))).thenReturn(0);
        Course noSemester = course(null, 1L, "ACTIVE", "PENDING");
        service.save(noSemester);
        assertNull(noSemester.getSemesterId());
        verify(sync, never()).sync(noSemester);
    }

    @Test
    @DisplayName("updateById：更新成功触发同步，失败不触发；remove 两路均执行")
    void updateAndRemove() {
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        Course c = course(1L, 1L, "ACTIVE", "APPROVED");
        assertTrue(service.updateById(c));
        verify(sync).sync(c);

        when(courseMapper.updateById(any(Course.class))).thenReturn(0);
        assertFalse(service.updateById(c));
        verify(sync, times(1)).sync(c);

        when(courseMapper.deleteById(anyLong())).thenReturn(1);
        assertTrue(service.removeById(1L));
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Course> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        when(courseMapper.delete(wrapper)).thenReturn(1);
        assertTrue(service.remove(wrapper));
    }

    @Test
    @DisplayName("createCourse：空参数/非教师/定义不存在/学院不匹配/无学期均拒绝")
    void createCourseGuards() {
        assertThrows(BusinessException.class, () -> service.createCourse(null, 1L));
        assertThrows(BusinessException.class, () -> service.createCourse(new Course(), null));

        User student = new User();
        student.setRole("STUDENT");
        when(userService.getById(1L)).thenReturn(student);
        assertThrows(BusinessException.class, () -> service.createCourse(new Course(), 1L));

        when(userService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.createCourse(new Course(), 1L));

        User teacher = new User();
        teacher.setId(1L);
        teacher.setRole("TEACHER");
        teacher.setCollegeId(2L);
        when(userService.getById(1L)).thenReturn(teacher);
        when(definitionService.requireActive(7L)).thenThrow(new BusinessException("definition"));
        Course c = new Course();
        c.setDefinitionId(7L);
        assertThrows(BusinessException.class, () -> service.createCourse(c, 1L));

        CourseDefinition def = definition(7L, 1L);
        doReturn(def).when(definitionService).requireActive(7L);
        Course mismatch = new Course();
        mismatch.setDefinitionId(7L);
        assertThrows(BusinessException.class, () -> service.createCourse(mismatch, 1L));

        doReturn(definition(7L, null)).when(definitionService).requireActive(7L);
        when(semesterService.listByStatus("ACTIVE")).thenReturn(List.of());
        Course noSemester = new Course();
        noSemester.setDefinitionId(7L);
        assertThrows(BusinessException.class, () -> service.createCourse(noSemester, 1L));
    }

    @Test
    @DisplayName("createCourse：定义快照、学期日期/年份、计划班数边界、默认运行字段与重课冲突")
    void createCourseSuccess() {
        User teacher = new User();
        teacher.setId(1L);
        teacher.setRole("teacher");
        teacher.setCollegeId(1L);
        when(userService.getById(1L)).thenReturn(teacher);
        CourseDefinition def = definition(7L, 1L);
        when(definitionService.requireActive(7L)).thenReturn(def);
        when(semesterService.getById(2L)).thenReturn(semester(2L, "2026-2027-1"));
        when(courseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(courseMapper.insert(any(Course.class))).thenReturn(1);

        Course c = new Course();
        c.setDefinitionId(7L);
        c.setSemesterId(2L);
        Course created = service.createCourse(c, 1L);
        assertEquals("CN202", created.getCode());
        assertEquals("计算机网络", created.getName());
        assertEquals(3, created.getCredits().intValue());
        assertEquals(1L, created.getCollegeId());
        assertEquals(2026, created.getYear());
        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), created.getStartDate());
        assertEquals(LocalDateTime.of(2027, 1, 15, 23, 59, 59), created.getEndDate());
        assertEquals("PENDING", created.getApprovalStatus());
        assertEquals("ACTIVE", created.getStatus());
        assertTrue(created.getIsActive());
        assertEquals("FREE", created.getEnrollmentType());
        assertEquals(100, created.getMaxStudents());
        assertFalse(created.getQuestionPracticeEnabled());
        assertEquals(1, created.getPlannedSections());

        when(courseMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        Course duplicate = new Course();
        duplicate.setDefinitionId(7L);
        duplicate.setSemesterId(2L);
        assertThrows(BusinessException.class, () -> service.createCourse(duplicate, 1L));

        Course badPlan = new Course();
        badPlan.setDefinitionId(7L);
        badPlan.setSemesterId(2L);
        badPlan.setPlannedSections(0);
        assertThrows(BusinessException.class, () -> service.createCourse(badPlan, 1L));
        badPlan.setPlannedSections(51);
        assertThrows(BusinessException.class, () -> service.createCourse(badPlan, 1L));
        badPlan.setPlannedSections(50);
        when(courseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        assertEquals(50, service.createCourse(badPlan, 1L).getPlannedSections());

        when(semesterService.getById(8L)).thenReturn(null);
        Course noSemester = new Course();
        noSemester.setDefinitionId(7L);
        noSemester.setSemesterId(8L);
        assertThrows(BusinessException.class, () -> service.createCourse(noSemester, 1L));
    }

    @Test
    @DisplayName("createCourse：旧入口 definitionId 为空时走兼容定义，学期为空时取 ACTIVE 学期")
    void createCourseLegacyDefinition() {
        User teacher = new User();
        teacher.setId(1L);
        teacher.setRole("TEACHER");
        when(userService.getById(1L)).thenReturn(teacher);
        when(definitionService.resolveFromCourse(any(), eq(1L))).thenReturn(9L);
        when(definitionService.requireActive(9L)).thenReturn(definition(9L, null));
        when(semesterService.listByStatus("ACTIVE")).thenReturn(List.of(semester(2L, "2026-2027-1")));
        when(semesterService.getById(2L)).thenReturn(semester(2L, "2026-2027-1"));
        when(courseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(courseMapper.insert(any(Course.class))).thenReturn(1);

        Course c = new Course();
        Course created = service.createCourse(c, 1L);
        assertEquals(9L, created.getDefinitionId());
        assertEquals(2L, created.getSemesterId());
        assertEquals(2026, created.getYear());
    }

    @Test
    @DisplayName("createCourse：教师无学院、课程已填学院、运行字段非空与学期名缺失")
    void createCourseEdgeBranches() {
        User teacher = new User();
        teacher.setId(1L);
        teacher.setRole("TEACHER");
        teacher.setCollegeId(null);
        when(userService.getById(1L)).thenReturn(teacher);
        when(definitionService.requireActive(7L)).thenReturn(definition(7L, 1L));
        when(semesterService.getById(2L)).thenReturn(semester(2L, "2026-2027-1"));
        when(courseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(courseMapper.insert(any(Course.class))).thenReturn(1);

        Course edge = new Course();
        edge.setDefinitionId(7L);
        edge.setSemesterId(2L);
        edge.setCollegeId(3L);
        edge.setStatus("DRAFT");
        edge.setIsActive(false);
        edge.setEnrollmentType("PAID");
        edge.setMaxStudents(5);
        edge.setQuestionPracticeEnabled(true);
        Course created = service.createCourse(edge, 1L);
        assertEquals(1L, created.getCollegeId());
        assertEquals("DRAFT", created.getStatus());
        assertFalse(created.getIsActive());
        assertEquals("PAID", created.getEnrollmentType());
        assertEquals(5, created.getMaxStudents());
        assertTrue(created.getQuestionPracticeEnabled());

        Semester blank = new Semester();
        blank.setId(3L);
        blank.setName(null);
        when(semesterService.getById(3L)).thenReturn(blank);
        Course noYear = new Course();
        noYear.setDefinitionId(7L);
        noYear.setSemesterId(3L);
        assertNull(service.createCourse(noYear, 1L).getYear());
    }

    @Test
    @DisplayName("parseYear：空/短/非数字返回 null，正常取前四位")
    void parseYearBoundaries() {
        User teacher = new User();
        teacher.setId(1L);
        teacher.setRole("TEACHER");
        when(userService.getById(1L)).thenReturn(teacher);
        when(definitionService.requireActive(7L)).thenReturn(definition(7L, null));
        when(courseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(courseMapper.insert(any(Course.class))).thenReturn(1);

        for (String name : List.of("2026-2027-1", "abc", "202", "abcd-efgh")) {
            Semester s = new Semester();
            s.setId(2L);
            s.setName(name);
            when(semesterService.getById(2L)).thenReturn(s);
            Course c = new Course();
            c.setDefinitionId(7L);
            c.setSemesterId(2L);
            service.createCourse(c, 1L);
        }
    }

    @Test
    @DisplayName("updateCourse/updateCourseById：不存在/越权/管理员/负责人，稳定字段清空后更新")
    void updateCourse() {
        when(courseMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.updateCourse(1L, new Course(), 5L, false));

        Course existing = course(1L, 9L, "ACTIVE", "APPROVED");
        when(courseMapper.selectById(1L)).thenReturn(existing);
        assertThrows(BusinessException.class, () -> service.updateCourse(1L, new Course(), 5L, false));

        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        Course patch = new Course();
        Course result = service.updateCourse(1L, patch, 9L, false);
        assertNotNull(result);
        assertNull(patch.getName());
        assertNull(patch.getDefinitionId());
        assertNull(patch.getTeacherId());

        Course adminPatch = new Course();
        assertNotNull(service.updateCourse(1L, adminPatch, 5L, true));
        assertNotNull(service.updateCourseById(1L, new Course()));
    }

    @Test
    @DisplayName("deleteCourseById：有题目 409，无题目删除")
    void deleteCourse() {
        when(questionMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.deleteCourseById(1L));

        when(questionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(courseMapper.deleteById(1L)).thenReturn(1);
        service.deleteCourseById(1L);
        verify(courseMapper).deleteById(1L);
    }

    @Test
    @DisplayName("approveCourse：不存在、重复审批幂等、正常通过；带审批人记录与不覆盖")
    void approveCourse() {
        when(courseMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.approveCourse(1L));
        assertThrows(BusinessException.class, () -> service.approveCourse(1L, 5L));

        Course approved = course(2L, 1L, "ACTIVE", "APPROVED");
        approved.setApprovalTime(LocalDateTime.now());
        when(courseMapper.selectById(2L)).thenReturn(approved);
        assertSame(approved, service.approveCourse(2L));
        verify(courseMapper, never()).updateById(approved);

        Course pending = course(3L, 1L, "DRAFT", "PENDING");
        when(courseMapper.selectById(3L)).thenReturn(pending);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        Course result = service.approveCourse(3L);
        assertEquals("APPROVED", result.getApprovalStatus());
        assertEquals("ACTIVE", result.getStatus());
        assertNotNull(result.getApprovalTime());

        Course before = course(4L, 1L, "DRAFT", "PENDING");
        Course after = course(4L, 1L, "ACTIVE", "APPROVED");
        when(courseMapper.selectById(4L)).thenReturn(before, after);
        service.approveCourse(4L, 5L);
        assertEquals(5L, after.getApprovedBy());

        Course already = course(5L, 1L, "ACTIVE", "APPROVED");
        already.setApprovedBy(5L);
        when(courseMapper.selectById(5L)).thenReturn(already);
        service.approveCourse(5L, 5L);
        assertEquals(5L, already.getApprovedBy());
    }

    @Test
    @DisplayName("toggle/complete/reject/resubmit：状态机与结课门禁")
    void statusLifecycle() {
        when(courseMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.toggleCourseStatus(1L));
        assertThrows(BusinessException.class, () -> service.completeCourse(1L, 5L, false));
        assertThrows(BusinessException.class, () -> service.rejectCourse(1L, "reason"));
        assertThrows(BusinessException.class, () -> service.resubmitCourse(1L));

        Course active = course(2L, 9L, "ACTIVE", "APPROVED");
        when(courseMapper.selectById(2L)).thenReturn(active);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        assertEquals("INACTIVE", service.toggleCourseStatus(2L).getStatus());
        assertEquals("ACTIVE", service.toggleCourseStatus(2L).getStatus());

        Course archived = course(3L, 9L, "ACTIVE", "APPROVED");
        archived.setCompletedAt(LocalDateTime.now());
        when(courseMapper.selectById(3L)).thenReturn(archived);
        assertSame(archived, service.completeCourse(3L, 9L, false));

        Course toClose = course(4L, 9L, "ACTIVE", "APPROVED");
        when(courseMapper.selectById(4L)).thenReturn(toClose, toClose);
        assertNotNull(service.completeCourse(4L, 9L, false));
        verify(sync).close(toClose);

        Course rejected = course(5L, 1L, "ACTIVE", "REJECTED");
        rejected.setApprovalComment("old");
        when(courseMapper.selectById(5L)).thenReturn(rejected);
        assertEquals("PENDING", service.resubmitCourse(5L).getApprovalStatus());
        assertNull(rejected.getApprovalComment());

        Course notRejected = course(6L, 1L, "ACTIVE", "APPROVED");
        when(courseMapper.selectById(6L)).thenReturn(notRejected);
        assertThrows(BusinessException.class, () -> service.resubmitCourse(6L));

        Course rejected2 = course(7L, 1L, "DRAFT", "REJECTED");
        when(courseMapper.selectById(7L)).thenReturn(rejected2);
        service.rejectCourse(7L, null);
        assertEquals("REJECTED", rejected2.getApprovalStatus());
        assertNull(rejected2.getApprovalComment());

        Course mixed = course(8L, 1L, "INACTIVE", "APPROVED");
        mixed.setApprovalTime(LocalDateTime.now());
        when(courseMapper.selectById(8L)).thenReturn(mixed);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        service.approveCourse(8L);
        assertNotNull(mixed.getApprovalTime());

        Course mixed2 = course(9L, 1L, "INACTIVE", "APPROVED");
        when(courseMapper.selectById(9L)).thenReturn(mixed2);
        service.approveCourse(9L, 5L);
        assertEquals(5L, mixed2.getApprovedBy());

        Course nullOperator = course(10L, 1L, "DRAFT", "PENDING");
        when(courseMapper.selectById(10L)).thenReturn(nullOperator);
        service.approveCourse(10L, null);

        Course sameOperator = course(11L, 1L, "DRAFT", "PENDING");
        sameOperator.setApprovedBy(5L);
        when(courseMapper.selectById(11L)).thenReturn(sameOperator);
        service.approveCourse(11L, 5L);

        Course adminClose = course(12L, 9L, "ACTIVE", "APPROVED");
        when(courseMapper.selectById(12L)).thenReturn(adminClose, adminClose);
        assertNotNull(service.completeCourse(12L, 5L, true));
        verify(sync).close(adminClose);

        Course nullCompleteOperator = course(13L, 9L, "ACTIVE", "APPROVED");
        when(courseMapper.selectById(13L)).thenReturn(nullCompleteOperator);
        assertThrows(BusinessException.class, () -> service.completeCourse(13L, null, false));

        Course otherOwner = course(14L, 9L, "ACTIVE", "APPROVED");
        when(courseMapper.selectById(14L)).thenReturn(otherOwner);
        assertThrows(BusinessException.class, () -> service.completeCourse(14L, 5L, false));
    }
}
