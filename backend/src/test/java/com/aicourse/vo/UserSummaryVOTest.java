package com.aicourse.vo;

import com.aicourse.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脱敏出参守护：同学 / 学生名册不得回传可识别到个人的联系方式与学籍号。
 *
 * <p>这条断言是「字段白名单」的回归护栏：任何人往 {@link UserSummaryVO} 里补
 * email/phone/studentId 字段，这里都会红。
 */
class UserSummaryVOTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private User sampleUser() {
        User user = new User();
        user.setId(7L);
        user.setUsername("stu_zhang");
        user.setFullName("张三");
        user.setAvatarUrl("/uploads/avatar/7.png");
        user.setRole("STUDENT");
        user.setMajor("软件工程");
        user.setGrade("2024");
        user.setIsActive(Boolean.TRUE);
        user.setIsVerified(Boolean.TRUE);
        user.setEmail("zhangsan@example.edu.cn");
        user.setPhone("13800000000");
        user.setStudentId("2024010101");
        user.setGender("M");
        return user;
    }

    @Test
    void fromKeepsWhitelistedFields() {
        UserSummaryVO vo = UserSummaryVO.from(sampleUser());
        assertTrue(vo.getId() == 7L, "id 必须透传");
        assertTrue("张三".equals(vo.getFullName()), "姓名必须透传");
        assertTrue("STUDENT".equals(vo.getRole()), "角色必须透传");
    }

    @Test
    void serializedPayloadHasNoSensitiveFields() throws Exception {
        String json = mapper.writeValueAsString(UserSummaryVO.from(sampleUser()));
        assertFalse(json.contains("email"), "同学列表不得输出 email");
        assertFalse(json.contains("phone"), "同学列表不得输出 phone");
        assertFalse(json.contains("studentId"), "同学列表不得输出 studentId");
        assertFalse(json.contains("gender"), "同学列表不得输出 gender");
        assertTrue(json.contains("fullName"), "姓名仍在，前端展示零改动");
    }

    @Test
    void nullSafeConversion() {
        assertNull(UserSummaryVO.from(null), "null 入参不得抛 NPE");
    }
}
