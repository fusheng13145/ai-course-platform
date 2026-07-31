package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class CourseEnrollmentServiceImpl extends ServiceImpl<CourseEnrollmentMapper, CourseEnrollment> implements CourseEnrollmentService {

    private final CourseService courseService;

    public CourseEnrollmentServiceImpl(CourseService courseService) {
        this.courseService = courseService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseEnrollment enroll(Long studentId, Long courseId) {
        // 校验是否已有有效报名
        LambdaQueryWrapper<CourseEnrollment> existWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getIsActive, true);
        long existCount = this.count(existWrapper);
        if (existCount > 0) {
            throw new BusinessException("您已报名该课程");
        }

        // 检查是否存在软退课记录（is_active=false），若存在则直接恢复
        LambdaQueryWrapper<CourseEnrollment> inactiveWrapper = new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getIsActive, false);
        CourseEnrollment inactiveEnrollment = this.getOne(inactiveWrapper);
        if (inactiveEnrollment != null) {
            inactiveEnrollment.setIsActive(true);
            inactiveEnrollment.setApprovalStatus("APPROVED");
            inactiveEnrollment.setProgress(BigDecimal.ZERO);
            this.updateById(inactiveEnrollment);
            return inactiveEnrollment;
        }

        // 组装报名数据
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(courseId);

        // 根据课程报名类型设置审核状态
        Course course = courseService.getById(courseId);
        String enrollmentType = course != null ? course.getEnrollmentType() : "OPEN";
        if ("APPROVAL".equals(enrollmentType) || "RESTRICTED".equals(enrollmentType)) {
            enrollment.setApprovalStatus("PENDING");
        } else {
            enrollment.setApprovalStatus("APPROVED");
        }
        enrollment.setIsActive(true);
        enrollment.setProgress(BigDecimal.ZERO);

        this.save(enrollment);
        return enrollment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enrollStudent(Long studentId, Long courseId) {
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        CourseEnrollment existing = this.getOne(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getStudentId, studentId)
                        .eq(CourseEnrollment::getCourseId, courseId));
        if (existing != null) {
            throw new BusinessException("已加入该课程");
        }
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(courseId);
        enrollment.setIsActive(true);
        enrollment.setApprovalStatus("APPROVED");
        enrollment.setEnrolledAt(LocalDateTime.now());
        this.save(enrollment);
    }

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
