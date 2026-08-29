package com.aicourse.util;

import com.aicourse.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PasswordPolicy 单元测试：覆盖通过 / 空 / 弱口令（缺字符类别）/ 超长 各分支。
 */
class PasswordPolicyTest {

    @Test
    void validPassword_passes() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Valid1!Pass"));
    }

    @Test
    void nullOrEmpty_throws() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate(null));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate(""));
    }

    @Test
    void weakPassword_throws() {
        // 缺少大写、数字、特殊字符
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("weakpassword"));
    }

    @Test
    void tooLong_throws() {
        // 31 位仍在 8-32 区间内会误判为合法；repeat(11) => 34 位，超出上限触发异常
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("A" + "a1!".repeat(11)));
    }
}
