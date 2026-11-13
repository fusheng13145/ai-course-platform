package com.aicourse.config;

import com.aicourse.entity.AdminClass;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.User;
import com.aicourse.mapper.AdminClassMapper;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.mapper.UserMapper;
import com.aicourse.security.TeachingClassAccessControl;
import com.aicourse.service.CourseService;
import com.aicourse.service.RosterService;
import com.aicourse.service.UserService;
import com.aicourse.service.impl.RosterServiceImpl;
import com.aicourse.vo.RosterSyncResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.mybatis.spring.annotation.MapperScan;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 真实库名单导入/重同步验收（V14 / B-12-P-08 WP5）。
 *
 * <p>只在显式设置 {@code AICP_REAL_DB_IT=true} 时执行，且类名以 {@code IT} 结尾，
 * 不进入 {@code mvn test} 常规套件；执行环境直连开发库 {@code ai_course_platform}。</p>
 *
 * <p>测试方法整体在 Spring 事务内运行并在结束时回滚：读写全部打在真实库，
 * 但不改变 512 条存量回填结果与教学班名单。</p>
 */
@SpringBootTest(classes = RosterRealDbIT.MinimalConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("real-db")
@EnabledIfEnvironmentVariable(named = "AICP_REAL_DB_IT", matches = "true")
@Transactional
class RosterRealDbIT {

    @Configuration
    @EnableAutoConfiguration
    @MapperScan("com.aicourse.mapper")
    @Import({RosterServiceImpl.class, TeachingClassAccessControl.class, MyMetaObjectHandler.class})
    static class MinimalConfig {
    }

    @Autowired
    private RosterService rosterService;
    @Autowired
    private TeachingClassMapper teachingClassMapper;
    @Autowired
    private AdminClassMapper adminClassMapper;
    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CourseEnrollmentMapper courseEnrollmentMapper;

    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("真实库：行政班导入幂等 + 重同步仅软移除导入名单")
    void importAndResyncAgainstRealDatabase() {
        TeachingClass teachingClass = teachingClassMapper.selectList(
                new LambdaQueryWrapper<TeachingClass>()
                        .eq(TeachingClass::getStatus, "ACTIVE")
                        .orderByAsc(TeachingClass::getId)).get(0);
        Course course = courseMapper.selectById(teachingClass.getCourseId());
        assertNotNull(course);
        when(courseService.getById(course.getId())).thenReturn(course);
        Long operatorId = teachingClass.getLeadTeacherId() != null
                ? teachingClass.getLeadTeacherId() : course.getTeacherId();

        AdminClass target = null;
        List<User> targetStudents = null;
        for (AdminClass adminClass : adminClassMapper.selectList(null)) {
            List<User> students = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .eq(User::getAdminClassId, adminClass.getId())
                    .eq(User::getRole, "STUDENT")
                    .eq(User::getIsActive, true));
            if (students.isEmpty()) {
                continue;
            }
            long enrolled = courseEnrollmentMapper.selectCount(new LambdaQueryWrapper<CourseEnrollment>()
                    .eq(CourseEnrollment::getCourseId, course.getId())
                    .eq(CourseEnrollment::getIsActive, true)
                    .in(CourseEnrollment::getStudentId, students.stream().map(User::getId).toList()));
            if (enrolled == 0) {
                target = adminClass;
                targetStudents = students;
                break;
            }
        }
        assertNotNull(target, "真实库中应存在未选该课程的行政班");

        long before = courseEnrollmentMapper.selectCount(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getTeachingClassId, teachingClass.getId())
                .eq(CourseEnrollment::getSource, "ADMIN_CLASS_IMPORT"));

        RosterSyncResultVO first = rosterService.importFromAdminClass(
                teachingClass.getId(), target.getId(), operatorId);
        assertEquals(targetStudents.size(), first.getAdded(), "首次导入应全部新增");

        RosterSyncResultVO second = rosterService.importFromAdminClass(
                teachingClass.getId(), target.getId(), operatorId);
        assertEquals(0, second.getAdded(), "重复导入不应新增");
        assertEquals(targetStudents.size(), second.getSkipped(), "重复导入应全部跳过");

        long imported = courseEnrollmentMapper.selectCount(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getTeachingClassId, teachingClass.getId())
                .eq(CourseEnrollment::getSource, "ADMIN_CLASS_IMPORT"));
        assertEquals(before + targetStudents.size(), imported, "导入记录应全部落 ADMIN_CLASS_IMPORT");

        // 模拟一名学生调离行政班（事务内变更，测试结束回滚）
        User leaving = targetStudents.get(0);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getAdminClassId, null)
                .eq(User::getId, leaving.getId()));

        RosterSyncResultVO resync = rosterService.resync(
                teachingClass.getId(), target.getId(), operatorId);
        assertEquals(1, resync.getRemoved(), "重同步应软移除调离学生");
        assertEquals(targetStudents.size() - 1, resync.getSkipped(), "其余学生应保持不变");

        long after = courseEnrollmentMapper.selectCount(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getTeachingClassId, teachingClass.getId())
                .eq(CourseEnrollment::getSource, "ADMIN_CLASS_IMPORT")
                .eq(CourseEnrollment::getIsActive, true));
        assertEquals(targetStudents.size() - 1, after, "软移除后有效导入名单应为 N-1");

        assertTrue(teachingClass.getId() > 0);
        System.out.println("[WP5 real-db] tc=" + teachingClass.getId() + ", course=" + course.getId()
                + ", adminClass=" + target.getId() + ", students=" + targetStudents.size()
                + ", added=" + first.getAdded() + ", duplicateSkipped=" + second.getSkipped()
                + ", removed=" + resync.getRemoved() + ", activeImportedAfterResync=" + after);
    }
}
