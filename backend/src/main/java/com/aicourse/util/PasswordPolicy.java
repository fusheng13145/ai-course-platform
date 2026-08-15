package com.aicourse.util;

import com.aicourse.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * 密码策略统一校验器（全站单一事实来源）
 * <p>
 * 所有密码变更入口必须统一走本校验，杜绝"某入口用弱规则放行"的旁路：
 * 注册、修改密码、找回密码（手机通道 + 邮箱通道）、管理端创建用户、管理端重置用户密码。
 * 规则：8-32 位，同时包含大写字母、小写字母、数字与特殊字符。
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[~!@#$%^&*()_+\\-={}\\[\\]:;\"'<>,.?/\\\\|`]).{8,32}$");

    /**
     * 校验密码复杂度，不满足时抛出 {@link BusinessException}（400）。
     * @param password 明文密码
     */
    public static void validate(String password) {
        if (password == null || password.isEmpty()) {
            throw new BusinessException("密码不能为空");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new BusinessException(
                    "密码必须包含大写字母、小写字母、数字和特殊字符，且长度为8-32位");
        }
    }
}
