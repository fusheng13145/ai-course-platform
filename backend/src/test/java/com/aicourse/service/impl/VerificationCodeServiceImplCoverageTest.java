package com.aicourse.service.impl;

import com.aicourse.entity.VerificationCode;
import com.aicourse.mapper.VerificationCodeMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * 验证码服务单元测试：最大化 INSTRUCTION 覆盖（ServiceImpl + mock baseMapper）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationCodeServiceImplCoverageTest {

    @Mock
    private VerificationCodeMapper baseMapper;

    private VerificationCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VerificationCodeServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    private VerificationCode vc(String target, String code) {
        VerificationCode v = new VerificationCode();
        v.setId(1L);
        v.setEmail(target);
        v.setPhone(target);
        v.setCode(code);
        v.setType("REGISTER");
        v.setIsUsed(false);
        return v;
    }

    // ===================== verifyCode =====================
    @Test
    @DisplayName("verifyCode: 校验通过 -> true")
    void verifyCode_ok() {
        VerificationCode v = vc("a@b.com", "123456");
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(v);
        when(baseMapper.updateById(any(VerificationCode.class))).thenReturn(1);
        assertTrue(service.verifyCode("a@b.com", "123456", "REGISTER"));
        assertTrue(v.getIsUsed());
        verify(baseMapper).updateById(v);
    }

    @Test
    @DisplayName("verifyCode: 未命中 -> false")
    void verifyCode_fail() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertFalse(service.verifyCode("a@b.com", "000000", "REGISTER"));
        verify(baseMapper, never()).updateById(any(VerificationCode.class));
    }

    // ===================== cleanExpiredCodes =====================
    @Test
    @DisplayName("cleanExpiredCodes: 无过期 -> 返回 0 不删除")
    void cleanExpired_none() {
        when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        assertEquals(0, service.cleanExpiredCodes());
        verify(baseMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("cleanExpiredCodes: 有过期 -> 删除并返回数量")
    void cleanExpired_some() {
        when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        when(baseMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        assertEquals(3, service.cleanExpiredCodes());
    }

    // ===================== getTodaySendCount =====================
    @Test
    @DisplayName("getTodaySendCount: 返回计数")
    void getTodaySendCount() {
        when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        assertEquals(5L, service.getTodaySendCount("a@b.com", "REGISTER"));
    }
}
