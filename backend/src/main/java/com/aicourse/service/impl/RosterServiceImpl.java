package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.common.EnrollmentSource;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.AdminClass;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.TeachingClassAdminClass;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.AdminClassMapper;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.mapper.TeachingClassAdminClassMapper;
import com.aicourse.mapper.UserMapper;
import com.aicourse.security.TeachingClassAccessControl;
import com.aicourse.service.RosterService;
import com.aicourse.vo.RosterSyncResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@link RosterService} 实现（V14 / B-12-P-08 WP5）。
 *
 * <p>关键口径：</p>
 * <ul>
 *     <li>导入/重同步只写 {@code source=ADMIN_CLASS_IMPORT} 的记录；</li>
 *     <li>{@code LEGACY}/{@code SELF_ENROLL} 记录（含已软退课）不被本服务修改，
 *         其中有效记录一律计入 {@code skipped}/{@code conflicted}，不静默覆盖；</li>
 *     <li>重同步的 {@code removed} 仅针对本教学班、本行政班来源的
 *         {@code ADMIN_CLASS_IMPORT} 有效记录做软移除；</li>
 *     <li>容量按教学班容量校验，超限直接 409 并回滚整次导入。</li>
 * </ul>
 */
@Service
public class RosterServiceImpl implements RosterService {

    private static final String ADMIN_CLASS_ACTIVE = "ACTIVE";
    private static final String ADMIN_CLASS_ARCHIVED = "ARCHIVED";
    private static final String LINK_ACTIVE = "ACTIVE";
    private static final String LINK_REMOVED = "REMOVED";

    private final TeachingClassAdminClassMapper teachingClassAdminClassMapper;
    private final AdminClassMapper adminClassMapper;
    private final UserMapper userMapper;
    private final CourseEnrollmentMapper courseEnrollmentMapper;
    private final TeachingClassAccessControl accessControl;

    public RosterServiceImpl(TeachingClassAdminClassMapper teachingClassAdminClassMapper,
                             AdminClassMapper adminClassMapper,
                             UserMapper userMapper,
                             CourseEnrollmentMapper courseEnrollmentMapper,
                             TeachingClassAccessControl accessControl) {
        this.teachingClassAdminClassMapper = teachingClassAdminClassMapper;
        this.adminClassMapper = adminClassMapper;
        this.userMapper = userMapper;
        this.courseEnrollmentMapper = courseEnrollmentMapper;
        this.accessControl = accessControl;
    }

