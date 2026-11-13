package com.aicourse.service.component.org;

import com.aicourse.entity.Course;
import com.aicourse.entity.TeachingClass;
import com.aicourse.mapper.TeachingClassMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程默认教学班同步器（B-12／P-08 WP4 判据 9A 的运行态延伸）。
 * <p>
 * V14 只给<b>存量</b>课程回填「恰 1 个默认教学班」，新建课程因此长期没有可报名的教学班，
 * 学生选课恒被 {@code CourseEnrollmentServiceImpl#resolveTeachingClass} 判 409
 * （2026-11-06 真栈 e2e 实测：教师建课→审批通过后选课 409「课程暂无启用的教学班」）。
 * 本类把 9A 从一次性回填升级为<b>持续不变量</b>：课程落库后即保证恰有 1 个默认班，
 * 且其状态与课程状态同步（ACTIVE→ACTIVE，其余→DRAFT，与 V14 的 CASE 口径一致）。
 * <p>
 * 三条边界：①主讲为空时不建（对齐 V14 的 {@code WHERE teacher_id IS NOT NULL}）；
 * ②课程已有教学班且识别不到默认班时不制造第二个（教师自建班优先）；
 * ③只同步状态与派生名/码，<b>不动容量</b>——容量是教师可编辑的业务值。
 */
@Component
public class CourseDefaultTeachingClassSync {

    private static final Logger log = LoggerFactory.getLogger(CourseDefaultTeachingClassSync.class);

    /** 与 V14 回填逐字一致的默认班名称后缀（含前导空格）。 */
    static final String DEFAULT_NAME_SUFFIX = " 默认教学班";

    /** 与 V14 回填逐字一致的默认班编号后缀。 */
    static final String DEFAULT_CODE_SUFFIX = "-DEFAULT";

    private static final String COURSE_STATUS_ACTIVE = "ACTIVE";
    private static final String CLASS_STATUS_ACTIVE = "ACTIVE";
    private static final String CLASS_STATUS_DRAFT = "DRAFT";

    private final TeachingClassMapper teachingClassMapper;

    public CourseDefaultTeachingClassSync(TeachingClassMapper teachingClassMapper) {
        this.teachingClassMapper = teachingClassMapper;
    }

    /**
     * 保证课程落库后恰有 1 个默认教学班，状态跟随课程。
     *
     * @param course 已落库的课程；id／teacherId／name 任一为空即跳过——name 为空说明这是一次
     *               局部更新（只带变更字段），此时派生默认班会把名称写成 {@code "null 默认教学班"}
     */
    public void sync(Course course) {
        if (course == null || course.getId() == null
                || course.getTeacherId() == null || course.getName() == null) {
            return;
        }
        String derivedName = course.getName() + DEFAULT_NAME_SUFFIX;
        String derivedCode = course.getCode() == null ? null : course.getCode() + DEFAULT_CODE_SUFFIX;
        String desiredStatus = COURSE_STATUS_ACTIVE.equalsIgnoreCase(course.getStatus())
                ? CLASS_STATUS_ACTIVE : CLASS_STATUS_DRAFT;

        List<TeachingClass> existing = teachingClassMapper.selectList(
                new LambdaQueryWrapper<TeachingClass>()
                        .eq(TeachingClass::getCourseId, course.getId())
                        .orderByAsc(TeachingClass::getId));
        TeachingClass def = existing.stream()
                .filter(t -> derivedName.equals(t.getName())
                        || (t.getCode() != null && t.getCode().endsWith(DEFAULT_CODE_SUFFIX)))
                .findFirst()
                .orElse(null);

        if (def == null) {
            if (!existing.isEmpty()) {
                return;
            }
            TeachingClass created = new TeachingClass();
            created.setCourseId(course.getId());
            created.setName(derivedName);
            created.setCode(derivedCode);
            created.setLeadTeacherId(course.getTeacherId());
            created.setCapacity(course.getMaxStudents());
            created.setStatus(desiredStatus);
            created.setCreatedBy(course.getTeacherId());
            LocalDateTime now = LocalDateTime.now();
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            teachingClassMapper.insert(created);
            log.debug("[org] 新建默认教学班：courseId={}, status={}", course.getId(), desiredStatus);
            return;
        }

        boolean nameDrift = !derivedName.equals(def.getName());
        boolean codeDrift = derivedCode != null && !derivedCode.equals(def.getCode());
        boolean statusDrift = !desiredStatus.equals(def.getStatus());
        if (!nameDrift && !codeDrift && !statusDrift) {
            return;
        }
        def.setName(derivedName);
        if (derivedCode != null) {
            def.setCode(derivedCode);
        }
        def.setStatus(desiredStatus);
        def.setUpdatedAt(LocalDateTime.now());
        teachingClassMapper.updateById(def);
        log.debug("[org] 默认教学班随课程同步：courseId={}, name/code/status 漂移={} / {} / {}",
                course.getId(), nameDrift, codeDrift, statusDrift);
    }
}
