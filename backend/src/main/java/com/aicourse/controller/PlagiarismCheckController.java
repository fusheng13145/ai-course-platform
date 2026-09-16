package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.PlagiarismMatch;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.SubmissionAccessControl;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.PlagiarismCheckService;
import com.aicourse.vo.PlagiarismCheckResultVO;
import com.aicourse.dto.PlagiarismCheckCreateDTO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 查重检测接口控制器
 */
@Tag(name = "查重检测", description = "作业查重检查与相似度匹配结果")
@RestController
@RequestMapping("/plagiarism-checks")
public class PlagiarismCheckController {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismCheckController.class);

    private final PlagiarismCheckService plagiarismCheckService;

    /**
     * 对象级授权组件（修复 B2）：查重结果的读端点此前无任何 ACL，
     * 任意登录学生可枚举 {@code /plagiarism-checks/1,2,3...} 读取他人查重结果与相似度分数。
     * 此处复用与 SubmissionController 一致的归属校验，作为后端权威兜底。
     */
    private final SubmissionAccessControl submissionAccessControl;

    /**
     * 选课服务（修复 B2 残留）：用于判定「某学生是否为该教师的学生」，
     * 供 {@code GET /plagiarism-checks/student/{studentId}} 的对象级授权收口使用。
     * 经服务层而非直接依赖 mapper，遵守 {@code ArchitectureTest} 的 controller→service 分层约束。
     */
    private final CourseEnrollmentService courseEnrollmentService;

    public PlagiarismCheckController(PlagiarismCheckService plagiarismCheckService,
                                     SubmissionAccessControl submissionAccessControl,
                                     CourseEnrollmentService courseEnrollmentService) {
        this.plagiarismCheckService = plagiarismCheckService;
        this.submissionAccessControl = submissionAccessControl;
        this.courseEnrollmentService = courseEnrollmentService;
    }

    /**
     * 创建查重任务
     * @param body 请求体（包含submissionId和checkType）
     * @return 新创建的查重任务
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<PlagiarismCheck> create(@Valid @RequestBody PlagiarismCheckCreateDTO dto) {
        Long submissionId = dto.getSubmissionId();
        String checkType = dto.getCheckType() != null ? dto.getCheckType() : "TEXT";
        PlagiarismCheck check = plagiarismCheckService.createCheck(submissionId, checkType);
        return ApiResponse.success("查重任务创建成功", check);
    }

    /**
     * 执行查重检测
     * @param id 查重任务ID
     * @return 操作结果
     */
    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> execute(@PathVariable Long id) {
        plagiarismCheckService.executeCheck(id);
        return ApiResponse.success("查重执行完成", null);
    }

    /**
     * 获取查重结果详情
     * @param id 查重任务ID
     * @return 查重结果详情（含匹配明细列表）
     */
    @GetMapping("/{id}")
    public ApiResponse<PlagiarismCheckResultVO> getCheckResult(@PathVariable Long id, Authentication auth) {
        assertCheckReadable(id, auth);
        PlagiarismCheckResultVO result = plagiarismCheckService.getCheckResult(id);
        return ApiResponse.success(result);
    }

    /**
     * 获取高相似度匹配列表
     * @param id       查重任务ID
     * @param threshold 相似度阈值（可选，默认0.3）
     * @return 高相似度匹配明细列表
     */
    @GetMapping("/{id}/matches")
    public ApiResponse<List<PlagiarismMatch>> getHighSimilarityMatches(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0.3") BigDecimal threshold,
            Authentication auth) {
        assertCheckReadable(id, auth);
        List<PlagiarismMatch> matches = plagiarismCheckService.getHighSimilarityMatches(id, threshold);
        return ApiResponse.success(matches);
    }

    /**
     * 获取某提交的查重历史记录
     * @param submissionId 提交记录ID
     * @return 查重历史列表
     */
    @GetMapping("/submission/{submissionId}")
    public ApiResponse<List<PlagiarismCheck>> getSubmissionHistory(@PathVariable Long submissionId, Authentication auth) {
        submissionAccessControl.assertReadable(submissionId, auth);
        List<PlagiarismCheck> checks = plagiarismCheckService.getSubmissionChecks(submissionId);
        return ApiResponse.success(checks);
    }

    /**
     * 获取全部查重任务列表（管理端），按创建时间倒序，最多返回最近500条
     * @return 查重任务列表
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<PlagiarismCheck>> listAll() {
        List<PlagiarismCheck> checks = plagiarismCheckService.lambdaQuery()
                .orderByDesc(PlagiarismCheck::getCreatedAt)
                .last("LIMIT 500")
                .list();
        return ApiResponse.success(checks);
    }

    /**
     * 获取某学生的查重历史记录
     *
     * <p>修复 B2 残留（2026-09-14）：本端点原先仅 {@code @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")}
     * 按角色放行，任何教师均可读取**非本人学生**（乃至全平台任意学生）的查重记录，构成水平越权 / IDOR。
     * 现追加对象级归属校验：管理员放行；教师仅当其与该学生存在「任教课程—有效选课」关系时放行。</p>
     *
     * @param studentId 学生ID
     * @return 该学生的查重历史列表（按创建时间倒序）
     */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<PlagiarismCheck>> getStudentHistory(@PathVariable Long studentId, Authentication auth) {
        assertTeacherScopedToStudent(studentId, auth);
        List<PlagiarismCheck> checks = plagiarismCheckService.lambdaQuery()
                .eq(PlagiarismCheck::getStudentId, studentId)
                .orderByDesc(PlagiarismCheck::getCreatedAt)
                .list();
        return ApiResponse.success(checks);
    }

    /**
     * 查重任务级读权限收口（修复 B2）：
     * 先由查重任务定位其所属提交记录，再复用 {@link SubmissionAccessControl#assertReadable}
     * 执行对象级归属校验——学生仅本人提交可读；教师/管理员须为其本人任教课程。
     * 越权时抛 {@link BusinessException}(403)，经全局异常处理器映射为 HTTP 403。
     */
    private void assertCheckReadable(Long checkId, Authentication auth) {
        PlagiarismCheck check = plagiarismCheckService.getById(checkId);
        if (check == null) {
            throw new BusinessException(404, "查重任务不存在");
        }
        submissionAccessControl.assertReadable(check.getSubmissionId(), auth);
    }

    /**
     * 「教师—学生」归属收口（修复 B2 残留）：
     * <ul>
     *   <li>ADMIN：放行（管理端可查看全平台）；</li>
     *   <li>TEACHER：仅当该学生在其名下≥1门课程上持有有效且审批通过的选课记录时放行，否则 403；</li>
     *   <li>其余角色：本端点已由 {@code @PreAuthorize} 拦截（仅 TEACHER/ADMIN），此处不处理。</li>
     * </ul>
     * 越权时抛 {@link BusinessException}(403)，经全局异常处理器映射为 HTTP 403。
     */
    private void assertTeacherScopedToStudent(Long studentId, Authentication auth) {
        if (studentId == null) {
            throw new BusinessException(400, "学生ID不能为空");
        }
        if (isAdmin(auth)) {
            return;
        }
        Long teacherId = (Long) auth.getPrincipal();
        if (!courseEnrollmentService.hasApprovedEnrollmentWithTeacher(studentId, teacherId)) {
            log.warn("[PlagiarismAccess] 越权访问尝试（教师读取非本人学生查重记录）：studentId={}, teacherId={}",
                    studentId, teacherId);
            throw new BusinessException(403, "无权查看该学生的查重记录");
        }
    }

    /** 判定当前主体是否为管理员角色。 */
    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + RoleConstants.ADMIN)::equals);
    }
}
