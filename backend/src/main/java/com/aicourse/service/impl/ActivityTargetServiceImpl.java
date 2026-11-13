package com.aicourse.service.impl;

import com.aicourse.common.ActivityVisibility;
import com.aicourse.common.ApiCode;
import com.aicourse.entity.ExamTeachingClass;
import com.aicourse.entity.TaskTeachingClass;
import com.aicourse.entity.TeachingClass;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamTeachingClassMapper;
import com.aicourse.mapper.TaskTeachingClassMapper;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.ActivityTargetService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * {@link ActivityTargetService} 实现（V15 / B-12-P-08 WP6）。
 *
 * <p>不变量：visibility=CLASSES 的活动至少存在 1 条目标行；visibility=COURSE 时不留残余目标行。
 * 目标教学班必须属于活动所属课程，避免跨课程污染可见范围。</p>
 */
@Service
public class ActivityTargetServiceImpl implements ActivityTargetService {

    private final TaskTeachingClassMapper taskTeachingClassMapper;
    private final ExamTeachingClassMapper examTeachingClassMapper;
    private final TeachingClassMapper teachingClassMapper;

    public ActivityTargetServiceImpl(TaskTeachingClassMapper taskTeachingClassMapper,
                                     ExamTeachingClassMapper examTeachingClassMapper,
                                     TeachingClassMapper teachingClassMapper) {
        this.taskTeachingClassMapper = taskTeachingClassMapper;
        this.examTeachingClassMapper = examTeachingClassMapper;
        this.teachingClassMapper = teachingClassMapper;
    }

    @Override
    public String normalizeAndValidate(Long activityId, Long courseId, String visibility, List<Long> classIds,
                                       boolean examPaper) {
        String normalized = ActivityVisibility.normalize(visibility);
        if (!ActivityVisibility.CLASSES.equals(normalized)) {
            return normalized;
        }
        List<Long> effective = classIds == null
                ? existingTargetIds(activityId, examPaper)
                : dedupe(classIds);
        if (effective.isEmpty()) {
            throw new BusinessException(ApiCode.BAD_REQUEST,
                    "可见范围为 CLASSES 时必须至少选择一个目标教学班");
        }
        List<TeachingClass> classes = teachingClassMapper.selectBatchIds(effective);
        if (classes.size() != effective.size()) {
            List<Long> missing = new ArrayList<>(effective);
            classes.forEach(c -> missing.remove(c.getId()));
            throw new BusinessException(ApiCode.BAD_REQUEST, "目标教学班不存在：" + missing);
        }
        for (TeachingClass teachingClass : classes) {
            if (!Objects.equals(teachingClass.getCourseId(), courseId)) {
                throw new BusinessException(ApiCode.BAD_REQUEST,
                        "目标教学班不属于该课程：" + teachingClass.getId());
            }
        }
        return normalized;
    }

    @Override
    @Transactional
    public void replaceTaskTargets(Long taskId, String visibility, List<Long> classIds) {
        String normalized = ActivityVisibility.normalize(visibility);
        List<Long> effective = ActivityVisibility.CLASSES.equals(normalized)
                ? (classIds == null ? taskTargetClassIds(taskId) : dedupe(classIds))
                : List.of();
        taskTeachingClassMapper.delete(new LambdaQueryWrapper<TaskTeachingClass>()
                .eq(TaskTeachingClass::getTaskId, taskId));
        for (Long classId : effective) {
            TaskTeachingClass row = new TaskTeachingClass();
            row.setTaskId(taskId);
            row.setTeachingClassId(classId);
            taskTeachingClassMapper.insert(row);
        }
    }

    @Override
    @Transactional
    public void replaceExamPaperTargets(Long examPaperId, String visibility, List<Long> classIds) {
        String normalized = ActivityVisibility.normalize(visibility);
        List<Long> effective = ActivityVisibility.CLASSES.equals(normalized)
                ? (classIds == null ? examPaperTargetClassIds(examPaperId) : dedupe(classIds))
                : List.of();
        examTeachingClassMapper.delete(new LambdaQueryWrapper<ExamTeachingClass>()
                .eq(ExamTeachingClass::getExamPaperId, examPaperId));
        for (Long classId : effective) {
            ExamTeachingClass row = new ExamTeachingClass();
            row.setExamPaperId(examPaperId);
            row.setTeachingClassId(classId);
            examTeachingClassMapper.insert(row);
        }
    }

    @Override
    public List<Long> taskTargetClassIds(Long taskId) {
        return taskTeachingClassMapper.selectList(new LambdaQueryWrapper<TaskTeachingClass>()
                        .eq(TaskTeachingClass::getTaskId, taskId))
                .stream()
                .map(TaskTeachingClass::getTeachingClassId)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    @Override
    public List<Long> examPaperTargetClassIds(Long examPaperId) {
        return examTeachingClassMapper.selectList(new LambdaQueryWrapper<ExamTeachingClass>()
                        .eq(ExamTeachingClass::getExamPaperId, examPaperId))
                .stream()
                .map(ExamTeachingClass::getTeachingClassId)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    private List<Long> existingTargetIds(Long activityId, boolean examPaper) {
        if (activityId == null) {
            return List.of();
        }
        return examPaper ? examPaperTargetClassIds(activityId) : taskTargetClassIds(activityId);
    }

    private static List<Long> dedupe(List<Long> ids) {
        return ids.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new));
    }
}
