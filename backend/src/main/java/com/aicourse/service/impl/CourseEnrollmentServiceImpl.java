package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiCode;
import com.aicourse.common.EnrollmentSource;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.TeachingClass;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * 课程选课服务实现：相关业务逻辑入口，提供增删改查与编排能力。
 */
@Service
public class CourseEnrollmentServiceImpl extends ServiceImpl<CourseEnrollmentMapper, CourseEnrollment> implements CourseEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(CourseEnrollmentServiceImpl.class);

    private static final String CLASS_STATUS_ACTIVE = "ACTIVE";

    private final CourseService courseService;
    private final CourseMapper courseMapper;
    private final TeachingClassMapper teachingClassMapper;

    public CourseEnrollmentServiceImpl(CourseService courseService, CourseMapper courseMapper,
                                        TeachingClassMapper teachingClassMapper) {
        this.courseService = courseService;
        this.courseMapper = courseMapper;
        this.teachingClassMapper = teachingClassMapper;
    }


    /** 课程选课相关操作 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseEnrollment enroll(Long studentId, Long courseId) {
        return enroll(studentId, courseId, null);
    }

    /** 课程选课相关操作（V14 / WP5：按教学班容量与开课状态校验） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseEnrollment enroll(Long studentId, Long courseId, Long teachingClassId) {
        // 课程可加入性校验（存在性 / 审批态 / 结课态）：防止加入未审核或已结束课程
        Course course = requireEnrollableCourse(courseId);

        // 校验是否已有有效报名
        LambdaQueryWrapper<CourseEnrollment> existWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getIsActive, true);
        long existCount = this.count(existWrapper);
        if (existCount > 0) {
            throw new BusinessException("您已报名该课程");
        }

        // 解析目标教学班：显式指定须属于本课程且 ACTIVE；未指定时自动选中唯一启用班
        TeachingClass teachingClass = resolveTeachingClass(course, teachingClassId);

        // 容量校验：恢复历史软退课记录同样占用名额，故先校验再恢复
        // 修复 Q-1：先对课程行加悲观锁（FOR UPDATE），使同课程并发选课串行化，杜绝超卖竞争
        courseMapper.selectByIdForUpdate(courseId);
        assertCapacity(course, teachingClass);

        // 检查是否存在软退课记录（is_active=false），若存在则直接恢复
        LambdaQueryWrapper<CourseEnrollment> inactiveWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getIsActive, false);
        CourseEnrollment inactiveEnrollment = this.getOne(inactiveWrapper);
        if (inactiveEnrollment != null) {
            inactiveEnrollment.setIsActive(true);
            inactiveEnrollment.setTeachingClassId(teachingClass.getId());
            inactiveEnrollment.setSource(EnrollmentSource.SELF_ENROLL);
            inactiveEnrollment.setSourceAdminClassId(null);
            inactiveEnrollment.setApprovalStatus(initialApprovalStatus(course));
            inactiveEnrollment.setProgress(BigDecimal.ZERO);
            inactiveEnrollment.setEnrolledAt(LocalDateTime.now());
            this.updateById(inactiveEnrollment);
            return inactiveEnrollment;
        }

        // 组装报名数据
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(courseId);
        enrollment.setTeachingClassId(teachingClass.getId());
        enrollment.setSource(EnrollmentSource.SELF_ENROLL);

        // 根据课程报名类型设置审核状态：审批型课程须落 PENDING 等待教师审批
        enrollment.setApprovalStatus(initialApprovalStatus(course));
        enrollment.setIsActive(true);
        enrollment.setProgress(BigDecimal.ZERO);

        this.save(enrollment);
        return enrollment;
    }


    /** 课程选课相关操作 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enrollStudent(Long studentId, Long courseId) {
        enrollStudent(studentId, courseId, null);
    }

    /** 课程选课相关操作（V14 / WP5：按教学班容量与开课状态校验） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enrollStudent(Long studentId, Long courseId, Long teachingClassId) {
        // 修复（2026-09-14）：原实现仅校验「课程存在 + 未重复加入」，
        // 恒把 approval_status 置为 APPROVED，且完全不校验容量，
        // 导致三条越权/状态机漏洞：①审批型(APPROVAL)课程被直接放行
        // ②未审核(PENDING)/已驳回(REJECTED)课程可被加入 ③选课人数可突破 max_students。
        Course course = requireEnrollableCourse(courseId);

        // 重复判定只看**有效**记录：已退课/已驳回的历史记录允许重新申请
        // （原实现按 (student, course) 全量判重，导致被驳回/退课的学生再也无法重新加入）
        long activeCount = this.count(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getIsActive, true));
        if (activeCount > 0) {
            throw new BusinessException("已加入该课程");
        }
        TeachingClass teachingClass = resolveTeachingClass(course, teachingClassId);
        // 修复 Q-1：悲观锁串行化同课程并发选课，避免「先 count 后 insert」超卖
        courseMapper.selectByIdForUpdate(courseId);
        assertCapacity(course, teachingClass);

        // 复用历史记录重新申请：表上存在 uk(student_id, course_id) 唯一约束，
        // 同一学生对同一课程只能有一行，故必须复用而非新增。
        CourseEnrollment inactive = this.getOne(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getIsActive, false));
        if (inactive != null) {
            inactive.setIsActive(true);
            inactive.setTeachingClassId(teachingClass.getId());
            inactive.setSource(EnrollmentSource.SELF_ENROLL);
            inactive.setSourceAdminClassId(null);
            inactive.setApprovalStatus(initialApprovalStatus(course));
            // 重新申请视为全新流程：清空上一轮审批痕迹
            inactive.setApprovedBy(null);
            inactive.setApprovalTime(null);
            inactive.setApprovalComment(null);
            inactive.setProgress(BigDecimal.ZERO);
            inactive.setEnrolledAt(LocalDateTime.now());
            this.updateById(inactive);
            return;
        }

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(courseId);
        enrollment.setTeachingClassId(teachingClass.getId());
        enrollment.setSource(EnrollmentSource.SELF_ENROLL);
        enrollment.setIsActive(true);
        enrollment.setApprovalStatus(initialApprovalStatus(course));
        enrollment.setProgress(BigDecimal.ZERO);
        enrollment.setEnrolledAt(LocalDateTime.now());

        this.save(enrollment);
    }

    /** 课程选课相关操作 */
    @Override
    public boolean hasApprovedEnrollment(Long studentId, Long courseId) {
        if (studentId == null || courseId == null) {
            return false;
        }
        return this.count(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getIsActive, true)
                .eq(CourseEnrollment::getApprovalStatus, "APPROVED")) > 0;
    }

    /**
     * 教师—学生归属判定（修复 B2 残留）：
     * 先取出该教师名下的全部课程；若该学生在其中任一课程上持有有效且审批通过的选课记录，即判定为「该教师的学生」。
     *
     * <p>选课状态口径与 {@link #hasApprovedEnrollment} 完全一致（{@code is_active=1 AND approval_status='APPROVED'}），
     * 以避免待审批(PENDING)、已驳回(REJECTED)或已退课记录被误判为在读关系。</p>
     */
    @Override
    public boolean hasApprovedEnrollmentWithTeacher(Long studentId, Long teacherId) {
        if (studentId == null || teacherId == null) {
            return false;
        }
        List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, teacherId));
        if (courses.isEmpty()) {
            return false;
        }
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        return this.count(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .in(CourseEnrollment::getCourseId, courseIds)
                .eq(CourseEnrollment::getIsActive, true)
                .eq(CourseEnrollment::getApprovalStatus, "APPROVED")) > 0;
    }

    // ==================== 选课前置校验（内部工具） ====================
    /**
     * 课程「可加入」校验：课程必须存在，且不得处于待审核/已驳回/已结束状态。
     *
     * <p>对 {@code approvalStatus}/{@code status} 为 null 的历史数据保持放行，
     * 避免存量数据与既有单测因缺省字段被误拒。</p>
     */
    private Course requireEnrollableCourse(Long courseId) {
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        String approval = course.getApprovalStatus();
        if ("PENDING".equalsIgnoreCase(approval) || "REJECTED".equalsIgnoreCase(approval)) {
            log.warn("[Enroll] 拒绝加入未通过审核的课程：courseId={}, approvalStatus={}", courseId, approval);
            throw new BusinessException(ApiCode.FORBIDDEN, "课程尚未通过审核，无法加入");
        }
        if ("INACTIVE".equalsIgnoreCase(course.getStatus())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "课程已结束，无法加入");
        }
        return course;
    }

    /**
     * 解析目标教学班（V14 / WP5）。
     *
     * <p>显式指定时必须属于该课程且状态 ACTIVE（不属于本课程 → 400，未启用 → 409）；
     * 未指定时课程下恰有一个 ACTIVE 班则自动选中，多个 → 400 要求显式选择，
     * 一个都没有 → 409（含 D8 停用课程的 DRAFT 班，不产生可报名入口）。</p>
     */
    private TeachingClass resolveTeachingClass(Course course, Long teachingClassId) {
        if (teachingClassId != null) {
            TeachingClass requested = teachingClassMapper.selectById(teachingClassId);
            if (requested == null) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "教学班不存在：" + teachingClassId);
            }
            if (!course.getId().equals(requested.getCourseId())) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "所选教学班不属于该课程");
            }
            if (!CLASS_STATUS_ACTIVE.equalsIgnoreCase(requested.getStatus())) {
                throw new BusinessException(ApiCode.CONFLICT, "教学班未启用，暂不可报名");
            }
            return requested;
        }
        List<TeachingClass> activeClasses = teachingClassMapper.selectList(new LambdaQueryWrapper<TeachingClass>()
                .eq(TeachingClass::getCourseId, course.getId())
                .eq(TeachingClass::getStatus, CLASS_STATUS_ACTIVE)
                .orderByAsc(TeachingClass::getId));
        if (activeClasses.isEmpty()) {
            throw new BusinessException(ApiCode.CONFLICT, "课程暂无启用的教学班，暂不可报名");
        }
        if (activeClasses.size() > 1) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "该课程有多个教学班，请指定教学班后再报名");
        }
        return activeClasses.get(0);
    }

    /**
     * 容量校验：按教学班容量优先，教学班未配置容量时回退课程上限；
     * 统计口径为「该教学班有效选课记录数」（含待审批 PENDING，因其同样占用名额）。
     */
    private void assertCapacity(Course course, TeachingClass teachingClass) {
        Integer max = teachingClass.getCapacity() != null ? teachingClass.getCapacity() : course.getMaxStudents();
        if (max == null || max <= 0) {
            return;
        }
        long active = baseMapper.countActiveByTeachingClassForUpdate(teachingClass.getId());
        if (active >= max) {
            log.warn("[Enroll] 拒绝加入已满员教学班：teachingClassId={}, max={}, current={}",
                    teachingClass.getId(), max, active);
            throw new BusinessException(ApiCode.CONFLICT, "教学班选课人数已满（上限 " + max + " 人）");
        }
    }

    /** 审批型课程（APPROVAL/RESTRICTED）落 PENDING，其余落 APPROVED。 */
    private String initialApprovalStatus(Course course) {
        String type = course.getEnrollmentType();
        if ("APPROVAL".equalsIgnoreCase(type) || "RESTRICTED".equalsIgnoreCase(type)) {
            return "PENDING";
        }
        return "APPROVED";
    }


    /** 课程选课相关操作 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unenroll(Long id, Long userId) {
        CourseEnrollment enrollment = this.getById(id);
        if (enrollment == null || !userId.equals(enrollment.getStudentId())) {
            throw new BusinessException("您没有权限退课或该退课记录不存在");
        }
        enrollment.setIsActive(false);
        this.updateById(enrollment);
    }
}
