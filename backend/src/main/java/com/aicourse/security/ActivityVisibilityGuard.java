package com.aicourse.security;

import com.aicourse.common.ActivityVisibility;
import com.aicourse.common.ApiCode;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamTeachingClass;
import com.aicourse.entity.Task;
import com.aicourse.entity.TaskTeachingClass;
import com.aicourse.entity.TeachingClass;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamTeachingClassMapper;
import com.aicourse.mapper.TaskTeachingClassMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 教学活动可见性守卫（V15 / B-12-P-08 WP6）。
 *
 * <p>口径（拆包方案 5A / WP6 验收）：</p>
 * <ul>
 *     <li>可见范围 COURSE：该课程全部教学班可见（新建教学班自动继承，无需目标行）；</li>
 *     <li>可见范围 CLASSES：仅目标教学班可见；同课程非目标教学班教师/学生不可见；</li>
 *     <li>课程负责人对其课程内活动保持全量可见；同课程其他教学班主讲仅能看到 COURSE 活动
 *         与其主讲教学班被点名的 CLASSES 活动；</li>
 *     <li>管理员治理只读放行，但发布与改目标由控制器显式 403。</li>
 * </ul>
 */
@Component
public class ActivityVisibilityGuard {

    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;
    private final TeachingClassMapper teachingClassMapper;
    private final TaskTeachingClassMapper taskTeachingClassMapper;
    private final ExamTeachingClassMapper examTeachingClassMapper;

