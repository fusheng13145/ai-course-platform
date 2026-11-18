package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * AI 控制器共享的课程 ACL 与课程 ID 解析。
 *
 * <p>拆分后 QA / 推荐与学情 / 学习路径三个控制器共用本组件，避免同一套
 * 「未登录 401、非本课 403」语义各写一份而分叉。</p>
 */
@Component
public class AIAccessSupport {

    private final CourseAccessControl courseAccessControl;

    public AIAccessSupport(CourseAccessControl courseAccessControl) {
        this.courseAccessControl = courseAccessControl;
    }

    /**
     * 课程对象级 ACL：学生要求有效选课；教师要求本人课程；管理员按学院作用域。
     * 面向已认证主体，这里补上异步/单测中 Authentication 为空时的 401 语义，避免 NPE 变成 500。
     */
    public void assertCourseReadable(Long courseId, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "未登录");
        }
        courseAccessControl.assertCanReadCourse(courseId, auth);
    }

    /** DTO 的课程 ID 是字符串；非法数字按业务 400 处理，不让 NumberFormatException 冒成 500。 */
    public Long parseCourseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "课程ID格式不正确");
        }
    }
}
