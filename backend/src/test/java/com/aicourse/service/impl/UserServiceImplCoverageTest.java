package com.aicourse.service.impl;

import com.aicourse.entity.AdminClass;
import com.aicourse.entity.College;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Major;
import com.aicourse.entity.School;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.AdminClassMapper;
import com.aicourse.mapper.CollegeMapper;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.MajorMapper;
import com.aicourse.mapper.SchoolMapper;
import com.aicourse.mapper.UserMapper;
import com.aicourse.vo.UserStatsVO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserServiceImpl 覆盖率补测（I-04 第六批）。
 *
 * <p>覆盖组织名称回填的四级回退、资料更新全字段与邮箱唯一性、教师学生空选课、
 * 统计空班级、搜索条件组合与学院范围重载。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplCoverageTest {

    @Mock private UserMapper userMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private CourseEnrollmentMapper courseEnrollmentMapper;
    @Mock private SchoolMapper schoolMapper;
    @Mock private CollegeMapper collegeMapper;
    @Mock private MajorMapper majorMapper;
    @Mock private AdminClassMapper adminClassMapper;
    @InjectMocks private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("u" + id);
        user.setPassword("secret");
        user.setRole("STUDENT");
        user.setIsActive(true);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private School school(Long id, String name) {
        School school = new School();
        school.setId(id);
        school.setName(name);
        return school;
    }

    private College college(Long id, String name) {
        College college = new College();
        college.setId(id);
        college.setName(name);
        return college;
    }

    private Major major(Long id, String name) {
        Major major = new Major();
        major.setId(id);
        major.setName(name);
        return major;
    }

    private AdminClass adminClass(Long id, Long schoolId, Long collegeId, Long majorId, String name) {
        AdminClass adminClass = new AdminClass();
        adminClass.setId(id);
        adminClass.setSchoolId(schoolId);
        adminClass.setCollegeId(collegeId);
        adminClass.setMajorId(majorId);
        adminClass.setName(name);
        return adminClass;
    }

    // ===================== 组织名称回填 =====================

    @Test
    @DisplayName("资料回填：用户自身学校/学院/专业优先")
    void profile_ownOrganizationPreferred() {
        User user = user(1L);
        user.setSchoolId(11L);
        user.setCollegeId(21L);
        user.setMajorId(31L);
        user.setAdminClassId(41L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(schoolMapper.selectById(11L)).thenReturn(school(11L, "江苏科技大学"));
        when(collegeMapper.selectById(21L)).thenReturn(college(21L, "计算机学院"));
        when(majorMapper.selectById(31L)).thenReturn(major(31L, "软件工程"));
        when(adminClassMapper.selectById(41L)).thenReturn(adminClass(41L, null, null, null, "软件1班"));

        User result = userService.getUserProfile(1L);

        assertEquals("江苏科技大学", result.getSchoolName());
        assertEquals("计算机学院", result.getCollegeName());
        assertEquals("软件工程", result.getMajorName());
        assertEquals("软件1班", result.getAdminClassName());
        assertNull(result.getPassword());
    }

    @Test
    @DisplayName("资料回填：用户 ID 缺失时由行政班补学校/学院/专业")
    void profile_adminClassProvidesOrganization() {
        User user = user(2L);
        user.setAdminClassId(42L);
        when(userMapper.selectById(2L)).thenReturn(user);
        when(adminClassMapper.selectById(42L)).thenReturn(adminClass(42L, 12L, 22L, 32L, "软件2班"));
        when(schoolMapper.selectById(12L)).thenReturn(school(12L, "江苏科技大学"));
        when(collegeMapper.selectById(22L)).thenReturn(college(22L, "计算机学院"));
        when(majorMapper.selectById(32L)).thenReturn(major(32L, "软件工程"));

        User result = userService.getUserProfile(2L);

        assertEquals("江苏科技大学", result.getSchoolName());
        assertEquals("计算机学院", result.getCollegeName());
        assertEquals("软件工程", result.getMajorName());
        assertEquals("软件2班", result.getAdminClassName());
    }

    @Test
    @DisplayName("资料回填：单校部署回退唯一学校，学院/专业回退存量文本")
    void profile_singleSchoolAndTextFallback() {
        User user = user(3L);
        user.setDepartment("计算机学院");
        user.setMajor("软件工程");
        when(userMapper.selectById(3L)).thenReturn(user);
        when(schoolMapper.selectList(null)).thenReturn(List.of(school(1L, "江苏科技大学")));

        User result = userService.getUserProfile(3L);

        assertEquals("江苏科技大学", result.getSchoolName());
        assertEquals("计算机学院", result.getCollegeName());
        assertEquals("软件工程", result.getMajorName());
        assertNull(result.getAdminClassName());
    }

    @Test
    @DisplayName("资料回填：多学校不回退，空白文本不回退")
    void profile_multiSchoolAndBlankTextNoFallback() {
        User user = user(4L);
        user.setDepartment("   ");
        user.setMajor("   ");
        when(userMapper.selectById(4L)).thenReturn(user);
        when(schoolMapper.selectList(null)).thenReturn(List.of(
                school(1L, "学校A"), school(2L, "学校B")));

        User result = userService.getUserProfile(4L);

        assertNull(result.getSchoolName());
        assertNull(result.getCollegeName());
        assertNull(result.getMajorName());
    }

    @Test
    @DisplayName("资料回填：学校列表为 null 时不回退")
    void profile_nullSchoolListNoFallback() {
        User user = user(5L);
        user.setDepartment("   ");
        user.setMajor("   ");
        when(userMapper.selectById(5L)).thenReturn(user);
        when(schoolMapper.selectList(null)).thenReturn(null);

        User result = userService.getUserProfile(5L);

        assertNull(result.getSchoolName());
    }

    @Test
    @DisplayName("资料回填：行政班存在但组织 ID 全空时不取名")
    void profile_adminClassWithNullOrganizationIds() {
        User user = user(6L);
        user.setAdminClassId(46L);
        user.setDepartment("计算机学院");
        user.setMajor("软件工程");
        when(userMapper.selectById(6L)).thenReturn(user);
        when(adminClassMapper.selectById(46L)).thenReturn(adminClass(46L, null, null, null, "空组织班"));
        when(schoolMapper.selectList(null)).thenReturn(List.of());

        User result = userService.getUserProfile(6L);

        assertNull(result.getSchoolName());
        assertEquals("计算机学院", result.getCollegeName());
        assertEquals("软件工程", result.getMajorName());
        assertEquals("空组织班", result.getAdminClassName());
    }

    // ===================== 资料更新 =====================

    @Test
    @DisplayName("更新资料：全部可编辑字段落库，邮箱唯一时通过")
    void updateProfile_allFields() {
        User user = user(7L);
        user.setEmail("old@example.com");
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        User updates = new User();
        updates.setFullName(" 张三丰 ");
        updates.setPhone(" 13900139000 ");
        updates.setEmail(" new@example.com ");
        updates.setGender("男");
        updates.setAvatarUrl(" https://img.example.com/a.png ");
        updates.setBio("简介");
        updates.setMajor("软件工程");
        updates.setGrade("2023");
        updates.setDepartment("计算机学院");
        updates.setTitle("学生");
        updates.setNotificationSettings("{\"email\":true}");

        userService.updateProfile(7L, updates);

        assertEquals("张三丰", user.getFullName());
        assertEquals("13900139000", user.getPhone());
        assertEquals("new@example.com", user.getEmail());
        assertEquals("男", user.getGender());
        assertEquals("https://img.example.com/a.png", user.getAvatarUrl());
        assertEquals("简介", user.getBio());
        assertEquals("软件工程", user.getMajor());
        assertEquals("2023", user.getGrade());
        assertEquals("计算机学院", user.getDepartment());
        assertEquals("学生", user.getTitle());
        assertEquals("{\"email\":true}", user.getNotificationSettings());
    }

    @Test
    @DisplayName("更新资料：邮箱与他人冲突时抛 409")
    void updateProfile_emailConflict() {
        User user = user(8L);
        user.setEmail("old@example.com");
        when(userMapper.selectById(8L)).thenReturn(user);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        User updates = new User();
        updates.setEmail("taken@example.com");

        BusinessException error = assertThrows(BusinessException.class,
                () -> userService.updateProfile(8L, updates));
        assertTrue(error.getMessage().contains("邮箱已被使用"));
        assertEquals("old@example.com", user.getEmail());
    }

    @Test
    @DisplayName("更新资料：邮箱仅大小写不同时不触发唯一性查询")
    void updateProfile_emailCaseInsensitiveSame() {
        User user = user(9L);
        user.setEmail("Same@Example.com");
        when(userMapper.selectById(9L)).thenReturn(user);

        User updates = new User();
        updates.setEmail("same@example.com");

        userService.updateProfile(9L, updates);

        assertEquals("same@example.com", user.getEmail());
        verify(userMapper, never()).selectCount(any(Wrapper.class));
    }

    // ===================== 同学、教师学生、统计 =====================

    @Test
    @DisplayName("同班同学：用户不存在时抛异常")
    void classmates_userNotFound() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> userService.getClassmates(99L));
    }

    @Test
    @DisplayName("教师学生：课程存在但无有效选课时返回空")
    void teacherStudents_noEnrollment() {
        Course course = new Course();
        course.setId(10L);
        when(courseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(course));
        when(courseEnrollmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        List<User> students = userService.getTeacherStudents(1L);

        assertTrue(students.isEmpty());
        verify(userMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("用户统计：用户不存在抛异常")
    void stats_userNotFound() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> userService.getUserStats(99L));
    }

    @Test
    @DisplayName("用户统计：未加入行政班时不统计同学数")
    void stats_noAdminClass() {
        User user = user(10L);
        user.setAdminClassId(null);
        when(userMapper.selectById(10L)).thenReturn(user);

        UserStatsVO stats = userService.getUserStats(10L);

        assertEquals("u10", stats.getUsername());
        assertNull(stats.getClassmateCount());
        verify(userMapper, never()).selectCount(any(Wrapper.class));
    }

    // ===================== 搜索条件组合 =====================

    @Test
    @DisplayName("搜索用户：role/keyword 为空仍可分页且脱敏")
    void searchUsers_emptyFilters() {
        Page<User> page = new Page<>(1, 10);
        User user = user(11L);
        user.setPassword("secret");
        page.setRecords(List.of(user));
        when(userMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        Page<User> result = userService.searchUsers("", "", 1, 10);

        assertEquals(1, result.getRecords().size());
        assertNull(result.getRecords().get(0).getPassword());
    }

    @Test
    @DisplayName("搜索用户：role/keyword 为 null 时不加筛选条件")
    void searchUsers_nullFilters() {
        Page<User> page = new Page<>(1, 10);
        User user = user(13L);
        user.setPassword("secret");
        page.setRecords(List.of(user));
        when(userMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        Page<User> result = userService.searchUsers(null, null, 1, 10);

        assertEquals(1, result.getRecords().size());
        assertNull(result.getRecords().get(0).getPassword());
    }

    @Test
    @DisplayName("搜索用户：学院范围重载把 collegeId 传入条件")
    void searchUsersInCollege_delegatesWithCollegeId() {
        Page<User> page = new Page<>(1, 10);
        User user = user(12L);
        user.setPassword("secret");
        page.setRecords(List.of(user));
        when(userMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        Page<User> result = userService.searchUsersInCollege("张", "STUDENT", 1, 10, 8L);

        assertEquals(1, result.getRecords().size());
        assertNull(result.getRecords().get(0).getPassword());
        verify(userMapper).selectPage(any(Page.class), any(Wrapper.class));
    }
}
