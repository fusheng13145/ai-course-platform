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

    public AdvisorServiceImpl(AdminClassMapper adminClassMapper,
                              AdminClassAccessControl adminClassAccessControl,
                              AdminScopeGuard adminScopeGuard,
                              UserService userService,
                              CourseEnrollmentService enrollmentService) {
        this.adminClassMapper = adminClassMapper;
        this.adminClassAccessControl = adminClassAccessControl;
        this.adminScopeGuard = adminScopeGuard;
        this.userService = userService;
        this.enrollmentService = enrollmentService;
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

    private AdminClass requireAdminClass(Long adminClassId) {
        AdminClass adminClass = adminClassMapper.selectById(adminClassId);
        if (adminClass == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "行政班不存在");
        }
        return adminClass;
    }
}
