package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.ExamSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.ExamSessionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 考试会话对象级授权（防 IDOR / BOLA / 水平越权）。
 *
 * <p>原 {@code ExamSessionController} 仅按路径 {@code {id}} 操作，未校验资源归属，
 * 任意登录学生可枚举会话 ID 篡改/读取他人考试（替考、偷看成绩）。本组件在 Controller 入口统一执行
 * 归属校验，作为后端权威兜底（即使前端伪边界被绕过也安全）。</p>
 *
 * <p>归属规则：
 * <ul>
 *   <li>读权限（questions/time-remaining/report/result）：学生仅可访问本人会话；
 *       教师/管理员可访问任意会话（批卷、查阅成绩需要）；</li>
 *   <li>作答权限（auto-save/submit）：仅考生本人可操作，教师/管理员亦不可代答
 *       （替学生收卷应走既有的 force-submit）。</li>
 * </ul>
 * </p>
 */
@Component
public class ExamSessionAccessControl {

    private final ExamSessionService examSessionService;

    public ExamSessionAccessControl(ExamSessionService examSessionService) {
        this.examSessionService = examSessionService;
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
            throw new BusinessException(ApiCode.FORBIDDEN, "仅考生本人可操作该考试会话");
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