    @Override
    public List<TeachingClassAdminClass> listSources(Long teachingClassId, Long operatorId) {
        accessControl.assertRosterRead(teachingClassId, operatorId);
        return teachingClassAdminClassMapper.selectList(
                new LambdaQueryWrapper<TeachingClassAdminClass>()
                        .eq(TeachingClassAdminClass::getTeachingClassId, teachingClassId)
                        .orderByAsc(TeachingClassAdminClass::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RosterSyncResultVO importFromAdminClass(Long teachingClassId, Long adminClassId, Long operatorId) {
        TeachingClass teachingClass = accessControl.assertRosterManage(teachingClassId, operatorId);
        AdminClass adminClass = requireAdminClass(adminClassId);
        if (ADMIN_CLASS_ARCHIVED.equalsIgnoreCase(adminClass.getStatus())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "行政班已归档，不能导入名单");
        }
        Course course = accessControl.requireCourse(teachingClass.getCourseId());
        upsertSourceLink(teachingClassId, adminClassId, operatorId);
        return syncSource(teachingClass, course, adminClass, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RosterSyncResultVO resync(Long teachingClassId, Long adminClassId, Long operatorId) {
        TeachingClass teachingClass = accessControl.assertRosterManage(teachingClassId, operatorId);
        Course course = accessControl.requireCourse(teachingClass.getCourseId());

        LambdaQueryWrapper<TeachingClassAdminClass> linkQuery = new LambdaQueryWrapper<TeachingClassAdminClass>()
                .eq(TeachingClassAdminClass::getTeachingClassId, teachingClassId)
                .eq(TeachingClassAdminClass::getStatus, LINK_ACTIVE)
                .orderByAsc(TeachingClassAdminClass::getId);
        if (adminClassId != null) {
            linkQuery.eq(TeachingClassAdminClass::getAdminClassId, adminClassId);
        }
        List<TeachingClassAdminClass> links = teachingClassAdminClassMapper.selectList(linkQuery);
        if (links.isEmpty()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "该教学班尚未导入任何行政班");
        }

        RosterSyncResultVO merged = new RosterSyncResultVO();
        // 批量取行政班（B-03 N+1 治理，2026-11-07）：链接数通常为个位数，但同步入口可能被
        // 批量归档/重放触发，逐条 selectById 改为一次 IN 查询后按 id 取用；遍历仍按链接顺序，
        // merge 结果与修复前一致。
        List<Long> adminClassIds = links.stream()
                .map(TeachingClassAdminClass::getAdminClassId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, AdminClass> adminClassById = adminClassIds.isEmpty()
                ? Map.of()
                : adminClassMapper.selectBatchIds(adminClassIds).stream()
                        .filter(ac -> ac.getId() != null)
                        .collect(java.util.stream.Collectors.toMap(AdminClass::getId, ac -> ac, (a, b) -> a));
        for (TeachingClassAdminClass link : links) {
            AdminClass adminClass = adminClassById.get(link.getAdminClassId());
            if (adminClass == null) {
                continue;
            }
            merged.merge(syncSource(teachingClass, course, adminClass, true));
        }
        return merged;
    }

    /**
     * 同步单个行政班的名单到教学班。
     *
     * @param removeMissing true 时对已离开行政班、且来源为本服务导入的记录做软移除
     */
    private RosterSyncResultVO syncSource(TeachingClass teachingClass, Course course,
                                          AdminClass adminClass, boolean removeMissing) {
        RosterSyncResultVO result = new RosterSyncResultVO();

        List<User> students = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getAdminClassId, adminClass.getId())
                .eq(User::getRole, RoleConstants.STUDENT)
                .eq(User::getIsActive, true)
                .orderByAsc(User::getId));
        Set<Long> currentStudentIds = new HashSet<>();
        for (User student : students) {
            currentStudentIds.add(student.getId());
        }

        Map<Long, CourseEnrollment> existingByStudent = new HashMap<>();
        List<CourseEnrollment> existing = courseEnrollmentMapper.selectList(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, course.getId()));
        for (CourseEnrollment enrollment : existing) {
            existingByStudent.put(enrollment.getStudentId(), enrollment);
        }

        Integer capacity = effectiveCapacity(teachingClass, course);
        long occupied = capacity == null ? 0 :
                courseEnrollmentMapper.countActiveByTeachingClassForUpdate(teachingClass.getId());

        for (User student : students) {
            CourseEnrollment enrollment = existingByStudent.get(student.getId());
            if (enrollment == null) {
                assertCapacity(capacity, occupied + result.getAdded());
                saveImported(teachingClass, adminClass, course, student.getId());
                result.setAdded(result.getAdded() + 1);
                continue;
            }
            if (Boolean.TRUE.equals(enrollment.getIsActive())) {
                if (Objects.equals(teachingClass.getId(), enrollment.getTeachingClassId())) {
                    // 已在本教学班有效：重复导入幂等，不覆盖来源
                    result.setSkipped(result.getSkipped() + 1);
                } else {
                    // 学生已在该课程其他教学班有效选课：不静默覆盖
                    result.setConflicted(result.getConflicted() + 1);
                    result.getConflictedStudentIds().add(student.getId());
                }
                continue;
            }
            // 仅复活「无其他有效选课」的历史行：软退课后重新加入，复用唯一键行
            assertCapacity(capacity, occupied + result.getAdded());
            revokeAsImported(enrollment, teachingClass, adminClass, course);
            result.setAdded(result.getAdded() + 1);
        }

        if (removeMissing) {
            for (CourseEnrollment enrollment : existing) {
                if (!isImportedFrom(enrollment, teachingClass, adminClass)
                        || !Boolean.TRUE.equals(enrollment.getIsActive())) {
                    continue;
                }
                if (currentStudentIds.contains(enrollment.getStudentId())) {
                    continue;
                }
                // 重同步软移除：仅限本教学班、本行政班来源的导入记录
                enrollment.setIsActive(false);
                courseEnrollmentMapper.updateById(enrollment);
                result.setRemoved(result.getRemoved() + 1);
            }
        }
        return result;
    }

    private void upsertSourceLink(Long teachingClassId, Long adminClassId, Long operatorId) {
        TeachingClassAdminClass link = teachingClassAdminClassMapper.selectOne(
                new LambdaQueryWrapper<TeachingClassAdminClass>()
                        .eq(TeachingClassAdminClass::getTeachingClassId, teachingClassId)
                        .eq(TeachingClassAdminClass::getAdminClassId, adminClassId));
        if (link == null) {
            link = new TeachingClassAdminClass();
            link.setTeachingClassId(teachingClassId);
            link.setAdminClassId(adminClassId);
            link.setStatus(LINK_ACTIVE);
            link.setImportedBy(operatorId);
            link.setImportedAt(LocalDateTime.now());
            teachingClassAdminClassMapper.insert(link);
            return;
        }
        if (LINK_REMOVED.equalsIgnoreCase(link.getStatus())) {
            link.setStatus(LINK_ACTIVE);
            link.setImportedBy(operatorId);
            link.setImportedAt(LocalDateTime.now());
            teachingClassAdminClassMapper.updateById(link);
        }
        // 已 ACTIVE 的重复导入不重置导入人/时间（幂等）
    }

    private void saveImported(TeachingClass teachingClass, AdminClass adminClass, Course course, Long studentId) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(course.getId());
        enrollment.setTeachingClassId(teachingClass.getId());
        enrollment.setSource(EnrollmentSource.ADMIN_CLASS_IMPORT);
        enrollment.setSourceAdminClassId(adminClass.getId());
        enrollment.setApprovalStatus(initialApprovalStatus(course));
        enrollment.setIsActive(true);
        enrollment.setProgress(BigDecimal.ZERO);
        enrollment.setEnrolledAt(LocalDateTime.now());
        courseEnrollmentMapper.insert(enrollment);
    }

