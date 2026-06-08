package com.aicourse.service.impl;

import com.aicourse.entity.VerificationCode;
import com.aicourse.mapper.VerificationCodeMapper;
import com.aicourse.service.VerificationCodeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 验证码服务实现类
 * 实现验证码校验、清理、发送次数统计等核心业务逻辑
 */
@Service
public class VerificationCodeServiceImpl extends ServiceImpl<VerificationCodeMapper, VerificationCode> implements VerificationCodeService {

    /**
     * 验证码校验
     * 检查验证码是否正确、未使用、未过期，校验通过后标记为已使用
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean verifyCode(String target, String code, String type) {
        // 查询最新的未使用且未过期的验证码
        LambdaQueryWrapper<VerificationCode> queryWrapper = new LambdaQueryWrapper<VerificationCode>()
                .and(w -> w
                        .eq(VerificationCode::getEmail, target)
                        .or()
                        .eq(VerificationCode::getPhone, target))
                .eq(VerificationCode::getCode, code)
                .eq(VerificationCode::getType, type)
                .eq(VerificationCode::getIsUsed, false)
                .gt(VerificationCode::getExpiresAt, LocalDateTime.now())
                .orderByDesc(VerificationCode::getCreatedAt)
                .last("LIMIT 1");

        VerificationCode verificationCode = this.getOne(queryWrapper);

        if (verificationCode == null) {
            return false;
        }

        // 校验通过，标记为已使用
        verificationCode.setIsUsed(true);
        this.updateById(verificationCode);
        return true;
    }

    /**
     * 清理过期验证码
     * 删除所有expiresAt < now的记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanExpiredCodes() {
        LambdaQueryWrapper<VerificationCode> queryWrapper = new LambdaQueryWrapper<VerificationCode>()
                .lt(VerificationCode::getExpiresAt, LocalDateTime.now());

        int count = (int) this.count(queryWrapper);
        if (count > 0) {
            this.remove(queryWrapper);
        }
        return count;
    }

    /**
     * 获取今日发送次数
     * 统计指定目标今日已发送的验证码数量（用于频率限制）
     */
    @Override
    public long getTodaySendCount(String target, String type) {
        // 今日开始时间
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        // 今日结束时间
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

        LambdaQueryWrapper<VerificationCode> queryWrapper = new LambdaQueryWrapper<VerificationCode>()
                .and(w -> w
                        .eq(VerificationCode::getEmail, target)
                        .or()
                        .eq(VerificationCode::getPhone, target))
                .eq(VerificationCode::getType, type)
                .ge(VerificationCode::getCreatedAt, todayStart)
                .le(VerificationCode::getCreatedAt, todayEnd);

        return this.count(queryWrapper);
    }
}
