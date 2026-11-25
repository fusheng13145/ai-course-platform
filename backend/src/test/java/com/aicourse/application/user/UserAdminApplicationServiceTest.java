package com.aicourse.application.user;

import com.aicourse.dto.AdminCreateUserDTO;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.UserService;
import com.aicourse.service.UserSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * application 层用例编排的不变量测试（阶段3 DDD 边界第一批）。
 *
 * <p>这些断言刻意<b>不</b>走 MockMvc：它们验证的是「编排规则本身」——
 * 事务边界在哪、批量导入的跳过语义、旁路动作（吊销会话）失败时不能把已成功的写操作带崩。
 * 这些约束在 HTTP 层很难被观测，但一旦被改坏，症状是数据不一致而不是接口报错。</p>
 */
class UserAdminApplicationServiceTest {

    private final UserService userService = mock(UserService.class);
    private final UserSessionService userSessionService = mock(UserSessionService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminScopeGuard adminScopeGuard = mock(AdminScopeGuard.class);
    private final UserAdminApplicationService service =
            new UserAdminApplicationService(userService, userSessionService, passwordEncoder, adminScopeGuard);

    @Test
    @DisplayName("删除用户的事务边界必须在 application 层（吊销会话 + 软删同事务）")
    void deleteUser_isTransactionalBoundary() throws Exception {
        Method method = UserAdminApplicationService.class.getMethod("deleteUser", Long.class);
        assertNotNull(method.getAnnotation(Transactional.class),
                "deleteUser 必须带 @Transactional：吊销会话与软删除必须在同一事务内");

        User existing = new User();
        existing.setId(9L);
        when(userService.getById(9L)).thenReturn(existing);
        when(userSessionService.revokeAllActiveSessions(9L)).thenReturn(3);

        service.deleteUser(9L);

        verify(userSessionService).revokeAllActiveSessions(9L);
        verify(userService).removeById(9L);
    }

    @Test
    @DisplayName("管理员重置密码的事务边界必须在 application 层（改密 + 吊销会话同事务）")
    void resetPassword_isTransactionalBoundary() throws Exception {
        Method method = UserAdminApplicationService.class.getMethod("resetPassword", Long.class, String.class);
        assertNotNull(method.getAnnotation(Transactional.class),
                "resetPassword 必须带 @Transactional：密码变更与吊销会话必须同成同败");
    }

    @Test
    @DisplayName("删除前必须先吊销会话再软删（顺序错会留下「已删用户仍有有效令牌」窗口）")
    void deleteUser_revokesSessionsBeforeRemoval() {
        User existing = new User();
        existing.setId(9L);
        when(userService.getById(9L)).thenReturn(existing);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(userSessionService, userService);
        service.deleteUser(9L);

        order.verify(userSessionService).revokeAllActiveSessions(9L);
        order.verify(userService).removeById(9L);
    }

    @Test
    @DisplayName("批量导入逐行跳过：一行角色非法不中断整批，也不落库该行")
    void importUsers_skipsInvalidRowWithoutAbortingBatch() {
        AdminCreateUserDTO bad = new AdminCreateUserDTO();
        bad.setUsername("boss");
        bad.setRole("BOSS");
        bad.setPassword("Aa123456!");
        AdminCreateUserDTO good = new AdminCreateUserDTO();
        good.setUsername("ok");
        good.setRole("STUDENT");
        good.setPassword("Aa123456!");
        when(passwordEncoder.encode(any())).thenReturn("$2a$enc");

        UserAdminApplicationService.ImportOutcome outcome = service.importUsers(List.of(bad, good));

        assertEquals(1, outcome.successCount());
        assertEquals(1, outcome.failCount());
        assertEquals(2, outcome.total());
        assertEquals(1, outcome.errors().size(), "非法角色必须给出可读原因");
        assertTrue(outcome.errors().get(0).contains("角色不合法"));
        verify(userService, org.mockito.Mockito.times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("重置密码后吊销会话失败：异常必须上抛，由事务回滚密码变更")
    void resetPassword_sessionRevokeFailurePropagates() {
        User user = new User();
        user.setId(5L);
        when(userService.getById(5L)).thenReturn(user);
        when(passwordEncoder.encode(any())).thenReturn("$2a$new");
        when(userSessionService.revokeAllActiveSessions(5L)).thenThrow(new RuntimeException("redis down"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.resetPassword(5L, "Aa123456!"));
        assertEquals("redis down", ex.getMessage(), "吊销失败的原因必须原样上抛，不得被日志吞掉");
        verify(userService).updateById(any(User.class));
    }

    @Test
    @DisplayName("重置密码为空 → 400，且不触碰数据库")
    void resetPassword_rejectsEmptyPassword() {
        User user = new User();
        user.setId(5L);
        when(userService.getById(5L)).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.resetPassword(5L, ""));
        assertFalse(ex.getMessage().isEmpty());
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("院系管理员不得创建管理员账号（作用域规则在应用层，不再依赖控制器）")
    void createUser_collegeAdminCannotCreateAdmin() {
        when(adminScopeGuard.isCollegeAdmin()).thenReturn(true);
        AdminCreateUserDTO dto = new AdminCreateUserDTO();
        dto.setUsername("newadmin");
        dto.setRole("ADMIN");
        dto.setPassword("Aa123456!");

        assertThrows(BusinessException.class, () -> service.createUser(dto));
        verify(userService, never()).save(any(User.class));
    }
}
