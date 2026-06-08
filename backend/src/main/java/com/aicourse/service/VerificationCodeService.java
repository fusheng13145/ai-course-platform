package com.aicourse.service;

import com.aicourse.entity.VerificationCode;

/**
 * 验证码服务接口
 * 提供验证码校验、清理、发送次数统计等功能
 */
public interface VerificationCodeService extends com.baomidou.mybatisplus.extension.service.IService<VerificationCode> {

    /**
     * 验证码校验
     * 检查验证码是否正确且未使用未过期
     * @param target 目标（邮箱或手机号）
     * @param code 用户输入的验证码
     * @param type 验证码类型（REGISTER/RESET_PASSWORD/LOGIN）
     * @return 是否校验通过
     */
    boolean verifyCode(String target, String code, String type);

    /**
     * 清理过期验证码
     * 删除所有已过期的验证码记录
     * @return 清理的记录数
     */
    int cleanExpiredCodes();

    /**
     * 获取今日发送次数
     * 统计指定目标今日已发送的验证码数量
     * @param target 目标（邮箱或手机号）
     * @param type 验证码类型
     * @return 今日发送次数
     */
    long getTodaySendCount(String target, String type);
}
