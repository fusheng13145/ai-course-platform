package com.aicourse.application.enrollment;

import com.aicourse.common.ApiCode;
import com.aicourse.common.PagedResponse;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AccessSupport;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.security.TeachingClassAccessControl;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.UserService;
import com.aicourse.vo.CourseVOs;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 选课管理应用服务（3E-05D）。
 *
 * <p>从 {@code CourseEnrollmentController} 下移的内容：</p>
 * <ul>
 *   <li>全量选课分页的<b>院系收口</b>（显式 courseId 先按课程学院 403；否则按本院学生ID集过滤），
 *       以及「关键字先解析学生ID集再入 SQL」（禁止分页后当页二次过滤）；</li>
 *   <li>「我的报名」的 V14/WP5 口径（{@code is_active AND approval_status='APPROVED'}）；</li>
 *   <li>取消 / 通过 / 驳回三端共用的归属校验 {@link #assertEnrollmentCourseOwner}；</li>
 *   <li>3 处 {@code LambdaQueryWrapper} 与分页转 VO。</li>
 * </ul>
 *
 * <p>口径逐字保留：驳回必须同时置 {@code is_active=false}（2026-09-14 修复：否则下游按 is_active
 * 判权的链路仍把已驳回学生当成员放行，且继续占用名额）；通过/驳回仅允许 PENDING（否则 409）。</p>
 */
@Service
public class CourseEnrollmentApplicationService {

    private static final String APPROVED = "APPROVED";
    private static final String PENDING = "PENDING";
    private static final String REJECTED = "REJECTED";
    /** 关键字解析学生ID集的上限（超过即只取前 500 个候选，避免全表扫描）。 */
    private static final int KEYWORD_MATCH_LIMIT = 500;
    /** 我的报名分页固定取前 100 条（原实现即硬编码）。 */
    private static final int MY_ENROLLMENTS_PAGE_SIZE = 100;

    private final CourseEnrollmentService enrollmentService;
    private final CourseService courseService;
    private final UserService userService;
    private final AdminScopeGuard adminScopeGuard;
    private final TeachingClassAccessControl teachingClassAccessControl;

    public CourseEnrollmentApplicationService(CourseEnrollmentService enrollmentService,
                                              CourseService courseService,
                                              UserService userService,
                                              AdminScopeGuard adminScopeGuard,
                                              TeachingClassAccessControl teachingClassAccessControl) {
        this.enrollmentService = enrollmentService;
        this.courseService = courseService;
        this.userService = userService;
        this.adminScopeGuard = adminScopeGuard;
        this.teachingClassAccessControl = teachingClassAccessControl;
    }

    // ==================== 全量分页 ====================

    /**
     * 全量报名记录分页（管理员治理台）。院系管理员只能看本院课程/本院学生；
     * 空结果集也回显请求的 page/size。
     */
    public PagedResponse<CourseVOs.EnrollmentVO> listAll(int page, int size, String status,
                                                         Long courseId, String keyword) {
        LambdaQueryWrapper<CourseEnrollment> qw = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(status != null && !status.isBlank(), CourseEnrollment::getApprovalStatus, status)
                .eq(courseId != null, CourseEnrollment::getCourseId, courseId);
        // 院系管理员：显式传 courseId 时先按课程学院收口，避免跨院枚举
        boolean collegeScoped = adminScopeGuard.isCollegeAdmin();
        if (courseId != null && collegeScoped) {
            Course course = courseService.getById(courseId);
            if (course == null) {
                throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
            }
            adminScopeGuard.assertCourseAccess(course);
        }
        // keyword 按学生姓名/用户名过滤：先解析学生ID集再入 SQL（不得分页后当页二次过滤）
        List<Long> matched = null;
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            matched = userService.list(new LambdaQueryWrapper<User>()
                            .and(w -> w.like(User::getFullName, kw).or().like(User::getUsername, kw))
                            .last("LIMIT " + KEYWORD_MATCH_LIMIT))
                    .stream().map(User::getId).collect(Collectors.toList());
        }
        if (collegeScoped) {
            List<Long> collegeStudentIds = adminScopeGuard.currentCollegeUserIds();
            if (collegeStudentIds == null || collegeStudentIds.isEmpty()) {
                return emptyPage(page, size);
            }
            if (matched != null) {
                List<Long> intersected = matched.stream().filter(collegeStudentIds::contains).collect(Collectors.toList());
                if (intersected.isEmpty()) {
                    return emptyPage(page, size);
                }
                qw.in(CourseEnrollment::getStudentId, intersected);
            } else {
                qw.in(CourseEnrollment::getStudentId, collegeStudentIds);
            }
        } else if (matched != null) {
            if (matched.isEmpty()) {
                return emptyPage(page, size);
            }
            qw.in(CourseEnrollment::getStudentId, matched);
        }
        qw.orderByDesc(CourseEnrollment::getId);
        Page<CourseEnrollment> result = enrollmentService.page(Page.of(page, size), qw);
        return toPagedVO(result);
    }

    /** 我的报名记录（仅审批通过且有效，V14 / WP5）。 */
    public PagedResponse<CourseVOs.EnrollmentVO> myEnrollments(Long userId) {
        LambdaQueryWrapper<CourseEnrollment> qw = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, userId)
                .eq(CourseEnrollment::getIsActive, true)
                .eq(CourseEnrollment::getApprovalStatus, APPROVED);
        Page<CourseEnrollment> result = enrollmentService.page(new Page<>(1, MY_ENROLLMENTS_PAGE_SIZE), qw);
        return toPagedVO(result);
    }

    // ==================== 取消 / 审核 ====================

    /** 取消已通过的选课（学生本人 / 课程负责人或教学班主讲 / 管理员）。 */
    public CourseEnrollment cancel(Long id, String reason, Authentication auth) {
        CourseEnrollment enrollment = requireEnrollment(id);
        Long operatorId = (Long) auth.getPrincipal();
        if (AccessSupport.hasRole(auth, RoleConstants.STUDENT)) {
            if (!operatorId.equals(enrollment.getStudentId())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "只能取消自己的选课");
            }
        } else {
            assertEnrollmentCourseOwner(enrollment, auth);
        }
        return enrollmentService.cancelEnrollment(id, operatorId, reason);
    }

    /** 审核通过（仅 PENDING）。 */
    public CourseEnrollment approve(Long id, Authentication auth) {
        CourseEnrollment enrollment = requireEnrollment(id);
        if (!PENDING.equalsIgnoreCase(enrollment.getApprovalStatus())) {
            throw new BusinessException(ApiCode.CONFLICT, "仅待审批的选课可以通过");
        }
        assertEnrollmentCourseOwner(enrollment, auth);
        enrollment.setApprovalStatus(APPROVED);
        enrollment.setApprovedBy((Long) auth.getPrincipal());
        enrollment.setApprovalTime(LocalDateTime.now());
        enrollment.setIsActive(true);
        enrollmentService.updateById(enrollment);
        return enrollment;
    }

    /** 审核驳回（仅 PENDING）；必须同时置 is_active=false。 */
    public CourseEnrollment reject(Long id, String reason, Authentication auth) {
        CourseEnrollment enrollment = requireEnrollment(id);
        if (!PENDING.equalsIgnoreCase(enrollment.getApprovalStatus())) {
            throw new BusinessException(ApiCode.CONFLICT, "仅待审批的选课可以驳回");
        }
        assertEnrollmentCourseOwner(enrollment, auth);
        enrollment.setApprovalStatus(REJECTED);
        enrollment.setApprovedBy((Long) auth.getPrincipal());
        enrollment.setApprovalTime(LocalDateTime.now());
        // 驳回必须同时置 is_active=false（2026-09-14 修复）：否则下游按 is_active 判权的链路
        // （资源下载、开考资格）仍把已驳回学生当课程成员放行，且该记录继续占用 max_students 名额。
        enrollment.setIsActive(false);
        if (reason != null) {
            enrollment.setApprovalComment(reason);
        }
        enrollmentService.updateById(enrollment);
        return enrollment;
    }

    /** 本人有效选课课程ID（3E-05E：AI 学情洞察注入掌握度快照用，V14 口径同「我的报名」）。 */
    public List<Long> activeCourseIds(Long studentId) {
        return enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getStudentId, studentId)
                        .eq(CourseEnrollment::getIsActive, true)
                        .eq(CourseEnrollment::getApprovalStatus, APPROVED))
                .stream().map(CourseEnrollment::getCourseId).distinct().collect(Collectors.toList());
    }

    // ==================== 内部助手 ====================

    private CourseEnrollment requireEnrollment(Long id) {
        CourseEnrollment enrollment = enrollmentService.getById(id);
        if (enrollment == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "选课记录不存在");
        }
        return enrollment;
    }

    /**
     * 选课管理归属校验：管理员按学院范围放行；教师须为课程负责人或该教学班主讲。
     */
    private void assertEnrollmentCourseOwner(CourseEnrollment enrollment, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (AccessSupport.isAdmin(auth)) {
            if (adminScopeGuard.isCollegeAdmin()) {
                Course adminCourse = courseService.getById(enrollment.getCourseId());
                if (adminCourse == null) {
                    throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
                }
                adminScopeGuard.assertCourseAccess(adminCourse);
            }
            return;
        }
        Course course = courseService.getById(enrollment.getCourseId());
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        if (userId.equals(course.getTeacherId())) {
            return;
        }
        if (enrollment.getTeachingClassId() != null) {
            // 本班主讲同样可以管理自己班的选课；其他班主讲会被 403 拒绝。
            teachingClassAccessControl.assertRosterManage(enrollment.getTeachingClassId(), userId);
            return;
        }
        throw new BusinessException(ApiCode.FORBIDDEN, "无权管理该课程的选课记录");
    }

    private static PagedResponse<CourseVOs.EnrollmentVO> toPagedVO(Page<CourseEnrollment> page) {
        return PagedResponse.of(page.getRecords().stream().map(CourseVOs.EnrollmentVO::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    private static PagedResponse<CourseVOs.EnrollmentVO> emptyPage(int page, int size) {
        // 空页仍回显请求的 page/size（不写死 1/10）
        return PagedResponse.of(List.of(), 0, page, size);
    }
}