    private void revokeAsImported(CourseEnrollment enrollment, TeachingClass teachingClass,
                                  AdminClass adminClass, Course course) {
        enrollment.setIsActive(true);
        enrollment.setTeachingClassId(teachingClass.getId());
        enrollment.setSource(EnrollmentSource.ADMIN_CLASS_IMPORT);
        enrollment.setSourceAdminClassId(adminClass.getId());
        enrollment.setApprovalStatus(initialApprovalStatus(course));
        enrollment.setApprovedBy(null);
        enrollment.setApprovalTime(null);
        enrollment.setApprovalComment(null);
        enrollment.setProgress(BigDecimal.ZERO);
        enrollment.setEnrolledAt(LocalDateTime.now());
        courseEnrollmentMapper.updateById(enrollment);
    }

    private static boolean isImportedFrom(CourseEnrollment enrollment, TeachingClass teachingClass,
                                          AdminClass adminClass) {
        return EnrollmentSource.ADMIN_CLASS_IMPORT.equals(enrollment.getSource())
                && Objects.equals(teachingClass.getId(), enrollment.getTeachingClassId())
                && Objects.equals(adminClass.getId(), enrollment.getSourceAdminClassId());
    }

    private AdminClass requireAdminClass(Long adminClassId) {
        AdminClass adminClass = adminClassId == null ? null : adminClassMapper.selectById(adminClassId);
        if (adminClass == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "行政班不存在：" + adminClassId);
        }
        if (!ADMIN_CLASS_ACTIVE.equalsIgnoreCase(adminClass.getStatus())
                && !ADMIN_CLASS_ARCHIVED.equalsIgnoreCase(adminClass.getStatus())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "行政班状态非法：" + adminClass.getStatus());
        }
        return adminClass;
    }

    private static Integer effectiveCapacity(TeachingClass teachingClass, Course course) {
        Integer capacity = teachingClass.getCapacity() != null ? teachingClass.getCapacity() : course.getMaxStudents();
        return capacity == null || capacity <= 0 ? null : capacity;
    }

    private static void assertCapacity(Integer capacity, long projectedCount) {
        if (capacity != null && projectedCount >= capacity) {
            throw new BusinessException(ApiCode.CONFLICT, "教学班名单已满（上限 " + capacity + " 人）");
        }
    }

    /** 审批型课程（APPROVAL/RESTRICTED）落 PENDING，其余落 APPROVED（与自助选课口径一致）。 */
    private static String initialApprovalStatus(Course course) {
        String type = course.getEnrollmentType();
        if ("APPROVAL".equalsIgnoreCase(type) || "RESTRICTED".equalsIgnoreCase(type)) {
            return "PENDING";
        }
        return "APPROVED";
    }
}
