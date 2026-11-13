package com.aicourse.service.impl;

import com.aicourse.entity.Announcement;
import com.aicourse.entity.AdminClass;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.common.ApiCode;
import com.aicourse.mapper.AdminClassMapper;
import com.aicourse.mapper.AnnouncementMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.AnnouncementService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 公告服务实现（WP8，V16）。
 * <p>
 * 扇出口径（验收 1）：受众 = 目标人群 ∪ 发布人，按 userId 去重后一次
 * {@code batchSendNotifications}（既有 saveBatch 通道，category=ANNOUNCEMENT），
 * 扇出条数 = 去重人数。验收 2：详情对非受众抛 403，不存在抛 404——两者语义分开，
 * 不把「别人的公告」伪装成「不存在」给可枚举探测留面。验收 3：不迁移历史
 * notifications，收件箱继续读既有表（本服务从不写历史行）。
 */
@Slf4j
@Service
public class AnnouncementServiceImpl extends ServiceImpl<AnnouncementMapper, Announcement>
        implements AnnouncementService {

    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final TeachingClassMapper teachingClassMapper;
    private final AdminClassMapper adminClassMapper;

    public AnnouncementServiceImpl(CourseService courseService,
                                   CourseEnrollmentService enrollmentService,
                                   UserService userService,
                                   NotificationService notificationService,
                                   TeachingClassMapper teachingClassMapper,
                                   AdminClassMapper adminClassMapper) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.teachingClassMapper = teachingClassMapper;
        this.adminClassMapper = adminClassMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Announcement publish(Long courseId, Long teachingClassId, Long adminClassId,
                                String title, String content, Long publisherId, String publisherRole) {
        int targets = (courseId != null ? 1 : 0) + (teachingClassId != null ? 1 : 0)
                + (adminClassId != null ? 1 : 0);
        if (targets != 1) {
            throw new BusinessException(ApiCode.BAD_REQUEST,
                    "公告目标必须且只能指定课程/教学班/行政班之一");
        }
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "公告标题与正文不能为空");
        }

        Announcement announcement = new Announcement();
        announcement.setCourseId(courseId);
        announcement.setTeachingClassId(teachingClassId);
        announcement.setAdminClassId(adminClassId);
        announcement.setTitle(title.trim());
        announcement.setContent(content);
        announcement.setPublisherId(publisherId);
        announcement.setPublisherRole(publisherRole);
        this.save(announcement);

        Set<Long> audience = resolveAudience(courseId, teachingClassId, adminClassId);
        if (publisherId != null) {
            audience.add(publisherId);
        }
        notificationService.batchSendNotifications(List.copyOf(audience),
                title.trim(), content, "ANNOUNCEMENT", "ANNOUNCEMENT", "MEDIUM",
                "/notifications", "{\"announcementId\":" + announcement.getId() + "}");
        log.info("[announcement] id={} 目标 course={} tc={} adminClass={} 扇出={} 人",
                announcement.getId(), courseId, teachingClassId, adminClassId, audience.size());
        return announcement;
    }

    /** 按互斥目标解析受众 userId 集合（去重、去空）。 */
    private Set<Long> resolveAudience(Long courseId, Long teachingClassId, Long adminClassId) {
        Set<Long> audience = new LinkedHashSet<>();
        if (courseId != null) {
            audience.addAll(enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>()
                            .eq(CourseEnrollment::getCourseId, courseId)
                            .eq(CourseEnrollment::getApprovalStatus, "APPROVED")
                            .eq(CourseEnrollment::getIsActive, true))
                    .stream().map(CourseEnrollment::getStudentId).toList());
            Course course = courseService.getById(courseId);
            if (course != null && course.getTeacherId() != null) {
                audience.add(course.getTeacherId());
            }
        } else if (teachingClassId != null) {
            audience.addAll(enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>()
                            .eq(CourseEnrollment::getTeachingClassId, teachingClassId)
                            .eq(CourseEnrollment::getApprovalStatus, "APPROVED")
                            .eq(CourseEnrollment::getIsActive, true))
                    .stream().map(CourseEnrollment::getStudentId).toList());
            TeachingClass tc = teachingClassMapper.selectById(teachingClassId);
            if (tc != null && tc.getLeadTeacherId() != null) {
                audience.add(tc.getLeadTeacherId());
            }
        } else {
            audience.addAll(userService.list(new LambdaQueryWrapper<User>()
                            .eq(User::getAdminClassId, adminClassId)
                            .eq(User::getIsActive, true))
                    .stream().map(User::getId).toList());
            AdminClass adminClass = adminClassMapper.selectById(adminClassId);
            if (adminClass != null && adminClass.getAdvisorTeacherId() != null) {
                audience.add(adminClass.getAdvisorTeacherId());
            }
        }
        audience.remove(null);
        return audience;
    }

    @Override
    public IPage<Announcement> pageVisible(long page, long size, Long userId) {
        AudienceScope scope = audienceScopeOf(userId);
        // 三个维度都无归属 ⇒ 空页直返；否则空 and() 会让 WHERE 退化消失、变成全表可见
        if (scope.courseIds.isEmpty() && scope.teachingClassIds.isEmpty() && scope.adminClassIds.isEmpty()) {
            return new Page<>(page, size);
        }
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .and(w -> {
                    if (!scope.courseIds.isEmpty()) {
                        w.or().in(Announcement::getCourseId, scope.courseIds);
                    }
                    if (!scope.teachingClassIds.isEmpty()) {
                        w.or().in(Announcement::getTeachingClassId, scope.teachingClassIds);
                    }
                    if (!scope.adminClassIds.isEmpty()) {
                        w.or().in(Announcement::getAdminClassId, scope.adminClassIds);
                    }
                })
                .orderByDesc(Announcement::getCreatedAt);
        return this.page(new Page<>(page, size), wrapper);
    }

    @Override
    public Announcement getVisible(Long id, Long userId) {
        Announcement announcement = this.getById(id);
        if (announcement == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "公告不存在");
        }
        AudienceScope scope = audienceScopeOf(userId);
        boolean inCourse = announcement.getCourseId() != null && scope.courseIds.contains(announcement.getCourseId());
        boolean inTeachingClass = announcement.getTeachingClassId() != null
                && scope.teachingClassIds.contains(announcement.getTeachingClassId());
        boolean inAdminClass = announcement.getAdminClassId() != null
                && scope.adminClassIds.contains(announcement.getAdminClassId());
        if (!inCourse && !inTeachingClass && !inAdminClass) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权查看该公告");
        }
        return announcement;
    }

    /** 当前用户在三个目标维度上的归属集合（选课 → 课程/教学班；行政班 → 班级）。 */
    private AudienceScope audienceScopeOf(Long userId) {
        AudienceScope scope = new AudienceScope();
        if (userId == null) {
            return scope;
        }
        List<CourseEnrollment> enrollments = enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, userId)
                .eq(CourseEnrollment::getApprovalStatus, "APPROVED")
                .eq(CourseEnrollment::getIsActive, true));
        for (CourseEnrollment enrollment : enrollments) {
            if (enrollment.getCourseId() != null) {
                scope.courseIds.add(enrollment.getCourseId());
            }
            if (enrollment.getTeachingClassId() != null) {
                scope.teachingClassIds.add(enrollment.getTeachingClassId());
            }
        }
        for (Course course : courseService.list(new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, userId))) {
            scope.courseIds.add(course.getId());
        }
        for (TeachingClass tc : teachingClassMapper.selectList(new LambdaQueryWrapper<TeachingClass>()
                .eq(TeachingClass::getLeadTeacherId, userId))) {
            scope.teachingClassIds.add(tc.getId());
        }
        User user = userService.getById(userId);
        if (user != null && user.getAdminClassId() != null) {
            scope.adminClassIds.add(user.getAdminClassId());
        }
        for (AdminClass adminClass : adminClassMapper.selectList(new LambdaQueryWrapper<AdminClass>()
                .eq(AdminClass::getAdvisorTeacherId, userId))) {
            scope.adminClassIds.add(adminClass.getId());
        }
        return scope;
    }

    /** 三个目标维度的归属集合（LinkedHashSet 兼作去重）。 */
    private static class AudienceScope {
        final Set<Long> courseIds = new LinkedHashSet<>();
        final Set<Long> teachingClassIds = new LinkedHashSet<>();
        final Set<Long> adminClassIds = new LinkedHashSet<>();
    }
}
