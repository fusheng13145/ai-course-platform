package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.ExamPaper;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 试卷对象级授权（防 IDOR / BOLA / 水平越权）。
 *
 * <p><b>修复记录（2026-09-14，P1）</b>：原 {@code ExamPaperController} 的发布/组卷端点只做了角色校验
 * （{@code hasAnyRole(TEACHER, ADMIN)}），未校验试卷与课程的归属关系。实测：教师 B（teacher2）
 * 可成功发布教师 A 所授课程的试卷（返回 200 且状态置为 PUBLISHED，并向该课程学生推送考试通知），
 * 属典型水平越权。本组件在 Controller 入口统一执行归属校验，作为后端权威兜底。</p>
 *
 * <p>归属规则：学生一律拒绝；管理员放行；教师须为试卷所属课程（组卷时为目标课程）的授课教师。
 * 与既有 {@code ExamSessionAccessControl}/{@code SubmissionAccessControl} 保持同一模式。</p>
 */
@Component
public class ExamPaperAccessControl {

    private static final Logger log = LoggerFactory.getLogger(ExamPaperAccessControl.class);

    private final CourseService courseService;

    public ExamPaperAccessControl(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * 试卷管理权限校验（发布 / 更新 / 删除 / 复制）。
     *
     * @throws BusinessException 试卷不存在 code=404；学生或无归属教师 code=403
     */
    public void assertCanManage(ExamPaper paper, Authentication auth) {
        if (paper == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "试卷不存在");
        }
        if (isAdmin(auth)) {
            return;
        }
        String role = roleOf(auth);
        if (RoleConstants.STUDENT.equals(role)) {
            log.warn("[ExamPaperAccess] 越权管理尝试（学生角色）：paperId={}, userId={}", paper.getId(), principalId(auth));
            throw new BusinessException(ApiCode.FORBIDDEN, "无权操作该试卷");
        }
        assertCourseOwnedBy(paper.getCourseId(), auth, "无权操作其他教师课程的试卷");
    }

    /**
     * 组卷权限校验：目标课程必须归属当前教师（管理员放行）。
     *
     * @throws BusinessException 未指定课程 code=400；课程不存在 code=404；非授课教师 code=403
     */
    public void assertCanCompose(Long courseId, Authentication auth) {
        if (courseId == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请指定课程");
        }
        if (isAdmin(auth)) {
            return;
        }
        if (RoleConstants.STUDENT.equals(roleOf(auth))) {
            log.warn("[ExamPaperAccess] 越权组卷尝试（学生角色）：courseId={}, userId={}", courseId, principalId(auth));
            throw new BusinessException(ApiCode.FORBIDDEN, "无权为该课程组卷");
        }
        assertCourseOwnedBy(courseId, auth, "无权为其他教师的课程组卷");
    }

    // ===================== 内部工具 =====================

    private void assertCourseOwnedBy(Long courseId, Authentication auth, String message) {
        Course course = courseId != null ? courseService.getById(courseId) : null;
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        if (!principalId(auth).equals(course.getTeacherId())) {
            log.warn("[ExamPaperAccess] 越权管理尝试（非授课教师）：courseId={}, userId={}, teacherId={}",
                    courseId, principalId(auth), course.getTeacherId());
            throw new BusinessException(ApiCode.FORBIDDEN, message);
        }
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_" + RoleConstants.ADMIN));
    }

    private Long principalId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }

    private String roleOf(Authentication auth) {
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();
        return roles.isEmpty() ? "" : roles.get(0);
    }
}
