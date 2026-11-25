package com.aicourse.application.course;

import com.aicourse.common.ApiCode;
import com.aicourse.common.PageSupport;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.CourseSaveDTO;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseDefinition;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AccessSupport;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.CourseApprovalService;
import com.aicourse.service.CourseDefinitionService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.vo.CourseStatsVO;
import com.aicourse.vo.CourseVOs;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 课程应用服务（3E-04B）。
 *
 * <p>{@code CourseController} 改造前是「编排层」：15 个端点里有 5 处手工 {@code assertCourseOwner}、
 * 1 处 {@code assertCollegeScope}、4 处 wrapper、3 处手写分页转 VO，外加两条只写在注释里的口径
 * （管理员代建必须显式 teacherId、课程库稳定信息由服务层置空）。这里把它们全部收成单一事实源。</p>
 *
 * <h3>权限模型（V13 三层模型，原样保留）</h3>
 * <ul>
 *   <li><b>课程级</b>：「本人课程」——教师只能管 {@code teacher_id == 自己} 的课程。</li>
 *   <li><b>学院级</b>：院系管理员按 {@code courses.college_id} 限本院；显式请求他院 403。</li>
 *   <li><b>校级</b>：校级管理员不做额外限制，可按参数筛学院。</li>
 * </ul>
 *
 * <p>两级守卫被拆成 {@link #assertCourseOwner}（写面，教师本人 / 管理员→转学院校验）与
 * {@link #assertCollegeScope}（管理面，仅管理员路径）——改造前两者各写一遍，且 {@code /stats}
 * 端点还额外自己判了一次存在性。</p>
 */
@Service
public class CourseApplicationService {

    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;
    private final CourseApprovalService courseApprovalService;
    private final CourseDefinitionService definitionService;
    private final AdminScopeGuard adminScopeGuard;

    public CourseApplicationService(CourseService courseService,
                                    CourseEnrollmentService enrollmentService,
                                    CourseApprovalService courseApprovalService,
                                    CourseDefinitionService definitionService,
                                    AdminScopeGuard adminScopeGuard) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.courseApprovalService = courseApprovalService;
        this.definitionService = definitionService;
        this.adminScopeGuard = adminScopeGuard;
    }

    // ===================== 查询 =====================

    /**
     * 分页查询课程列表（多条件 + 学院范围）。
     *
     * <p>学院过滤口径（V13 起按 {@code courses.college_id}）：院系管理员强制本院，显式请求他院 403；
     * 校级管理员可按参数筛学院。</p>
     */
    /** 教师本人课程分页（3E-05E：从 TeacherController#getCourses 下移，原口径仅按 teacherId）。 */
    public PagedResponse<CourseVOs.CourseVO> teacherCourses(long page, long size, Long teacherId) {
        Page<Course> coursePage = courseService.page(Page.of(page, size),
                new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, teacherId));
        return PagedResponse.of(coursePage.getRecords().stream().map(CourseVOs.CourseVO::from).toList(),
                coursePage.getTotal(), coursePage.getCurrent(), coursePage.getSize());
    }

    public PagedResponse<CourseVOs.CourseVO> list(long page, long size, String keyword, String status,
                                                  String approvalStatus, Long collegeId, Long definitionId,
                                                  Long semesterId) {
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<Course>()
                .eq(status != null, Course::getStatus, status)
                .eq(approvalStatus != null, Course::getApprovalStatus, approvalStatus)
                .eq(definitionId != null, Course::getDefinitionId, definitionId)
                .eq(semesterId != null, Course::getSemesterId, semesterId)
                .like(keyword != null, Course::getName, keyword)
                .orderByDesc(Course::getCreatedAt);
        applyCollegeFilter(queryWrapper, collegeId);
        return PageSupport.toPage(courseService.page(Page.of(page, size), queryWrapper),
                CourseVOs.CourseVO::from);
    }

    /** 课程详情（院系管理员按学院限制访问）。 */
    public CourseVOs.CourseVO detail(Long id) {
        Course course = courseService.getById(id);
        adminScopeGuard.assertCourseAccess(course);
        return CourseVOs.CourseVO.from(course);
    }

    /** 当前用户已报名课程（仅审批通过且有效的记录，V14 / WP5）。 */
    public PagedResponse<CourseVOs.EnrollmentVO> enrolledCourses(Authentication auth, long page, long size) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<CourseEnrollment> queryWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, userId)
                .eq(CourseEnrollment::getIsActive, true)
                .eq(CourseEnrollment::getApprovalStatus, "APPROVED");
        return PageSupport.toPage(enrollmentService.page(Page.of(page, size), queryWrapper),
                CourseVOs.EnrollmentVO::from);
    }

    /** 当前教师创建的课程。 */
    public PagedResponse<CourseVOs.CourseVO> myCourses(Authentication auth, long page, long size) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, userId);
        return PageSupport.toPage(courseService.page(Page.of(page, size), queryWrapper),
                CourseVOs.CourseVO::from);
    }

    /** 课程学生名单（教师仅限本人课程）。 */
    public PagedResponse<CourseVOs.EnrollmentVO> students(Long id, long page, long size, Authentication auth) {
        assertCourseOwner(id, auth);
        LambdaQueryWrapper<CourseEnrollment> queryWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getCourseId, id);
        return PageSupport.toPage(enrollmentService.page(Page.of(page, size), queryWrapper),
                CourseVOs.EnrollmentVO::from);
    }

    /** 课程统计：已通过正式学员数 + 待审核报名数 + 课程状态。 */
    public CourseStatsVO stats(Long id, Authentication auth) {
        assertCourseOwner(id, auth);
        Course course = courseService.getById(id);
        if (course == null) {
            // 改造前这里是 new BusinessException("课程不存在")，落默认码 400，属语义级错码；本批改正为 404
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        long totalStudents = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getCourseId, id)
                .eq(CourseEnrollment::getIsActive, true)
                .eq(CourseEnrollment::getApprovalStatus, "APPROVED"));
        long pendingStudents = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getCourseId, id)
                .eq(CourseEnrollment::getApprovalStatus, "PENDING"));
        return new CourseStatsVO(totalStudents, pendingStudents, id,
                course.getName(), course.getStatus(), course.getApprovalStatus());
    }

    /** 课程审批流水（按时间正序）。 */
    public List<CourseVOs.ApprovalRecordVO> approvalRecords(Long id, Authentication auth) {
        assertCourseOwner(id, auth);
        return CourseVOs.ApprovalRecordVO.from(courseApprovalService.listByCourse(id));
    }

    // ===================== 写面 =====================

    /**
     * 创建开课任务（V28 三层模型）。
     *
     * <p>教师：授课教师固定为本人；管理员：必须显式指定 {@code teacherId}，可代建。
     * 院系管理员代建时，课程库定义须属本院（服务层会再校验一次教师学院）。</p>
     */
    public CourseVOs.CourseVO create(CourseSaveDTO dto, Authentication auth) {
        // 入参收 DTO：id／审批字段（approvalStatus/approvedBy/...）与归档时间不接受客户端传入
        Course course = dto.toEntity();
        Long userId = (Long) auth.getPrincipal();
        boolean admin = AccessSupport.isAdmin(auth);
        Long teacherId = admin ? course.getTeacherId() : userId;
        if (teacherId == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "管理员代建时必须指定授课教师");
        }
        if (admin && course.getDefinitionId() != null) {
            // 院系管理员只能在自己学院的课程库下代建；服务层会再次校验教师学院。
            CourseDefinition definition = definitionService.getById(course.getDefinitionId());
            if (definition != null) {
                adminScopeGuard.assertCollegeAccess(definition.getCollegeId());
            }
        }
        // 旧入口（无 definitionId）仍由服务层生成兼容课程定义。
        if (!admin) {
            courseApprovalService.applyOwnerCollege(course, userId);
        }
        Course saved = courseService.createCourse(course, teacherId);
        courseApprovalService.recordSubmission(saved.getId(), userId);
        return CourseVOs.CourseVO.from(saved);
    }

    /**
     * 修改课程信息（课程创建者权限；WP9 管理员只读矩阵——管理员 403）。
     *
     * <p>存在性校验、权限校验与落库都在服务层完成（事务化）。</p>
     */
    public CourseVOs.CourseVO update(Long id, CourseSaveDTO dto, Authentication auth) {
        Course course = dto.toEntity();
        Long userId = (Long) auth.getPrincipal();
        boolean isAdmin = AccessSupport.isAdmin(auth);
        if (adminScopeGuard.isCollegeAdmin()) {
            Course existing = courseService.getById(id);
            if (existing == null) {
                throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
            }
            adminScopeGuard.assertCourseAccess(existing);
        }
        return CourseVOs.CourseVO.from(courseService.updateCourse(id, course, userId, isAdmin));
    }

    /** 结课归档（写入结课时间，关闭课程及其教学班）。 */
    public CourseVOs.CourseVO complete(Long id, Authentication auth) {
        assertCourseOwner(id, auth);
        Long operatorId = (Long) auth.getPrincipal();
        return CourseVOs.CourseVO.from(
                courseService.completeCourse(id, operatorId, AccessSupport.isAdmin(auth)));
    }

    /** 删除课程（软删除；教师仅限本人创建的课程）。 */
    public void delete(Long id, Authentication auth) {
        assertCourseOwner(id, auth);
        courseService.deleteCourseById(id);
    }

    /** 审批通过（仅管理员；同时落 approved_by 与 APPROVE 流水，V13）。 */
    public CourseVOs.CourseVO approve(Long id, Authentication auth) {
        assertCollegeScope(id);
        return CourseVOs.CourseVO.from(courseApprovalService.approve(id, (Long) auth.getPrincipal()));
    }

    /** 启用/禁用切换（教学写面；教师仅限本人课程）。 */
    public CourseVOs.CourseVO toggle(Long id, Authentication auth) {
        assertCourseOwner(id, auth);
        return CourseVOs.CourseVO.from(courseService.toggleCourseStatus(id));
    }

    /** 审批驳回（仅管理员；同时追加 REJECT 流水，V13）。 */
    public CourseVOs.CourseVO reject(Long id, String reason, Authentication auth) {
        assertCollegeScope(id);
        return CourseVOs.CourseVO.from(
                courseApprovalService.reject(id, (Long) auth.getPrincipal(), reason));
    }

    /** 重新提交被驳回的课程（教师仅限本人课程；历史审批流水保留）。 */
    public CourseVOs.CourseVO resubmit(Long id, Authentication auth) {
        assertCourseOwner(id, auth);
        return CourseVOs.CourseVO.from(
                courseApprovalService.resubmit(id, (Long) auth.getPrincipal()));
    }

    // ===================== 内部：权限与转换 =====================

    /**
     * 课程归属校验：管理员放行（转学院范围校验）；教师仅能操作自己创建的课程。
     */
    private void assertCourseOwner(Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (AccessSupport.isAdmin(auth)) {
            assertCollegeScope(courseId);
            return;
        }
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        if (!userId.equals(course.getTeacherId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权操作该课程");
        }
    }

    /**
     * 院系管理员按授课教师学院限制课程访问；校级管理员不做额外限制。
     */
    private void assertCollegeScope(Long courseId) {
        if (!adminScopeGuard.isCollegeAdmin()) {
            return;
        }
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        adminScopeGuard.assertCourseAccess(course);
    }

    /**
     * 课程列表学院过滤：院系管理员强制本院（显式请求他院 403）；校级管理员可按参数筛学院。
     */
    private void applyCollegeFilter(LambdaQueryWrapper<Course> queryWrapper, Long requestedCollegeId) {
        Long scopedCollegeId = adminScopeGuard.currentCollegeFilter();
        if (scopedCollegeId != null) {
            if (requestedCollegeId != null && !scopedCollegeId.equals(requestedCollegeId)) {
                throw new BusinessException(ApiCode.FORBIDDEN, "无权查看其他学院的课程");
            }
            queryWrapper.eq(Course::getCollegeId, scopedCollegeId);
            return;
        }
        if (requestedCollegeId != null) {
            queryWrapper.eq(Course::getCollegeId, requestedCollegeId);
        }
    }

}
