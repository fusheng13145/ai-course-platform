package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 考试会话对象级授权（防 IDOR / BOLA / 水平越权）。
 *
 * <p>原 {@code ExamSessionController} 仅按路径 {@code {id}} 操作，未校验资源归属，
 * 任意登录学生可枚举会话 ID 篡改/读取他人考试（替考、偷看成绩）；教师亦可对其他教师
 * 课程的考试收卷/阅卷。本组件在 Controller 入口统一执行归属校验，作为后端权威兜底
 * （即使前端伪边界被绕过也安全）。</p>
 *
 * <p>归属规则：
 * <ul>
 *   <li>读权限（questions/time-remaining/report/result）：学生仅可访问本人会话；
 *       教师/管理员可访问任意会话（批卷、查阅成绩需要）；</li>
 *   <li>作答权限（auto-save/submit）：仅考生本人可操作，教师/管理员亦不可代答
 *       （替学生收卷应走既有的 force-submit）；</li>
 *   <li>管理权限（force-submit/auto-grade/manual-grade/cheating-detect/check-cheating）：
 *       学生一律拒绝；管理员放行；教师须为会话所属试卷对应课程的授课教师
 *       （链路：exam_sessions.exam_paper_id → exam_papers.course_id → courses.teacher_id）。</li>
 * </ul>
 * </p>
 */
@Component
public class ExamSessionAccessControl {

    private static final Logger log = LoggerFactory.getLogger(ExamSessionAccessControl.class);

    private final ExamSessionService examSessionService;
    private final ExamPaperService examPaperService;
    private final CourseService courseService;

    public ExamSessionAccessControl(ExamSessionService examSessionService,
                                    ExamPaperService examPaperService,
                                    CourseService courseService) {
        this.examSessionService = examSessionService;
        this.examPaperService = examPaperService;
        this.courseService = courseService;
    }

    /**
     * 读权限校验（questions / time-remaining / report / result）。
     * 学生仅本人；教师/管理员放行。返回已加载的会话以便复用，避免重复查询。
     *
     * @throws BusinessException 会话不存在时 code=404；学生越权访问时 code=403
     */
    public ExamSession assertReadable(Long sessionId, Authentication auth) {
        ExamSession session = requireSession(sessionId);
        if (RoleConstants.STUDENT.equals(roleOf(auth))) {
            if (!principalId(auth).equals(session.getStudentId())) {
                log.warn("[ExamSessionAccess] 越权访问尝试（学生非本人会话）：sessionId={}, 访问者={}, 持有者={}",
                        sessionId, principalId(auth), session.getStudentId());
                throw new BusinessException(ApiCode.FORBIDDEN, "无权访问该考试会话");
            }
        }
        return session;
    }

    /**
     * 作答权限校验（auto-save / submit）。仅考生本人可操作，教师/管理员代答一律拒绝。
     *
     * @throws BusinessException 会话不存在时 code=404；非本人（含教师/管理员）时 code=403
     */
    public ExamSession assertOwnedByStudent(Long sessionId, Authentication auth) {
        ExamSession session = requireSession(sessionId);
        if (!principalId(auth).equals(session.getStudentId())) {
            log.warn("[ExamSessionAccess] 越权作答尝试（非考生本人）：sessionId={}, 访问者={}, 持有者={}",
                    sessionId, principalId(auth), session.getStudentId());
            throw new BusinessException(ApiCode.FORBIDDEN, "仅考生本人可操作该考试会话");
        }
        return session;
    }

    /**
     * 管理权限校验（force-submit / auto-grade / manual-grade / cheating-detect / check-cheating）。
     * 学生一律拒绝；管理员放行；教师须为会话所属试卷对应课程的授课教师，
     * 防止教师 A 对教师 B 课程的考试收卷/阅卷/作弊检测。
     *
     * @throws BusinessException 会话不存在时 code=404；学生或无归属教师时 code=403
     */
    public ExamSession assertManageable(Long sessionId, Authentication auth) {
        ExamSession session = requireSession(sessionId);
        Long userId = principalId(auth);
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_" + RoleConstants.ADMIN));
        if (isAdmin) {
            return session;
        }
        if (RoleConstants.STUDENT.equals(roleOf(auth))) {
            log.warn("[ExamSessionAccess] 越权管理尝试（学生角色）：sessionId={}, userId={}", sessionId, userId);
            throw new BusinessException(ApiCode.FORBIDDEN, "无权管理该考试会话");
        }
        ExamPaper paper = examPaperService.getById(session.getExamPaperId());
        if (paper == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "试卷不存在");
        }
        Course course = courseService.getById(paper.getCourseId());
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        if (!userId.equals(course.getTeacherId())) {
            log.warn("[ExamSessionAccess] 越权管理尝试（非授课教师）：sessionId={}, userId={}, courseId={}, teacherId={}",
                    sessionId, userId, course.getId(), course.getTeacherId());
            throw new BusinessException(ApiCode.FORBIDDEN, "无权管理其他教师的考试");
        }
        return session;
    }

    // ===================== 内部工具 =====================

    private ExamSession requireSession(Long sessionId) {
        ExamSession session = examSessionService.getById(sessionId);
        if (session == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "考试会话不存在");
        }
        return session;
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