    public ActivityVisibilityGuard(CourseService courseService,
                                   CourseEnrollmentService enrollmentService,
                                   TeachingClassMapper teachingClassMapper,
                                   TaskTeachingClassMapper taskTeachingClassMapper,
                                   ExamTeachingClassMapper examTeachingClassMapper) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.teachingClassMapper = teachingClassMapper;
        this.taskTeachingClassMapper = taskTeachingClassMapper;
        this.examTeachingClassMapper = examTeachingClassMapper;
    }

    /**
     * 管理员不得发布教学活动或修改目标教学班（WP6 验收第 4 条）。
     */
    public void assertNotAdmin(Authentication auth) {
        if (isAdmin(auth)) {
            throw new BusinessException(ApiCode.FORBIDDEN,
                    "管理员仅治理只读，不得发布教学活动或修改目标教学班");
        }
    }

    /**
     * 任务详情可见性：学生须已发布且已选课，CLASSES 还须命中其教学班；
     * 教师须为课程负责人，或经教学班获得可见性。
     */
    public void assertVisibleTask(Task task, Authentication auth) {
        if (task == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "任务不存在");
        }
        if (isAdmin(auth)) {
            return;
        }
        Long userId = principalId(auth);
        if (isStudent(auth)) {
            boolean published = "PUBLISHED".equals(task.getStatus())
                    || Boolean.TRUE.equals(task.getIsPublished());
            if (!published) {
                throw new BusinessException(ApiCode.FORBIDDEN, "任务尚未发布，无权查看");
            }
            CourseEnrollment enrollment = approvedEnrollment(userId, task.getCourseId(), null);
            if (enrollment == null) {
                throw new BusinessException(ApiCode.FORBIDDEN, "尚未加入该课程，无权查看课程内容");
            }
            if (isClassesScope(task.getVisibility())
                    && !taskTargeted(task.getId(), enrollment.getTeachingClassId())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "该任务未对你所在的教学班开放");
            }
            return;
        }
        Course course = requireCourse(task.getCourseId());
        if (Objects.equals(userId, course.getTeacherId())) {
            return;
        }
        Set<Long> ledClassIds = ledClassIds(userId, course.getId());
        if (isClassesScope(task.getVisibility())) {
            if (taskTargetsAny(task.getId(), ledClassIds)) {
                return;
            }
        } else if (!ledClassIds.isEmpty()) {
            return;
        }
        throw new BusinessException(ApiCode.FORBIDDEN, "无权查看其他教师课程的内容");
    }

    /**
     * 试卷详情可见性：同任务；是否已发布由既有发布状态口径负责，本守卫只判教学班范围。
     */
    public void assertVisibleExamPaper(ExamPaper paper, Authentication auth) {
        if (paper == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "试卷不存在");
        }
        if (isAdmin(auth)) {
            return;
        }
        Long userId = principalId(auth);
        if (isStudent(auth)) {
            CourseEnrollment enrollment = approvedEnrollment(userId, paper.getCourseId(), null);
            if (enrollment == null) {
                throw new BusinessException(ApiCode.FORBIDDEN, "尚未加入该课程，无权查看试卷");
            }
            if (isClassesScope(paper.getVisibility())
                    && !examPaperTargeted(paper.getId(), enrollment.getTeachingClassId())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "该试卷未对你所在的教学班开放");
            }
            return;
        }
        Course course = requireCourse(paper.getCourseId());
        if (Objects.equals(userId, course.getTeacherId())) {
            return;
        }
        Set<Long> ledClassIds = ledClassIds(userId, course.getId());
        if (isClassesScope(paper.getVisibility())) {
            if (examPaperTargetsAny(paper.getId(), ledClassIds)) {
                return;
            }
        } else if (!ledClassIds.isEmpty()) {
            return;
        }
        throw new BusinessException(ApiCode.FORBIDDEN, "无权查看其他教师课程的试卷");
    }

    /**
     * 任务列表限定到当前主体可见的教学班范围。
     *
     * @param teachingClassId 可选的教学班筛选；传入时校验该主体与该教学班的关系
     */
    public void applyTaskScope(LambdaQueryWrapper<Task> wrapper, Authentication auth, Long teachingClassId) {
        if (isAdmin(auth)) {
            return;
        }
        Long userId = principalId(auth);
        if (teachingClassId != null) {
            TeachingClass teachingClass = requireClass(teachingClassId);
            assertClassViewer(userId, isStudent(auth), teachingClass);
            wrapper.eq(Task::getCourseId, teachingClass.getCourseId());
            addTaskClassVisibility(wrapper, List.of(teachingClass.getId()));
            return;
        }
        if (isStudent(auth)) {
            List<CourseEnrollment> enrollments = approvedEnrollments(userId, null);
            if (enrollments.isEmpty()) {
                wrapper.apply("1 = 0");
                return;
            }
            Set<Long> courseIds = new LinkedHashSet<>();
            Set<Long> classIds = new LinkedHashSet<>();
            for (CourseEnrollment enrollment : enrollments) {
                if (enrollment.getCourseId() != null) {
                    courseIds.add(enrollment.getCourseId());
                }
                if (enrollment.getTeachingClassId() != null) {
                    classIds.add(enrollment.getTeachingClassId());
                }
            }
            if (courseIds.isEmpty()) {
                wrapper.apply("1 = 0");
                return;
            }
            wrapper.in(Task::getCourseId, courseIds);
            addTaskClassVisibility(wrapper, classIds);
            return;
        }
        applyTeacherTaskScope(wrapper, userId);
    }

    /**
     * 试卷列表限定到当前主体可见的教学班范围，口径同任务。
     */
    public void applyExamPaperScope(LambdaQueryWrapper<ExamPaper> wrapper, Authentication auth,
                                    Long teachingClassId) {
        if (isAdmin(auth)) {
            return;
        }
        Long userId = principalId(auth);
        if (teachingClassId != null) {
            TeachingClass teachingClass = requireClass(teachingClassId);
            assertClassViewer(userId, isStudent(auth), teachingClass);
            wrapper.eq(ExamPaper::getCourseId, teachingClass.getCourseId());
            addExamClassVisibility(wrapper, List.of(teachingClass.getId()));
            return;
        }
        if (isStudent(auth)) {
            List<CourseEnrollment> enrollments = approvedEnrollments(userId, null);
            if (enrollments.isEmpty()) {
                wrapper.apply("1 = 0");
                return;
            }
            Set<Long> courseIds = new LinkedHashSet<>();
            Set<Long> classIds = new LinkedHashSet<>();
            for (CourseEnrollment enrollment : enrollments) {
                if (enrollment.getCourseId() != null) {
                    courseIds.add(enrollment.getCourseId());
                }
                if (enrollment.getTeachingClassId() != null) {
                    classIds.add(enrollment.getTeachingClassId());
                }
            }
            if (courseIds.isEmpty()) {
                wrapper.apply("1 = 0");
                return;
            }
            wrapper.in(ExamPaper::getCourseId, courseIds);
            addExamClassVisibility(wrapper, classIds);
            return;
        }
        applyTeacherExamScope(wrapper, userId);
    }

    private void applyTeacherTaskScope(LambdaQueryWrapper<Task> wrapper, Long userId) {
        Set<Long> ownedCourseIds = ownedCourseIds(userId);
        Set<Long> ledCourseIds = new LinkedHashSet<>();
        Set<Long> ledClassIds = new LinkedHashSet<>();
        collectLedClasses(userId, ledCourseIds, ledClassIds);
        if (ownedCourseIds.isEmpty() && ledCourseIds.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(outer -> {
            boolean hasOwned = !ownedCourseIds.isEmpty();
            if (hasOwned) {
                outer.in(Task::getCourseId, ownedCourseIds);
            }
            if (!ledCourseIds.isEmpty()) {
                if (hasOwned) {
                    outer.or();
                }
                outer.and(led -> led.in(Task::getCourseId, ledCourseIds)
                        .and(visibility -> addTaskClassVisibility(visibility, ledClassIds)));
            }
        });
    }

    private void applyTeacherExamScope(LambdaQueryWrapper<ExamPaper> wrapper, Long userId) {
        Set<Long> ownedCourseIds = ownedCourseIds(userId);
        Set<Long> ledCourseIds = new LinkedHashSet<>();
        Set<Long> ledClassIds = new LinkedHashSet<>();
        collectLedClasses(userId, ledCourseIds, ledClassIds);
        if (ownedCourseIds.isEmpty() && ledCourseIds.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(outer -> {
            boolean hasOwned = !ownedCourseIds.isEmpty();
            if (hasOwned) {
                outer.in(ExamPaper::getCourseId, ownedCourseIds);
            }
            if (!ledCourseIds.isEmpty()) {
                if (hasOwned) {
                    outer.or();
                }
                outer.and(led -> led.in(ExamPaper::getCourseId, ledCourseIds)
                        .and(visibility -> addExamClassVisibility(visibility, ledClassIds)));
            }
        });
    }

    private void addTaskClassVisibility(LambdaQueryWrapper<Task> wrapper, Collection<Long> classIds) {
        List<Long> targetedIds = taskTargetIds(classIds);
        wrapper.and(scope -> {
            scope.eq(Task::getVisibility, ActivityVisibility.COURSE);
            if (!targetedIds.isEmpty()) {
                scope.or().in(Task::getId, targetedIds);
            }
        });
    }

    private void addExamClassVisibility(LambdaQueryWrapper<ExamPaper> wrapper, Collection<Long> classIds) {
        List<Long> targetedIds = examPaperTargetIds(classIds);
        wrapper.and(scope -> {
            scope.eq(ExamPaper::getVisibility, ActivityVisibility.COURSE);
            if (!targetedIds.isEmpty()) {
                scope.or().in(ExamPaper::getId, targetedIds);
            }
        });
    }

    private void assertClassViewer(Long userId, boolean student, TeachingClass teachingClass) {
        if (student) {
            CourseEnrollment enrollment = approvedEnrollment(userId, teachingClass.getCourseId(),
                    teachingClass.getId());
            if (enrollment == null) {
                throw new BusinessException(ApiCode.FORBIDDEN, "你不在该教学班，无权按此班筛选");
            }
            return;
        }
        Course course = requireCourse(teachingClass.getCourseId());
        if (Objects.equals(userId, course.getTeacherId())
                || Objects.equals(userId, teachingClass.getLeadTeacherId())) {
            return;
        }
        throw new BusinessException(ApiCode.FORBIDDEN, "无权按其他教师的教学班筛选");
    }

    private CourseEnrollment approvedEnrollment(Long studentId, Long courseId, Long teachingClassId) {
        List<CourseEnrollment> rows = approvedEnrollments(studentId, courseId, teachingClassId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<CourseEnrollment> approvedEnrollments(Long studentId, Long teachingClassId) {
        return approvedEnrollments(studentId, null, teachingClassId);
    }

    private List<CourseEnrollment> approvedEnrollments(Long studentId, Long courseId, Long teachingClassId) {
        return enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(courseId != null, CourseEnrollment::getCourseId, courseId)
                .eq(teachingClassId != null, CourseEnrollment::getTeachingClassId, teachingClassId)
                .eq(CourseEnrollment::getApprovalStatus, "APPROVED")
                .eq(CourseEnrollment::getIsActive, true));
    }

    private Set<Long> ownedCourseIds(Long userId) {
        return courseService.list(new LambdaQueryWrapper<Course>()
                        .eq(Course::getTeacherId, userId))
                .stream()
                .map(Course::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void collectLedClasses(Long userId, Set<Long> ledCourseIds, Set<Long> ledClassIds) {
        for (TeachingClass teachingClass : teachingClassMapper.selectList(
                new LambdaQueryWrapper<TeachingClass>().eq(TeachingClass::getLeadTeacherId, userId))) {
            if (teachingClass.getCourseId() != null) {
                ledCourseIds.add(teachingClass.getCourseId());
            }
            if (teachingClass.getId() != null) {
                ledClassIds.add(teachingClass.getId());
            }
        }
    }

    private Set<Long> ledClassIds(Long userId, Long courseId) {
        return teachingClassMapper.selectList(new LambdaQueryWrapper<TeachingClass>()
                        .eq(TeachingClass::getLeadTeacherId, userId)
                        .eq(TeachingClass::getCourseId, courseId))
                .stream()
                .map(TeachingClass::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean taskTargeted(Long taskId, Long teachingClassId) {
        return teachingClassId != null && taskTeachingClassMapper.selectCount(
                new LambdaQueryWrapper<TaskTeachingClass>()
                        .eq(TaskTeachingClass::getTaskId, taskId)
                        .eq(TaskTeachingClass::getTeachingClassId, teachingClassId)) > 0;
    }

    private boolean taskTargetsAny(Long taskId, Collection<Long> classIds) {
        if (classIds.isEmpty()) {
            return false;
        }
        return taskTeachingClassMapper.selectCount(new LambdaQueryWrapper<TaskTeachingClass>()
                .eq(TaskTeachingClass::getTaskId, taskId)
                .in(TaskTeachingClass::getTeachingClassId, classIds)) > 0;
    }

    private boolean examPaperTargeted(Long examPaperId, Long teachingClassId) {
        return teachingClassId != null && examTeachingClassMapper.selectCount(
                new LambdaQueryWrapper<ExamTeachingClass>()
                        .eq(ExamTeachingClass::getExamPaperId, examPaperId)
                        .eq(ExamTeachingClass::getTeachingClassId, teachingClassId)) > 0;
    }

    private boolean examPaperTargetsAny(Long examPaperId, Collection<Long> classIds) {
        if (classIds.isEmpty()) {
            return false;
        }
        return examTeachingClassMapper.selectCount(new LambdaQueryWrapper<ExamTeachingClass>()
                .eq(ExamTeachingClass::getExamPaperId, examPaperId)
                .in(ExamTeachingClass::getTeachingClassId, classIds)) > 0;
    }

    private List<Long> taskTargetIds(Collection<Long> classIds) {
        if (classIds.isEmpty()) {
            return List.of();
        }
        return taskTeachingClassMapper.selectList(new LambdaQueryWrapper<TaskTeachingClass>()
                        .in(TaskTeachingClass::getTeachingClassId, classIds))
                .stream()
                .map(TaskTeachingClass::getTaskId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Long> examPaperTargetIds(Collection<Long> classIds) {
        if (classIds.isEmpty()) {
            return List.of();
        }
        return examTeachingClassMapper.selectList(new LambdaQueryWrapper<ExamTeachingClass>()
                        .in(ExamTeachingClass::getTeachingClassId, classIds))
                .stream()
                .map(ExamTeachingClass::getExamPaperId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private TeachingClass requireClass(Long teachingClassId) {
        TeachingClass teachingClass = teachingClassId == null ? null : teachingClassMapper.selectById(teachingClassId);
        if (teachingClass == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "教学班不存在：" + teachingClassId);
        }
        return teachingClass;
    }

    private Course requireCourse(Long courseId) {
        Course course = courseId == null ? null : courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在：" + courseId);
        }
        return course;
    }

    private static boolean isClassesScope(String visibility) {
        return ActivityVisibility.CLASSES.equals(visibility);
    }

    private boolean isAdmin(Authentication auth) {
        return hasRole(auth, RoleConstants.ADMIN);
    }

    private boolean isStudent(Authentication auth) {
        return hasRole(auth, RoleConstants.STUDENT);
    }

    private boolean hasRole(Authentication auth, String role) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + role)::equals);
    }

    private Long principalId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }
}
