package com.aicourse.config;

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

    private static User user(Long id, String username, String password, boolean active) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setIsActive(active);
        return user;
    }
}
