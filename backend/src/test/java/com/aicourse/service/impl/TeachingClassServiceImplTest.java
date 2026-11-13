package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.security.TeachingClassAccessControl;
import com.aicourse.service.CourseService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TeachingClassServiceImpl 单元测试（V14 / WP4）。
 * 钉住：课程未通过审批时新班强制 DRAFT；有成员时不可删；名单只走 ACL 后读取。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeachingClassServiceImplTest {

    @Mock
    private CourseService courseService;
    @Mock
    private UserService userService;
    @Mock
    private CourseEnrollmentMapper courseEnrollmentMapper;
    @Mock
    private TeachingClassAccessControl accessControl;
    @Mock
    private TeachingClassMapper teachingClassMapper;

    private TeachingClassServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TeachingClass.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CourseEnrollment.class);
        service = new TeachingClassServiceImpl(courseService, userService, courseEnrollmentMapper, accessControl);
        ReflectionTestUtils.setField(service, "baseMapper", teachingClassMapper);
    }

    @Test
    @DisplayName("课程未通过审批时新建教学班强制 DRAFT")
    void createOnUnapprovedCourseIsDraft() {
        Course course = course(10L, 100L, 1L, "APPROVED", "INACTIVE");
        when(accessControl.assertCourseOwner(10L, 100L)).thenReturn(course);
        when(accessControl.assertLeadTeacherInCourseCollege(any(), any())).thenReturn(teacher(100L, 1L));
        when(teachingClassMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(teachingClassMapper.insert(any(TeachingClass.class))).thenReturn(1);

        TeachingClass created = service.create(100L, 10L, null, null, null, 50);

        assertEquals("DRAFT", created.getStatus());
        assertEquals("数据库 默认教学班", created.getName());
        assertEquals(100L, created.getLeadTeacherId());
        assertEquals("DRAFT", created.getStatus());
    }

    @Test
    @DisplayName("课程已通过且启用时新建教学班为 ACTIVE")
    void createOnApprovedCourseIsActive() {
        Course course = course(10L, 100L, 1L, "APPROVED", "ACTIVE");
        when(accessControl.assertCourseOwner(10L, 100L)).thenReturn(course);
        when(accessControl.assertLeadTeacherInCourseCollege(any(), any())).thenReturn(teacher(100L, 1L));
        when(teachingClassMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(teachingClassMapper.insert(any(TeachingClass.class))).thenReturn(1);

        assertEquals("ACTIVE", service.create(100L, 10L, null, null, null, null).getStatus());
    }

    @Test
    @DisplayName("教学班仍有名单时删除返回 409")
    void deleteWithMembersIsConflict() {
        TeachingClass teachingClass = teachingClass(30L, 10L, 100L);
        when(accessControl.requireTeachingClass(30L)).thenReturn(teachingClass);
        when(accessControl.assertCourseOwner(10L, 100L)).thenReturn(course(10L, 100L, 1L, "APPROVED", "ACTIVE"));
        when(courseEnrollmentMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(30L, 100L));
        assertEquals(ApiCode.CONFLICT, ex.getCode());
    }

    @Test
    @DisplayName("名单读取先过 ACL，再按教学班查询")
    void listMembersGoesThroughAcl() {
        when(accessControl.assertTeachingClassLead(30L, 100L)).thenReturn(teachingClass(30L, 10L, 100L));
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setId(1L);
        when(courseEnrollmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(enrollment));

        assertEquals(1, service.listMembers(30L, 100L).size());
    }

    @Test
    @DisplayName("修改为 ACTIVE 时课程未启用则 409")
    void cannotActivateWhenCourseDisabled() {
        TeachingClass teachingClass = teachingClass(30L, 10L, 100L);
        when(accessControl.requireTeachingClass(30L)).thenReturn(teachingClass);
        when(accessControl.assertCourseOwner(10L, 100L)).thenReturn(course(10L, 100L, 1L, "REJECTED", "ACTIVE"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateBasic(30L, 100L, null, null, null, "ACTIVE"));
        assertEquals(ApiCode.CONFLICT, ex.getCode());
    }

    @Test
    @DisplayName("指派主讲时写入目标教师并持久化")
    void assignLeadTeacherUpdatesEntity() {
        TeachingClass teachingClass = teachingClass(30L, 10L, 100L);
        when(accessControl.requireTeachingClass(30L)).thenReturn(teachingClass);
        when(accessControl.assertCourseOwner(10L, 100L)).thenReturn(course(10L, 100L, 1L, "APPROVED", "ACTIVE"));
        User target = teacher(200L, 1L);
        when(accessControl.assertLeadTeacherInCourseCollege(any(), any())).thenReturn(target);

        service.assignLeadTeacher(30L, 200L, 100L);

        ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
        org.mockito.Mockito.verify(teachingClassMapper).updateById(captor.capture());
        assertEquals(200L, captor.getValue().getLeadTeacherId());
    }

    private static Course course(Long id, Long teacherId, Long collegeId, String approval, String status) {
        Course course = new Course();
        course.setId(id);
        course.setName("数据库");
        course.setTeacherId(teacherId);
        course.setCollegeId(collegeId);
        course.setApprovalStatus(approval);
        course.setStatus(status);
        return course;
    }

    private static User teacher(Long id, Long collegeId) {
        User user = new User();
        user.setId(id);
        user.setRole("TEACHER");
        user.setCollegeId(collegeId);
        return user;
    }

    private static TeachingClass teachingClass(Long id, Long courseId, Long leadTeacherId) {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(id);
        teachingClass.setCourseId(courseId);
        teachingClass.setLeadTeacherId(leadTeacherId);
        teachingClass.setName("数据库 默认教学班");
        return teachingClass;
    }
}
