package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.security.TeachingClassAccessControl;
import com.aicourse.service.CourseService;
import com.aicourse.service.TeachingClassService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link TeachingClassService} 实现（V14 / B-12-P-08 WP4）。
 *
 * <p>关键口径：</p>
 * <ul>
 *     <li>建班人必须是课程负责人（{@code courses.teacher_id}），管理员不代办；</li>
 *     <li>课程未通过审批（{@code approval_status<>APPROVED}）或已停用（{@code status<>ACTIVE}）时，
 *         新建教学班一律 DRAFT，不接受客户端指定的 ACTIVE；</li>
 *     <li>主讲只能指派本院教师（2A），跨院 403；</li>
 *     <li>名单/成绩读取仅限该教学班主讲，同课程其他教学班主讲 403（管理员治理只读放行）。</li>
 * </ul>
 */
@Service
public class TeachingClassServiceImpl extends ServiceImpl<TeachingClassMapper, TeachingClass>
        implements TeachingClassService {

    private static final String COURSE_STATUS_ACTIVE = "ACTIVE";
    private static final String APPROVAL_APPROVED = "APPROVED";
    private static final String CLASS_STATUS_ACTIVE = "ACTIVE";
    private static final String CLASS_STATUS_DRAFT = "DRAFT";
    private static final String CLASS_STATUS_CLOSED = "CLOSED";
    private static final int NAME_MAX_LENGTH = 120;
    private static final int CODE_MAX_LENGTH = 50;

    private final CourseService courseService;
    private final UserService userService;
    private final CourseEnrollmentMapper courseEnrollmentMapper;
    private final TeachingClassAccessControl accessControl;

    public TeachingClassServiceImpl(CourseService courseService,
                                    UserService userService,
                                    CourseEnrollmentMapper courseEnrollmentMapper,
                                    TeachingClassAccessControl accessControl) {
        this.courseService = courseService;
        this.userService = userService;
        this.courseEnrollmentMapper = courseEnrollmentMapper;
        this.accessControl = accessControl;
    }

    @Override
    public List<TeachingClass> listByCourse(Long courseId) {
        if (courseId == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "课程ID不能为空");
        }
        return this.list(new LambdaQueryWrapper<TeachingClass>()
                .eq(TeachingClass::getCourseId, courseId)
                .orderByAsc(TeachingClass::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeachingClass create(Long operatorId, Long courseId, String name, String code,
                                Long leadTeacherId, Integer capacity) {
        Course course = accessControl.assertCourseOwner(courseId, operatorId);

        String className = normalizeName(name, course);
        Long leadId = leadTeacherId == null ? course.getTeacherId() : leadTeacherId;
        accessControl.assertLeadTeacherInCourseCollege(course, leadId);
        validateCapacity(capacity);

        long exists = this.count(new LambdaQueryWrapper<TeachingClass>()
                .eq(TeachingClass::getCourseId, courseId)
                .eq(TeachingClass::getName, className));
        if (exists > 0) {
            throw new BusinessException(ApiCode.CONFLICT, "该课程下已存在同名教学班：" + className);
        }

        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setCourseId(courseId);
        teachingClass.setName(className);
        teachingClass.setCode(normalizeCode(code));
        teachingClass.setLeadTeacherId(leadId);
        teachingClass.setCapacity(capacity);
        // 只有「课程已通过审批且处于启用」才允许新教学班直接 ACTIVE；其余一律 DRAFT。
        teachingClass.setStatus(isCourseEnabled(course) ? CLASS_STATUS_ACTIVE : CLASS_STATUS_DRAFT);
        teachingClass.setCreatedBy(operatorId);
        teachingClass.setCreatedAt(LocalDateTime.now());
        teachingClass.setUpdatedAt(LocalDateTime.now());
        try {
            this.save(teachingClass);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ApiCode.CONFLICT, "该课程下已存在同名教学班：" + className);
        }
        return teachingClass;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeachingClass assignLeadTeacher(Long teachingClassId, Long teacherId, Long operatorId) {
        TeachingClass teachingClass = accessControl.requireTeachingClass(teachingClassId);
        Course course = accessControl.assertCourseOwner(teachingClass.getCourseId(), operatorId);
        User teacher = accessControl.assertLeadTeacherInCourseCollege(course, teacherId);
        teachingClass.setLeadTeacherId(teacher.getId());
        teachingClass.setUpdatedAt(LocalDateTime.now());
        this.updateById(teachingClass);
        return teachingClass;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeachingClass updateBasic(Long teachingClassId, Long operatorId, String name, String code,
                                     Integer capacity, String status) {
        TeachingClass teachingClass = accessControl.requireTeachingClass(teachingClassId);
        Course course = accessControl.assertCourseOwner(teachingClass.getCourseId(), operatorId);

        if (name != null) {
            String className = normalizeName(name, course);
            long exists = this.count(new LambdaQueryWrapper<TeachingClass>()
                    .eq(TeachingClass::getCourseId, teachingClass.getCourseId())
                    .eq(TeachingClass::getName, className)
                    .ne(TeachingClass::getId, teachingClassId));
            if (exists > 0) {
                throw new BusinessException(ApiCode.CONFLICT, "该课程下已存在同名教学班：" + className);
            }
            teachingClass.setName(className);
        }
        if (code != null) {
            teachingClass.setCode(normalizeCode(code));
        }
        if (capacity != null) {
            validateCapacity(capacity);
            teachingClass.setCapacity(capacity);
        }
        if (status != null) {
            String normalized = status.trim().toUpperCase();
            if (!CLASS_STATUS_DRAFT.equals(normalized)
                    && !CLASS_STATUS_ACTIVE.equals(normalized)
                    && !CLASS_STATUS_CLOSED.equals(normalized)) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "教学班状态非法：" + status);
            }
            if (CLASS_STATUS_ACTIVE.equals(normalized) && !isCourseEnabled(course)) {
                throw new BusinessException(ApiCode.CONFLICT, "课程未通过审批或已停用，教学班不能启用");
            }
            teachingClass.setStatus(normalized);
        }
        teachingClass.setUpdatedAt(LocalDateTime.now());
        this.updateById(teachingClass);
        return teachingClass;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long teachingClassId, Long operatorId) {
        TeachingClass teachingClass = accessControl.requireTeachingClass(teachingClassId);
        accessControl.assertCourseOwner(teachingClass.getCourseId(), operatorId);
        long members = courseEnrollmentMapper.selectCount(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getTeachingClassId, teachingClassId));
        if (members > 0) {
            throw new BusinessException(ApiCode.CONFLICT, "教学班仍有学生名单，不能删除");
        }
        this.removeById(teachingClassId);
    }

    @Override
    public List<CourseEnrollment> listMembers(Long teachingClassId, Long operatorId) {
        accessControl.assertTeachingClassLead(teachingClassId, operatorId);
        return courseEnrollmentMapper.selectList(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getTeachingClassId, teachingClassId)
                .orderByAsc(CourseEnrollment::getId));
    }

    private static boolean isCourseEnabled(Course course) {
        return COURSE_STATUS_ACTIVE.equals(course.getStatus())
                && APPROVAL_APPROVED.equals(course.getApprovalStatus());
    }

    private static String normalizeName(String name, Course course) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            trimmed = course.getName() + " 默认教学班";
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "教学班名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        return trimmed;
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.length() > CODE_MAX_LENGTH) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "教学班编号不能超过 " + CODE_MAX_LENGTH + " 个字符");
        }
        return trimmed;
    }

    private static void validateCapacity(Integer capacity) {
        if (capacity != null && capacity <= 0) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "教学班容量必须大于 0");
        }
    }
}
