package com.aicourse.config;

import com.aicourse.common.RoleConstants;
import com.aicourse.entity.User;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 演示账号生产加固初始器单元测试（F-02 密钥治理 P1）。
 * <p>
 * 锁定四条契约：①开关关闭时停用并改密；②已改密账号绝不覆盖；③幂等；④true+prod 拒绝启动。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("演示账号加固：DemoAccountHardeningInitializer")
class DemoAccountHardeningInitializerTest {

    private static final String CHANGED_HASH = "$2a$10$alreadyChangedByAdminHashValue000000000000000000000000";
    private static final String NEW_RANDOM_HASH = "$2a$10$newRandomisedHashValue000000000000000000000000000000";

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationArguments args;

    private MockEnvironment environment;
    private DemoAccountHardeningInitializer initializer;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        initializer = new DemoAccountHardeningInitializer(userService, passwordEncoder, environment);
    }

    @Test
    @DisplayName("开关关闭：仍为种子弱口令的账号被停用并重置为不可知口令")
    void hardensSeedWeakAccountsWhenDisabled() {
        ReflectionTestUtils.setField(initializer, "demoAccountsEnabled", false);
        User weak = user(4L, "student", SeedAccounts.SEED_WEAK_BCRYPT, true);
        User changed = user(2L, "teacher", CHANGED_HASH, true);
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any()))
                .thenReturn(List.of(weak, changed));
        when(passwordEncoder.encode(anyString())).thenReturn(NEW_RANDOM_HASH);

        initializer.run(args);

        // 弱口令账号：停用 + 口令改写
        assertThat(weak.getIsActive()).isFalse();
        assertThat(weak.getPassword()).isEqualTo(NEW_RANDOM_HASH);

        // 已改密账号：完全不动（不覆盖真实用户口令）
        assertThat(changed.getIsActive()).isTrue();
        assertThat(changed.getPassword()).isEqualTo(CHANGED_HASH);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("student");
    }

    @Test
    @DisplayName("开关开启（默认）：不写库、不改口令，仅按需告警")
    void leavesAccountsUntouchedWhenEnabled() {
        ReflectionTestUtils.setField(initializer, "demoAccountsEnabled", true);
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any()))
                .thenReturn(List.of(user(1L, "admin", SeedAccounts.SEED_WEAK_BCRYPT, true)));

        initializer.run(args);

        verify(userService, never()).updateById(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("幂等：无种子弱口令账号时不产生任何写操作")
    void hardeningIsIdempotent() {
        ReflectionTestUtils.setField(initializer, "demoAccountsEnabled", false);
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any()))
                .thenReturn(List.of(user(1L, "admin", CHANGED_HASH, false)));

        initializer.run(args);

        verify(userService, never()).updateById(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("fail-fast：开关开启且激活 prod profile 时拒绝启动")
    void refusesStartupWhenEnabledInProdProfile() {
        ReflectionTestUtils.setField(initializer, "demoAccountsEnabled", true);
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> initializer.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_ACCOUNTS_ENABLED");

        verify(userService, never()).list(ArgumentMatchers.<Wrapper<User>>any());
        verifyNoInteractions(passwordEncoder);
    }

    // ==================== 新增回归（2026-09-16）：加固不得把系统锁死 ====================

    /**
     * 回归（DR-6）：内置 admin 仍是种子弱口令、且库中只有它一个有效管理员时，
     * 加固会把它停用 → 零管理员且无法自救。此时必须 fail-fast 拒启。
     * <p>
     * 并锁定<b>执行顺序</b>：判定发生在改写之前（否则首次抛错时账号已被停用，
     * 下次启动因「已无种子弱口令账号」跳过判定 → 永久锁死）。故断言 updateById 从未调用。
     */
    @Test
    @DisplayName("fail-fast：加固将导致零管理员时拒绝启动，且不改写任何账号")
    void refusesStartupWhenHardeningWouldOrphanAdmins() {
        ReflectionTestUtils.setField(initializer, "demoAccountsEnabled", false);
        User seedAdmin = user(1L, "admin", SeedAccounts.SEED_WEAK_BCRYPT, true, RoleConstants.ADMIN);
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of(seedAdmin));
        when(userService.count(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(1L);

        assertThatThrownBy(() -> initializer.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ADMIN_INITIAL_PASSWORD");

        // 关键：先判定后动手 —— 不允许留下「已被停用」的半成品状态
        verify(userService, never()).updateById(any());
        verifyNoInteractions(passwordEncoder);
        assertThat(seedAdmin.getIsActive()).isTrue();
        assertThat(seedAdmin.getPassword()).isEqualTo(SeedAccounts.SEED_WEAK_BCRYPT);
    }

    /** 回归（DR-6）：库中另有有效 ADMIN 账号时，停用种子 admin 是安全的，应正常加固。 */
    @Test
    @DisplayName("放行：另有有效管理员时正常加固种子 admin")
    void allowsHardeningWhenAnotherAdminRemains() {
        ReflectionTestUtils.setField(initializer, "demoAccountsEnabled", false);
        User seedAdmin = user(1L, "admin", SeedAccounts.SEED_WEAK_BCRYPT, true, RoleConstants.ADMIN);
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of(seedAdmin));
        when(userService.count(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(2L);
        when(passwordEncoder.encode(anyString())).thenReturn(NEW_RANDOM_HASH);

        initializer.run(args);

        assertThat(seedAdmin.getIsActive()).isFalse();
        assertThat(seedAdmin.getPassword()).isEqualTo(NEW_RANDOM_HASH);
        verify(userService, times(1)).updateById(any());
    }

    /** 边界：候选账号中不含管理员（如仅 student 弱口令）时，不做零管理员判定、也不产生无谓查询。 */
    @Test
    @DisplayName("边界：候选不含管理员时不触发管理员判定")
    void skipsAdminGuardWhenNoAdminIsDisabled() {
        ReflectionTestUtils.setField(initializer, "demoAccountsEnabled", false);
        User weakStudent = user(4L, "student", SeedAccounts.SEED_WEAK_BCRYPT, true, RoleConstants.STUDENT);
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of(weakStudent));
        when(passwordEncoder.encode(anyString())).thenReturn(NEW_RANDOM_HASH);

        initializer.run(args);

        assertThat(weakStudent.getIsActive()).isFalse();
        verify(userService, never()).count(ArgumentMatchers.<Wrapper<User>>any());
    }

    private static User user(Long id, String username, String password, boolean active) {
        return user(id, username, password, active, RoleConstants.STUDENT);
    }

    private static User user(Long id, String username, String password, boolean active, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setIsActive(active);
        user.setRole(role);
        return user;
    }
}
