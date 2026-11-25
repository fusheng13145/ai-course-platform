package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.ExamPaper;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import org.springframework.security.core.Authentication;
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
    private final CourseEnrollmentService courseEnrollmentService;
    private final AdminScopeGuard adminScopeGuard;

    public ExamPaperAccessControl(CourseService courseService,
                                  CourseEnrollmentService courseEnrollmentService,
                                  AdminScopeGuard adminScopeGuard) {
        this.courseService = courseService;
        this.courseEnrollmentService = courseEnrollmentService;
        this.adminScopeGuard = adminScopeGuard;
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
            assertAdminCourseAccess(paper.getCourseId());
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
            assertAdminCourseAccess(courseId);
            return;
        }
        if (RoleConstants.STUDENT.equals(roleOf(auth))) {
            log.warn("[ExamPaperAccess] 越权组卷尝试（学生角色）：courseId={}, userId={}", courseId, principalId(auth));
            throw new BusinessException(ApiCode.FORBIDDEN, "无权为该课程组卷");
        }
        assertCourseOwnedBy(courseId, auth, "无权为其他教师的课程组卷");
    }

    /**
     * 试卷内容读取权限校验（查看试卷题目 / 试卷详情等只读端点）。
     *
     * <p><b>修复记录（2026-09-21，P0）</b>：{@code GET /exam-papers/{id}/questions} 原先既无角色校验
     * 也无归属校验，实测非选课学生（student3）可直接读取他人课程试卷的全部题目，且返回体为未脱敏实体
     * （含 {@code correctAnswers} / {@code explanation}），构成考试公平性缺陷。本方法补齐读路径的
     * 对象级授权——写路径早已有 {@link #assertCanManage}，此处将同样的归属规则延伸到读操作。</p>
     *
     * <p>规则：管理员放行；学生须对该试卷所属课程有<b>已通过审批</b>的选课记录；
     * 教师须为该试卷所属课程的授课教师。</p>
     *
     * @throws BusinessException 试卷或课程不存在 code=404；无权限 code=403
     */
    public void assertCanRead(ExamPaper paper, Authentication auth) {
        if (paper == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "试卷不存在");
        }
        if (isAdmin(auth)) {
            assertAdminCourseAccess(paper.getCourseId());
            return;
        }
        String role = roleOf(auth);
        if (RoleConstants.STUDENT.equals(role)) {
            Course course = paper.getCourseId() != null ? courseService.getById(paper.getCourseId()) : null;
            if (course == null) {
                throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
            }
            if (!courseEnrollmentService.hasApprovedEnrollment(principalId(auth), course.getId())) {
                log.warn("[ExamPaperAccess] 拒绝非课程成员读取试卷内容：paperId={}, userId={}, courseId={}",
                        paper.getId(), principalId(auth), course.getId());
                throw new BusinessException(ApiCode.FORBIDDEN, "尚未加入该课程，无权查看试卷内容");
            }
            return;
        }
        assertCourseOwnedBy(paper.getCourseId(), auth, "无权查看其他教师课程的试卷");
    }

    // ===================== 内部工具 =====================

    /**
     * 管理员作用域：校级管理员保持全平台；院系管理员只能落在本院课程。
     * 校级管理员不额外查询课程，保持改造前的治理读行为。
     */
    private void assertAdminCourseAccess(Long courseId) {
        if (!adminScopeGuard.isCollegeAdmin()) {
            return;
        }
        Course course = courseId != null ? courseService.getById(courseId) : null;
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        adminScopeGuard.assertCourseAccess(course);
    }

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
        // 统一到 AccessSupport：角色判定只保留一处实现（阶段 2 DUP-18/19）
        return AccessSupport.isAdmin(auth);
    }

    private Long principalId(Authentication auth) {
        return AccessSupport.requirePrincipalId(auth);
    }

    private String roleOf(Authentication auth) {
        return AccessSupport.roleOf(auth);
    }
}
