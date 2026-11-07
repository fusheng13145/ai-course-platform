package com.aicourse.service.impl;

import com.aicourse.entity.AdminClass;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.common.ApiCode;
import com.aicourse.mapper.AdminClassMapper;
import com.aicourse.security.AdminClassAccessControl;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.AdvisorService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.UserService;
import com.aicourse.vo.AdvisorClassProfileVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 班主任画像服务实现（WP9）。
 * <p>
 * 聚合口径：画像全部由选课记录（{@code course_enrollments}）聚合而来，只出
 * count/avg/预警数三类聚合值，<b>不返回任何学生个人成绩字段</b>（6A，验收 2 的
 * 响应体断言钉在 {@code AdvisorControllerProfileTest}）。预警 = finalScore &lt; 60。</p>
 */
@Service
public class AdvisorServiceImpl implements AdvisorService {

    private final AdminClassMapper adminClassMapper;
    private final AdminClassAccessControl adminClassAccessControl;
    private final AdminScopeGuard adminScopeGuard;
    private final UserService userService;
    private final CourseEnrollmentService enrollmentService;
    private final com.aicourse.service.CourseService courseService;

    public AdvisorServiceImpl(AdminClassMapper adminClassMapper,
                              AdminClassAccessControl adminClassAccessControl,
                              AdminScopeGuard adminScopeGuard,
                              UserService userService,
                              CourseEnrollmentService enrollmentService,
                              com.aicourse.service.CourseService courseService) {
        this.adminClassMapper = adminClassMapper;
        this.adminClassAccessControl = adminClassAccessControl;
        this.adminScopeGuard = adminScopeGuard;
        this.userService = userService;
        this.enrollmentService = enrollmentService;
        this.courseService = courseService;
    }

