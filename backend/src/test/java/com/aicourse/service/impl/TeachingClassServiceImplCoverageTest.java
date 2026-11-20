package com.aicourse.service.impl;

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
import com.aicourse.vo.TeachingClassMemberVO;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TeachingClassServiceImpl 覆盖率补测（I-04 第七批）。
 *
 * <p>覆盖建班默认名/编号/容量与重名、课程启用门禁、改班字段与状态、
 * 删除名单门禁、批量发布、名单读取与成员详情兜底。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeachingClassServiceImplCoverageTest {

    @Mock private CourseService courseService;
    @Mock private UserService userService;
    @Mock private CourseEnrollmentMapper courseEnrollmentMapper;
    @Mock private TeachingClassAccessControl accessControl;
    @Mock private TeachingClassMapper teachingClassMapper;

    private TeachingClassServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> entity : List.of(TeachingClass.class, CourseEnrollment.class,
                Course.class, User.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @BeforeEach
    void setUp() {
        service = new TeachingClassServiceImpl(courseService, userService,
                courseEnrollmentMapper, accessControl);
        ReflectionTestUtils.setField(service, "baseMapper", teachingClassMapper);
    }

    private Course course(String status, String approval) {
        Course c = new Course();
        c.setId(1L);
        c.setName("计算机网络");
        c.setTeacherId(10L);
        c.setStatus(status);
        c.setApprovalStatus(approval);
        return c;
    }

    private TeachingClass tc(Long id, String name, String status) {
        TeachingClass t = new TeachingClass();
        t.setId(id);
        t.setCourseId(1L);
        t.setName(name);
        t.setStatus(status);
        return t;
    }

    @Test
    @DisplayName("listByCourse：空 ID 400，正常查询")
    void listByCourse() {
        assertThrows(BusinessException.class, () -> service.listByCourse(null));
        when(teachingClassMapper.selectList(any(Wrapper.class))).thenReturn(List.of(tc(1L, "A", "ACTIVE")));
        assertEquals(1, service.listByCourse(1L).size());
    }

    @Test
    @DisplayName("create：默认名称/默认主讲/启用课程 ACTIVE、未启用 DRAFT、编号与容量归一")
    void createDefaults() {
        when(accessControl.assertCourseOwner(1L, 5L)).thenReturn(course("ACTIVE", "APPROVED"));
        when(accessControl.assertLeadTeacherInCourseCollege(any(), eq(10L))).thenReturn(new User());
        when(teachingClassMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(teachingClassMapper.insert(any(TeachingClass.class))).thenReturn(1);

        TeachingClass created = service.create(5L, 1L, null, null, null, null);
        assertEquals("计算机网络 默认教学班", created.getName());
        assertEquals(10L, created.getLeadTeacherId());
        assertEquals("ACTIVE", created.getStatus());
        assertNull(created.getCode());

        TeachingClass draft = service.create(5L, 1L, "  一班  ", "  C1 ", 20L, 30);
        assertEquals("一班", draft.getName());
        assertEquals("C1", draft.getCode());
        assertEquals("ACTIVE", draft.getStatus());
    }

    @Test
    @DisplayName("create：课程未通过审批为 DRAFT，长名称/重名/容量非法/唯一键冲突均 400/409")
    void createGuards() {
        when(accessControl.assertCourseOwner(1L, 5L)).thenReturn(course("ACTIVE", "DRAFT"));
        when(accessControl.assertLeadTeacherInCourseCollege(any(), any())).thenReturn(new User());

        when(teachingClassMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(teachingClassMapper.insert(any(TeachingClass.class))).thenReturn(1);
        TeachingClass draft = service.create(5L, 1L, "", "", 10L, 30);
        assertEquals("DRAFT", draft.getStatus());

        assertThrows(BusinessException.class, () -> service.create(5L, 1L, "x".repeat(121), null, 10L, 30));
        assertThrows(BusinessException.class, () -> service.create(5L, 1L, "A", "x".repeat(51), 10L, 30));
        assertThrows(BusinessException.class, () -> service.create(5L, 1L, "A", null, 10L, 0));

        when(teachingClassMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.create(5L, 1L, "A", null, 10L, 30));

        when(teachingClassMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(teachingClassMapper.insert(any(TeachingClass.class))).thenThrow(new DuplicateKeyException("dup"));
        assertThrows(BusinessException.class, () -> service.create(5L, 1L, "A", null, 10L, 30));
    }

    @Test
    @DisplayName("assignLeadTeacher：回写主讲与更新时间")
    void assignLead() {
        TeachingClass t = tc(1L, "A", "ACTIVE");
        when(accessControl.requireTeachingClass(1L)).thenReturn(t);
        when(accessControl.assertCourseOwner(1L, 5L)).thenReturn(course("ACTIVE", "APPROVED"));
        User teacher = new User();
        teacher.setId(20L);
        when(accessControl.assertLeadTeacherInCourseCollege(any(), eq(20L))).thenReturn(teacher);
        when(teachingClassMapper.updateById(any(TeachingClass.class))).thenReturn(1);

        TeachingClass result = service.assignLeadTeacher(1L, 20L, 5L);
        assertEquals(20L, result.getLeadTeacherId());
        assertNotNull(result.getUpdatedAt());
        verify(teachingClassMapper).updateById(t);
    }

    @Test
    @DisplayName("updateBasic：字段全空只更新时间；重名/非法容量/非法状态/未启用启用均拒绝")
    void updateBasic() {
        TeachingClass t = tc(1L, "A", "DRAFT");
        when(accessControl.requireTeachingClass(1L)).thenReturn(t);
        when(accessControl.assertCourseOwner(1L, 5L)).thenReturn(course("ACTIVE", "APPROVED"));
        when(teachingClassMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(teachingClassMapper.updateById(any(TeachingClass.class))).thenReturn(1);

        service.updateBasic(1L, 5L, null, null, null, null);
        assertEquals("DRAFT", t.getStatus());

        service.updateBasic(1L, 5L, " 新名 ", " C2 ", 40, " active ");
        assertEquals("新名", t.getName());
        assertEquals("C2", t.getCode());
        assertEquals(40, t.getCapacity());
        assertEquals("ACTIVE", t.getStatus());

        assertThrows(BusinessException.class, () -> service.updateBasic(1L, 5L, "x".repeat(121), null, null, null));
        assertThrows(BusinessException.class, () -> service.updateBasic(1L, 5L, null, null, 0, null));
        assertThrows(BusinessException.class, () -> service.updateBasic(1L, 5L, null, null, null, "BAD"));
        when(teachingClassMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.updateBasic(1L, 5L, "A", null, null, null));

        when(teachingClassMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(accessControl.assertCourseOwner(1L, 5L)).thenReturn(course("DRAFT", "APPROVED"));
        assertThrows(BusinessException.class, () -> service.updateBasic(1L, 5L, null, null, null, "ACTIVE"));
    }

    @Test
    @DisplayName("delete：有名单 409，空名单删除")
    void delete() {
        TeachingClass t = tc(1L, "A", "ACTIVE");
        when(accessControl.requireTeachingClass(1L)).thenReturn(t);
        when(accessControl.assertCourseOwner(1L, 5L)).thenReturn(course("ACTIVE", "APPROVED"));

        when(courseEnrollmentMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.delete(1L, 5L));

        when(courseEnrollmentMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(teachingClassMapper.deleteById(1L)).thenReturn(1);
        service.delete(1L, 5L);
        verify(teachingClassMapper).deleteById(1L);
    }

    @Test
    @DisplayName("publishCourseClasses：课程未启用/无班级 409，DRAFT 批量置 ACTIVE，已 ACTIVE 不重复更新")
    void publish() {
        when(accessControl.assertCourseOwner(1L, 5L)).thenReturn(course("DRAFT", "APPROVED"));
        assertThrows(BusinessException.class, () -> service.publishCourseClasses(1L, 5L));

        Course enabled = course("ACTIVE", "APPROVED");
        when(accessControl.assertCourseOwner(1L, 5L)).thenReturn(enabled);
        when(teachingClassMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.publishCourseClasses(1L, 5L));

        when(teachingClassMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(tc(1L, "A", "DRAFT"), tc(2L, "B", "ACTIVE")))
                .thenReturn(List.of(tc(1L, "A", "ACTIVE"), tc(2L, "B", "ACTIVE")));
        when(teachingClassMapper.updateById(any(TeachingClass.class))).thenReturn(1);
        List<TeachingClass> result = service.publishCourseClasses(1L, 5L);
        assertEquals(2, result.size());
        verify(teachingClassMapper, times(1)).updateById(any(TeachingClass.class));
    }

    @Test
    @DisplayName("listMembers/listMemberDetails：名单读取、空名单、用户缺失与姓名兜底")
    void members() {
        TeachingClass t = tc(1L, "A", "ACTIVE");
        when(accessControl.assertTeachingClassLead(1L, 5L)).thenReturn(t);
        when(accessControl.assertRosterRead(1L, 5L)).thenReturn(t);
        when(courseEnrollmentMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of(enrollment(1L, 101L, "APPROVED", true),
                        enrollment(2L, 102L, "PENDING", false)));

        assertEquals(0, service.listMembers(1L, 5L).size());
        assertTrue(service.listMemberDetails(1L, 5L).isEmpty());

        User named = new User();
        named.setId(101L);
        named.setFullName("张三");
        named.setUsername("zs");
        named.setStudentId("2026001");
        User unnamed = new User();
        unnamed.setId(102L);
        unnamed.setUsername("ls");
        when(userService.listByIds(anyList())).thenReturn(List.of(named, unnamed));

        List<TeachingClassMemberVO> rows = service.listMemberDetails(1L, 5L);
        assertEquals(2, rows.size());
        assertEquals("张三", rows.get(0).getStudentName());
        assertEquals("ls", rows.get(1).getStudentName());
        assertEquals("2026001", rows.get(0).getStudentNo());
    }

    private CourseEnrollment enrollment(Long id, Long studentId, String approval, Boolean active) {
        CourseEnrollment e = new CourseEnrollment();
        e.setId(id);
        e.setStudentId(studentId);
        e.setApprovalStatus(approval);
        e.setIsActive(active);
        e.setEnrolledAt(LocalDateTime.now());
        return e;
    }
}
