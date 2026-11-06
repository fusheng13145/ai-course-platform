package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseApprovalRecord;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseApprovalRecordMapper;
import com.aicourse.service.CourseApprovalService;
import com.aicourse.service.CourseService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 课程审批编排实现（V13 / B-12-P-08 WP3）。
 */
@Service
public class CourseApprovalServiceImpl implements CourseApprovalService {

    private static final Logger log = LoggerFactory.getLogger(CourseApprovalServiceImpl.class);

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    private final CourseService courseService;
    private final CourseApprovalRecordMapper recordMapper;
    private final UserService userService;

    public CourseApprovalServiceImpl(CourseService courseService,
                                     CourseApprovalRecordMapper recordMapper,
                                     UserService userService) {
        this.courseService = courseService;
        this.recordMapper = recordMapper;
        this.userService = userService;
    }

    @Override
    public void applyOwnerCollege(Course course, Long teacherId) {
        if (course == null || teacherId == null) {
            return;
        }
        User owner = userService.getById(teacherId);
        // 负责人学院是唯一事实来源；客户端传入的 collegeId 不可信，一律覆盖。
        course.setCollegeId(owner != null ? owner.getCollegeId() : null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordSubmission(Long courseId, Long operatorId) {
        record(courseId, operatorId, "SUBMIT", null, PENDING, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course approve(Long courseId, Long operatorId) {
        Course before = requireCourse(courseId);
        boolean alreadyApproved = APPROVED.equalsIgnoreCase(before.getApprovalStatus())
                && "ACTIVE".equalsIgnoreCase(before.getStatus());
        Course after = courseService.approveCourse(courseId, operatorId);
        if (!alreadyApproved) {
            record(courseId, operatorId, "APPROVE", before.getApprovalStatus(), APPROVED, null);
        }
        return after;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course reject(Long courseId, Long operatorId, String reason) {
        Course before = requireCourse(courseId);
        Course after = courseService.rejectCourse(courseId, reason);
        record(courseId, operatorId, "REJECT", before.getApprovalStatus(), REJECTED, reason);
        return after;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course resubmit(Long courseId, Long operatorId) {
        Course before = requireCourse(courseId);
        Course after = courseService.resubmitCourse(courseId);
        record(courseId, operatorId, "RESUBMIT", before.getApprovalStatus(), PENDING, null);
        return after;
    }

    @Override
    public List<CourseApprovalRecord> listByCourse(Long courseId) {
        return recordMapper.selectList(new LambdaQueryWrapper<CourseApprovalRecord>()
                .eq(CourseApprovalRecord::getCourseId, courseId)
                .orderByAsc(CourseApprovalRecord::getCreatedAt)
                .orderByAsc(CourseApprovalRecord::getId));
    }

    private Course requireCourse(Long courseId) {
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        return course;
    }

    private void record(Long courseId, Long operatorId, String action,
                        String fromStatus, String toStatus, String comment) {
        CourseApprovalRecord record = new CourseApprovalRecord();
        record.setCourseId(courseId);
        record.setOperatorId(operatorId);
        record.setAction(action);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setComment(comment);
        recordMapper.insert(record);
        log.info("课程审批流水：courseId={}, action={}, {} -> {}", courseId, action, fromStatus, toStatus);
    }
}
