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
 * 开课任务 → 教学班占位生成器（V28 三层课程模型）。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>课程定义来源的新开课任务（{@code definitionId != null}）：只有审批通过且课程启用时，
 *       才按 {@code planned_sections} 生成 DRAFT 教学班；审批前不产生任何班级。</li>
 *   <li>历史兼容路径（无 definitionId）：保持 1 个默认教学班，状态跟随课程，
 *       避免存量单测与旧数据在迁移后突然失去可报名入口。</li>
 *   <li>教师已自建教学班时不制造第二个；只对单班默认班做名称/编号/状态同步。</li>
 * </ul>
 */
@Component
public class CourseDefaultTeachingClassSync {

    private static final Logger log = LoggerFactory.getLogger(CourseDefaultTeachingClassSync.class);

    /** 与 V14 回填逐字一致的默认班名称后缀（含前导空格）。 */
    static final String DEFAULT_NAME_SUFFIX = " 默认教学班";

    /** 与 V14 回填逐字一致的默认班编号后缀。 */
    static final String DEFAULT_CODE_SUFFIX = "-DEFAULT";

    private static final String COURSE_STATUS_ACTIVE = "ACTIVE";
    private static final String APPROVAL_APPROVED = "APPROVED";
    private static final String APPROVAL_PENDING = "PENDING";
    private static final String APPROVAL_REJECTED = "REJECTED";
    private static final String CLASS_STATUS_ACTIVE = "ACTIVE";
    private static final String CLASS_STATUS_DRAFT = "DRAFT";
    private static final int MAX_PLANNED_SECTIONS = 50;

    private final TeachingClassMapper teachingClassMapper;

    public CourseDefaultTeachingClassSync(TeachingClassMapper teachingClassMapper) {
        this.teachingClassMapper = teachingClassMapper;
    }

    public void sync(Course course) {
        if (course == null || course.getId() == null
                || course.getTeacherId() == null || course.getName() == null) {
            return;
        }
        boolean definitionModel = course.getDefinitionId() != null;
        String approval = course.getApprovalStatus();
        if (definitionModel && approval != null
                && (APPROVAL_PENDING.equalsIgnoreCase(approval)
                || APPROVAL_REJECTED.equalsIgnoreCase(approval))) {
            return;
        }

        List<TeachingClass> existing = teachingClassMapper.selectList(
                new LambdaQueryWrapper<TeachingClass>()
                        .eq(TeachingClass::getCourseId, course.getId())
                        .orderByAsc(TeachingClass::getId));

        int planned = definitionModel ? plannedSections(course) : 1;
        if (existing.isEmpty()) {
            for (int index = 1; index <= planned; index++) {
                teachingClassMapper.insert(newClass(course, index, planned, definitionModel));
            }
            if (planned > 0) {
                log.debug("[org] 生成教学班占位：courseId={}, count={}, status={}",
                        course.getId(), planned, definitionModel ? CLASS_STATUS_DRAFT : statusOf(course, false));
            }
            return;
        }

        if (!definitionModel) {
            syncLegacyDefault(course, existing);
            return;
        }

        // 计划班数调大时补齐缺口；不自动删除已有班级，避免带走成员与学习记录。
        if (existing.size() < planned) {
            for (int index = existing.size() + 1; index <= planned; index++) {
                teachingClassMapper.insert(newClass(course, index, planned, true));
            }
        }
        if (!COURSE_STATUS_ACTIVE.equalsIgnoreCase(course.getStatus())) {
            for (TeachingClass item : existing) {
                if (!CLASS_STATUS_DRAFT.equals(item.getStatus())) {
                    item.setStatus(CLASS_STATUS_DRAFT);
                    item.setUpdatedAt(LocalDateTime.now());
                    teachingClassMapper.updateById(item);
                }
            }
        }
    }

    public void close(Course course) {
        if (course == null || course.getId() == null) {
            return;
        }
        List<TeachingClass> existing = teachingClassMapper.selectList(
                new LambdaQueryWrapper<TeachingClass>()
                        .eq(TeachingClass::getCourseId, course.getId()));
        for (TeachingClass item : existing) {
            if (!"CLOSED".equals(item.getStatus())) {
                item.setStatus("CLOSED");
                item.setUpdatedAt(LocalDateTime.now());
                teachingClassMapper.updateById(item);
            }
        }
    }

    private TeachingClass newClass(Course course, int index, int planned, boolean definitionModel) {
        TeachingClass created = new TeachingClass();
        created.setCourseId(course.getId());
        if (planned <= 1) {
            created.setName(course.getName() + DEFAULT_NAME_SUFFIX);
            created.setCode(course.getCode() == null ? null : course.getCode() + DEFAULT_CODE_SUFFIX);
        } else {
            created.setName(course.getName() + " 教学班 " + index);
            created.setCode(course.getCode() == null ? null
                    : course.getCode() + "-" + String.format("%02d", index));
        }
        created.setLeadTeacherId(course.getTeacherId());
        created.setCapacity(course.getMaxStudents());
        created.setStatus(definitionModel ? CLASS_STATUS_DRAFT : statusOf(course, false));
        created.setCreatedBy(course.getTeacherId());
        LocalDateTime now = LocalDateTime.now();
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        return created;
    }

    private void syncLegacyDefault(Course course, List<TeachingClass> existing) {
        String derivedName = course.getName() + DEFAULT_NAME_SUFFIX;
        String derivedCode = course.getCode() == null ? null : course.getCode() + DEFAULT_CODE_SUFFIX;
        String desiredStatus = statusOf(course, false);
        TeachingClass def = existing.stream()
                .filter(t -> derivedName.equals(t.getName())
                        || (t.getCode() != null && t.getCode().endsWith(DEFAULT_CODE_SUFFIX)))
                .findFirst()
                .orElse(null);
        if (def == null) {
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
    }

    private static String statusOf(Course course, boolean draft) {
        if (draft) {
            return CLASS_STATUS_DRAFT;
        }
        return COURSE_STATUS_ACTIVE.equalsIgnoreCase(course.getStatus())
                ? CLASS_STATUS_ACTIVE : CLASS_STATUS_DRAFT;
    }

    private static int plannedSections(Course course) {
        Integer planned = course.getPlannedSections();
        if (planned == null || planned < 1) {
            return 1;
        }
        return Math.min(planned, MAX_PLANNED_SECTIONS);
    }
}