    @Override
    public List<AdminClass> listMyClasses(Long advisorId) {
        if (advisorId == null) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "未识别当前用户");
        }
        return adminClassMapper.selectList(new LambdaQueryWrapper<AdminClass>()
                .eq(AdminClass::getAdvisorTeacherId, advisorId)
                .orderByAsc(AdminClass::getId));
    }

    @Override
    public AdvisorClassProfileVO classProfile(Long adminClassId, Long operatorId, boolean adminCaller) {
        AdminClass adminClass;
        if (adminCaller) {
            // 管理员只读矩阵：院系管理员仅本院，校级全校（能力矩阵「班级画像」行）
            adminClass = requireAdminClass(adminClassId);
            adminScopeGuard.assertCollegeAccess(adminClass.getCollegeId());
        } else {
            adminClass = adminClassAccessControl.assertAdminClassAdvisor(adminClassId, operatorId);
        }

        List<Long> studentIds = userService.list(new LambdaQueryWrapper<User>()
                        .eq(User::getAdminClassId, adminClassId)
                        .eq(User::getIsActive, true))
                .stream().map(User::getId).toList();

        AdvisorClassProfileVO profile = new AdvisorClassProfileVO();
        profile.setAdminClassId(adminClass.getId());
        profile.setClassName(adminClass.getName());
        profile.setStudentCount(studentIds.size());
        if (studentIds.isEmpty()) {
            return profile;
        }

        List<CourseEnrollment> enrollments = enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>()
                .in(CourseEnrollment::getStudentId, studentIds)
                .eq(CourseEnrollment::getApprovalStatus, "APPROVED")
                .eq(CourseEnrollment::getIsActive, true));
        if (enrollments.isEmpty()) {
            return profile;
        }

        BigDecimal progressSum = BigDecimal.ZERO;
        BigDecimal scoreSum = BigDecimal.ZERO;
        int progressCount = 0;
        int gradedCount = 0;
        long atRisk = 0;
        for (CourseEnrollment enrollment : enrollments) {
            if (enrollment.getProgress() != null) {
                progressSum = progressSum.add(enrollment.getProgress());
                progressCount++;
            }
            if (enrollment.getFinalScore() != null) {
                scoreSum = scoreSum.add(enrollment.getFinalScore());
                gradedCount++;
                if (enrollment.getFinalScore().compareTo(BigDecimal.valueOf(60)) < 0) {
                    atRisk++;
                }
            }
        }
        profile.setAvgProgress(progressCount == 0 ? null
                : progressSum.divide(BigDecimal.valueOf(progressCount), 1, RoundingMode.HALF_UP));
        profile.setAvgFinalScore(gradedCount == 0 ? null
                : scoreSum.divide(BigDecimal.valueOf(gradedCount), 1, RoundingMode.HALF_UP));
        profile.setAtRiskCount(atRisk);
        profile.setGradedCount(gradedCount);
        return profile;
    }

    @Override
    public List<java.util.Map<String, Object>> classAlerts(Long adminClassId, Long operatorId, boolean adminCaller) {
        if (adminCaller) {
            AdminClass adminClass = adminClassMapper.selectById(adminClassId);
            if (adminClass == null) {
                throw new BusinessException(ApiCode.NOT_FOUND, "行政班不存在");
            }
            adminScopeGuard.assertCollegeAccess(adminClass.getCollegeId());
        } else {
            adminClassAccessControl.assertAdminClassAdvisor(adminClassId, operatorId);
        }
        List<Long> studentIds = userService.list(new LambdaQueryWrapper<User>()
                        .eq(User::getAdminClassId, adminClassId)
                        .eq(User::getIsActive, true))
                .stream().map(User::getId).toList();
        if (studentIds.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        Map<Long, String> names = userService.list(new LambdaQueryWrapper<User>()
                        .in(User::getId, studentIds))
                .stream().collect(java.util.stream.Collectors.toMap(User::getId,
                        u -> u.getFullName() != null && !u.getFullName().isBlank()
                                ? u.getFullName() : u.getUsername(),
                        (a, b) -> a));
        List<CourseEnrollment> enrollments = enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>()
                .in(CourseEnrollment::getStudentId, studentIds)
                .eq(CourseEnrollment::getApprovalStatus, "APPROVED")
                .eq(CourseEnrollment::getIsActive, true));
        Map<Long, String> courseNames = courseService.list(new LambdaQueryWrapper<com.aicourse.entity.Course>()
                        .in(com.aicourse.entity.Course::getId,
                                enrollments.stream().map(CourseEnrollment::getCourseId)
                                        .distinct().toList()))
                .stream().collect(java.util.stream.Collectors.toMap(
                        com.aicourse.entity.Course::getId, com.aicourse.entity.Course::getName, (a, b) -> a));

        List<java.util.Map<String, Object>> alerts = new java.util.ArrayList<>();
        for (CourseEnrollment enrollment : enrollments) {
            List<String> reasons = new java.util.ArrayList<>(2);
            if (enrollment.getProgress() != null
                    && enrollment.getProgress().compareTo(BigDecimal.valueOf(40)) < 0) {
                reasons.add("进度不足40%");
            }
            if (enrollment.getFinalScore() != null
                    && enrollment.getFinalScore().compareTo(BigDecimal.valueOf(60)) < 0) {
                reasons.add("总评不及格");
            }
            if (reasons.isEmpty()) {
                continue;
            }
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>(6);
            row.put("studentId", enrollment.getStudentId());
            row.put("studentName", names.getOrDefault(enrollment.getStudentId(),
                    "学生 #" + enrollment.getStudentId()));
            row.put("courseId", enrollment.getCourseId());
            row.put("courseName", courseNames.getOrDefault(enrollment.getCourseId(),
                    "课程 #" + enrollment.getCourseId()));
            row.put("progress", enrollment.getProgress());
            row.put("finalScore", enrollment.getFinalScore());
            row.put("reasons", String.join("、", reasons));
            alerts.add(row);
        }
        return alerts;
    }

    private AdminClass requireAdminClass(Long adminClassId) {
        AdminClass adminClass = adminClassMapper.selectById(adminClassId);
        if (adminClass == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "行政班不存在");
        }
        return adminClass;
    }
}
