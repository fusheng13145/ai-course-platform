package com.aicourse.service.impl;

import com.aicourse.entity.VerificationCode;
import com.aicourse.mapper.VerificationCodeMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证码服务单元测试（补充版，自包含，独占类名避免与现有文件冲突）。
 * 通过 mock baseMapper 覆盖 verifyCode / cleanExpiredCodes / getTodaySendCount 的所有分支，
 * 包含邮箱与手机号两种 target、命中与未命中、过期清理有/无记录、今日计数等路径。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationCodeServiceImplTest2 {

    @Mock
    private VerificationCodeMapper baseMapper;

    private VerificationCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VerificationCodeServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);

        // 默认桩：未命中 / 无记录。各测试方法按需覆盖。
        lenient().when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        lenient().when(baseMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        lenient().when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        lenient().when(baseMapper.updateById(any(VerificationCode.class))).thenReturn(1);
        lenient().when(baseMapper.delete(any(Wrapper.class))).thenReturn(1);
    }

    private VerificationCode vc(Long id, String target, String code, boolean isUsed) {
        VerificationCode v = new VerificationCode();
        v.setId(id);
        v.setEmail(target.contains("@") ? target : null);
        v.setPhone(target.contains("@") ? null : target);
        v.setCode(code);
        v.setType("REGISTER");
        v.setIsUsed(isUsed);
        return v;
    }

    // ===================== verifyCode =====================

    @Test
    @DisplayName("verifyCode: 邮箱命中且未使用 -> 标记已用并返回 true")
    void verifyCode_emailHit() {
        VerificationCode v = vc(1L, "a@b.com", "123456", false);
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(v);

        assertTrue(service.verifyCode("a@b.com", "123456", "REGISTER"));
        assertTrue(v.getIsUsed());
        verify(baseMapper).updateById(v);
    }

    @Test
    @DisplayName("verifyCode: 手机号命中且未使用 -> 返回 true")
    void verifyCode_phoneHit() {
        VerificationCode v = vc(2L, "13800000000", "654321", false);
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(v);

        assertTrue(service.verifyCode("13800000000", "654321", "REGISTER"));
        assertTrue(v.getIsUsed());
        verify(baseMapper).updateById(v);
    }

    @Test
    @DisplayName("verifyCode: 已使用的验证码仍被查到但因 isUsed=true 更新后返回 true（命中即更新）")
    void verifyCode_alreadyUsedStillReturnsTrue() {
        // 业务上 getOne 仅筛 isUsed=false，此处模拟极端：返回对象本身 isUsed=true，
        // 调用方仅验证「查到即标记」，覆盖 setUsed(true) + updateById 的写入路径。
        VerificationCode v = vc(3L, "a@b.com", "111111", true);
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(v);

        assertTrue(service.verifyCode("a@b.com", "111111", "REGISTER"));
        assertTrue(v.getIsUsed());
        verify(baseMapper).updateById(v);
    }

    @Test
    @DisplayName("verifyCode: 未命中 -> 返回 false，不更新")
    void verifyCode_miss() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);

        assertFalse(service.verifyCode("a@b.com", "000000", "REGISTER"));
        verify(baseMapper, never()).updateById(any(VerificationCode.class));
    }

    @Test
    @DisplayName("verifyCode: 验证码/类型不匹配 -> getOne 返回 null -> false")
    void verifyCode_wrongCode() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);

        assertFalse(service.verifyCode("a@b.com", "wrong", "REGISTER"));
        verify(baseMapper, never()).updateById(any(VerificationCode.class));
    }

    // ===================== cleanExpiredCodes =====================

    @Test
    @DisplayName("cleanExpiredCodes: 无过期记录 -> 返回 0 且不执行删除")
    void cleanExpired_none() {
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertEquals(0, service.cleanExpiredCodes());
        verify(baseMapper, never()).delete(any());
    }

    @Test
    @DisplayName("cleanExpiredCodes: 有过期记录 -> 删除并返回数量")
    void cleanExpired_some() {
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
        when(baseMapper.delete(any(Wrapper.class))).thenReturn(1);

        assertEquals(3, service.cleanExpiredCodes());
        verify(baseMapper).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("cleanExpiredCodes: 删除返回 false 仍返回计数")
    void cleanExpired_deleteReturnsFalse() {
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(baseMapper.delete(any(Wrapper.class))).thenReturn(0);

        assertEquals(1, service.cleanExpiredCodes());
    }

    // ===================== getTodaySendCount =====================

    @Test
    @DisplayName("getTodaySendCount: 返回统计计数")
    void getTodaySendCount() {
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(7L);

        assertEquals(7L, service.getTodaySendCount("a@b.com", "REGISTER"));
        // 不同 target / type 仍走同一 count 路径
        assertEquals(7L, service.getTodaySendCount("13800000000", "RESET_PASSWORD"));
    }

    @Test
    @DisplayName("getTodaySendCount: 今日无发送 -> 返回 0")
    void getTodaySendCount_zero() {
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertEquals(0L, service.getTodaySendCount("a@b.com", "REGISTER"));
    }
}
