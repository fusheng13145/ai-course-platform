package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.common.RoleConstants;
import com.aicourse.dto.AdminCreateUserDTO;
import com.aicourse.dto.AdminUpdateUserDTO;
import com.aicourse.dto.UserUpdateDTO;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.UserService;
import com.aicourse.vo.UserStatsVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserController 覆盖率补测（I-04 第七批）。
 *
 * <p>直调控制器方法，覆盖用户列表/搜索学院过滤、详情与资料脱敏边界、
 * 创建与更新的白名单字段、院系管理员限制、删除/启停状态、个人资料更新。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserControllerCoverageTest {

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AdminScopeGuard adminScopeGuard;

    private UserController controller;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    @BeforeEach
    void setUp() {
        controller = new UserController(userService, passwordEncoder, adminScopeGuard);
        lenient().when(adminScopeGuard.isCollegeAdmin()).thenReturn(false);
        lenient().when(adminScopeGuard.currentCollegeFilter()).thenReturn(null);
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("encoded");
    }

    private Authentication auth(Long id) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getPrincipal()).thenReturn(id);
        return auth;
    }

    private User user(Long id, String email, String phone) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id);
        u.setEmail(email);
        u.setPhone(phone);
        u.setIsActive(true);
        return u;
    }

    @Test
    @DisplayName("list/search：校级走全量查询，院系管理员追加学院过滤")
    void listAndSearch_scope() {
        when(userService.searchUsers(isNull(), eq("STUDENT"), eq(1), eq(10)))
                .thenReturn(new Page<>(1, 10));
        ApiResponse<PagedResponse<com.aicourse.vo.UserVO>> list = controller.list(1, 10, "STUDENT", null);
        assertNotNull(list.getData());

        when(adminScopeGuard.currentCollegeFilter()).thenReturn(3L);
        when(userService.searchUsersInCollege(eq("kw"), isNull(), eq(2), eq(5), eq(3L)))
                .thenReturn(new Page<>(2, 5));
        assertNotNull(controller.search("kw", null, 2, 5).getData());
    }

    @Test
    @DisplayName("getById：本人不做脱敏、他人脱敏邮箱与手机号、用户不存在返回 null")
    void getById_masking() {
        User self = user(1L, "self@example.com", "13800138000");
        self.setPassword("raw");
        when(userService.getById(1L)).thenReturn(self);
        ApiResponse<com.aicourse.vo.UserVO> own = controller.getById(1L, auth(1L));
        assertThrows(NoSuchMethodException.class, () -> com.aicourse.vo.UserVO.class.getMethod("getPassword"),
                "UserVO 不应声明 password getter：出参层不存在该字段");
        assertEquals("self@example.com", own.getData().getEmail());

        User other = user(2L, "ab@example.com", "13800138000");
        when(userService.getById(2L)).thenReturn(other);
        ApiResponse<com.aicourse.vo.UserVO> masked = controller.getById(2L, auth(1L));
        assertEquals("a***@example.com", masked.getData().getEmail());
        assertEquals("138****8000", masked.getData().getPhone());

        when(userService.getById(3L)).thenReturn(null);
        assertNull(controller.getById(3L, auth(1L)).getData());
    }

    @Test
    @DisplayName("maskEmail/maskSensitive：单字符、无 @、null、非 11 位手机号原样保留")
    void masking_boundaries() {
        User oneChar = user(1L, "a@example.com", "1234567890");
        when(userService.getUserProfile(1L)).thenReturn(oneChar);
        ApiResponse<com.aicourse.vo.UserVO> result = controller.getProfile(1L, auth(99L));
        assertEquals("*@example.com", result.getData().getEmail());
        assertEquals("1234567890", result.getData().getPhone());

        User noAt = user(2L, "not-an-email", null);
        when(userService.getUserProfile(2L)).thenReturn(noAt);
        assertEquals("not-an-email", controller.getProfile(2L, auth(99L)).getData().getEmail());
        assertNull(controller.getProfile(2L, auth(99L)).getData().getPhone());

        User nullUser = null;
        when(userService.getUserProfile(3L)).thenReturn(nullUser);
        assertDoesNotThrow(() -> controller.getProfile(3L, auth(99L)));
    }

    @Test
    @DisplayName("classmates/stats/teacherStudents：本人、他人与统计邮箱脱敏")
    void relatedReads() {
        when(userService.getClassmates(1L)).thenReturn(List.of(user(2L, "b@x.com", null)));
        assertEquals(1, controller.getClassmates(1L, auth(1L)).getData().size());

        when(userService.getById(2L)).thenReturn(user(2L, "b@x.com", null));
        when(userService.getClassmates(2L)).thenReturn(List.of());
        assertNotNull(controller.getClassmates(2L, auth(1L)).getData());

        UserStatsVO stats = new UserStatsVO();
        stats.setEmail("ab@example.com");
        when(userService.getUserStats(1L)).thenReturn(stats);
        assertEquals("ab@example.com", controller.getUserStats(1L, auth(1L)).getData().getEmail());

        UserStatsVO otherStats = new UserStatsVO();
        otherStats.setEmail("ab@example.com");
        when(userService.getUserStats(2L)).thenReturn(otherStats);
        assertEquals("a***@example.com", controller.getUserStats(2L, auth(1L)).getData().getEmail());

        UserStatsVO noEmail = new UserStatsVO();
        when(userService.getUserStats(3L)).thenReturn(noEmail);
        assertNull(controller.getUserStats(3L, auth(1L)).getData().getEmail());

        when(userService.getTeacherStudents(5L)).thenReturn(List.of());
        assertNotNull(controller.getTeacherStudents(auth(5L)).getData());
    }

    @Test
    @DisplayName("create：默认启用、学院归属、行政班 0 归一、院系管理员边界")
    void create_fields() {
        AdminCreateUserDTO dto = new AdminCreateUserDTO();
        dto.setUsername("student1");
        dto.setFullName("张三");
        dto.setPhone("13800138000");
        dto.setEmail("z@example.com");
        dto.setPassword("Password123");
        dto.setRole(RoleConstants.STUDENT);
        dto.setIsActive(null);
        dto.setCollegeId(9L);
        dto.setAdminClassId(0L);

        ApiResponse<com.aicourse.vo.UserVO> created = controller.create(dto);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(Boolean.TRUE, saved.getIsActive());
        assertEquals(9L, saved.getCollegeId());
        assertNull(saved.getAdminClassId());
        verify(passwordEncoder).encode("Password123");
        assertThrows(NoSuchMethodException.class, () -> com.aicourse.vo.UserVO.class.getMethod("getPassword"),
                "UserVO 不应声明 password getter：出参层不存在该字段");

        dto.setAdminClassId(12L);
        controller.create(dto);
        verify(userService, times(2)).save(any(User.class));

        when(adminScopeGuard.isCollegeAdmin()).thenReturn(true);
        when(adminScopeGuard.currentCollegeId()).thenReturn(1L);
        dto.setRole(RoleConstants.ADMIN);
        assertThrows(BusinessException.class, () -> controller.create(dto));

        dto.setRole(RoleConstants.STUDENT);
        dto.setCollegeId(2L);
        assertThrows(BusinessException.class, () -> controller.create(dto));

        dto.setCollegeId(null);
        controller.create(dto);
        verify(userService, atLeast(3)).save(any(User.class));
    }

    @Test
    @DisplayName("update：不存在 400、院系管理员限制、全字段更新与行政班 0 显式清空")
    void update_fields() {
        when(userService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> controller.update(1L, new AdminUpdateUserDTO()));

        User existing = user(2L, "old@example.com", "13800138000");
        when(userService.getById(2L)).thenReturn(existing);
        when(userService.getById(2L)).thenReturn(existing, existing);

        AdminUpdateUserDTO dto = new AdminUpdateUserDTO();
        dto.setUsername("newName");
        dto.setFullName("新姓名");
        dto.setPhone("13900139000");
        dto.setEmail("new@example.com");
        dto.setRole(RoleConstants.TEACHER);
        dto.setIsActive(false);
        dto.setAvatarUrl("https://example.com/a.png");
        dto.setBio("bio");
        dto.setCollegeId(3L);
        dto.setAdminClassId(0L);
        controller.update(2L, dto);

        AdminUpdateUserDTO empty = new AdminUpdateUserDTO();
        when(userService.getById(3L)).thenReturn(user(3L, null, null));
        controller.update(3L, empty);

        AdminUpdateUserDTO nullUpdated = new AdminUpdateUserDTO();
        when(userService.getById(4L)).thenReturn(user(4L, null, null));
        when(userService.getById(4L)).thenReturn(user(4L, null, null), (User) null);
        assertNull(controller.update(4L, nullUpdated).getData());

        when(adminScopeGuard.isCollegeAdmin()).thenReturn(true);
        when(adminScopeGuard.currentCollegeId()).thenReturn(1L);
        AdminUpdateUserDTO adminDto = new AdminUpdateUserDTO();
        adminDto.setRole(RoleConstants.ADMIN);
        assertThrows(BusinessException.class, () -> controller.update(2L, adminDto));

        AdminUpdateUserDTO moveDto = new AdminUpdateUserDTO();
        moveDto.setCollegeId(2L);
        assertThrows(BusinessException.class, () -> controller.update(2L, moveDto));

        AdminUpdateUserDTO collegeNull = new AdminUpdateUserDTO();
        when(userService.getById(5L)).thenReturn(user(5L, null, null));
        controller.update(5L, collegeNull);
        verify(userService, atLeastOnce()).update(any(Wrapper.class));
    }

    @Test
    @DisplayName("delete/toggleStatus：不存在也执行删除、状态缺省取反、显式状态优先")
    void deleteAndToggle() {
        when(userService.getById(1L)).thenReturn(null);
        assertDoesNotThrow(() -> controller.delete(1L));
        verify(userService).removeById(1L);

        when(userService.getById(2L)).thenReturn(user(2L, null, null));
        controller.delete(2L);
        verify(adminScopeGuard).assertUserManageable(any(User.class));

        assertThrows(BusinessException.class, () -> controller.toggleStatus(9L, Map.of()));

        User nullActive = user(3L, null, null);
        nullActive.setIsActive(null);
        when(userService.getById(3L)).thenReturn(nullActive);
        controller.toggleStatus(3L, Map.of());
        assertEquals(Boolean.TRUE, nullActive.getIsActive());

        User active = user(4L, null, null);
        when(userService.getById(4L)).thenReturn(active);
        controller.toggleStatus(4L, Map.of());
        assertEquals(Boolean.FALSE, active.getIsActive());

        controller.toggleStatus(4L, Map.of("status", true));
        assertEquals(Boolean.TRUE, active.getIsActive());
    }

    @Test
    @DisplayName("profile/updateProfile：全空字段不写、全字段写入、头像别名生效")
    void profileAndUpdate() {
        User current = user(1L, "a@x.com", null);
        when(userService.getUserProfile(1L)).thenReturn(current);
        assertNotNull(controller.profile(auth(1L)).getData());

        controller.updateProfile(auth(1L), new UserUpdateDTO());
        verify(userService).updateProfile(eq(1L), any(User.class));

        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setFullName("李四");
        dto.setPhone("13800138000");
        dto.setEmail("l@example.com");
        dto.setGender("MALE");
        dto.setAvatar("https://example.com/a.png");
        dto.setBio("bio");
        controller.updateProfile(auth(2L), dto);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService, times(1)).updateProfile(eq(2L), captor.capture());
        User patch = captor.getValue();
        assertEquals("李四", patch.getFullName());
        assertEquals("MALE", patch.getGender());
        assertEquals("https://example.com/a.png", patch.getAvatarUrl());
    }
}
