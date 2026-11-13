package com.aicourse.service.component.student;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Task;
import com.aicourse.entity.TaskCompletion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.TaskCompletionMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.TaskService;
import com.aicourse.service.component.org.TeachingClassSnapshotResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * 学生任务点完成态支持协作者：选课校验、完成态查询/幂等 upsert（含并发冲突兜底）、
 * 课程进度刷新、客观题判定等——被驾驶舱/学习中心/任务组件共享。
 * <p>由 {@code StudentLearningServiceImpl} 门面下属组件委托调用。</p>
 */
@Component
public class StudentTaskCompletionComponent {

    private static final Logger log = LoggerFactory.getLogger(StudentTaskCompletionComponent.class);

    private final TaskService taskService;
    private final TaskCompletionMapper taskCompletionMapper;
    private final CourseEnrollmentService enrollmentService;
    private final TeachingClassSnapshotResolver teachingClassSnapshotResolver;

    public StudentTaskCompletionComponent(TaskService taskService,
                              TaskCompletionMapper taskCompletionMapper,
                              CourseEnrollmentService enrollmentService,
                              TeachingClassSnapshotResolver teachingClassSnapshotResolver) {
        this.taskService = taskService;
        this.taskCompletionMapper = taskCompletionMapper;
        this.enrollmentService = enrollmentService;
        this.teachingClassSnapshotResolver = teachingClassSnapshotResolver;
    }

    /** 自愈合建表：避免依赖脆弱的 DB 迁移；若表已存在则幂等跳过。 */
    public void ensureTable() {
        try {
            taskCompletionMapper.createTableIfNotExists();
            log.info("task_completions 表已就绪（自愈合建表）");
        } catch (Exception e) {
            log.warn("task_completions 自愈合建表失败（可忽略，若表已存在）: {}", e.getMessage());
        }
    }

    /** 校验学生已加入该课程（未加入抛业务异常） */
    public void assertEnrolled(Long userId, Long courseId) {
        CourseEnrollment en = enrollmentService.getOne(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, userId)
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getIsActive, true));
        if (en == null) {
            throw new BusinessException("未加入该课程");
        }
    }

    /** 查询单条完成态记录（无则 null） */
    public TaskCompletion getOneCompletion(Long taskId, Long userId) {
        return taskCompletionMapper.selectOne(new LambdaQueryWrapper<TaskCompletion>()
                .eq(TaskCompletion::getStudentId, userId)
                .eq(TaskCompletion::getTaskId, taskId));
    }

    /**
     * 幂等 upsert 完成态：插入或更新，捕获唯一索引并发冲突后转更新；
     * 任务由「未完成 → 完成」瞬间刷新课程进度（仅完成态翻转触发一次）。
     */
    public TaskCompletion upsertCompletion(Long userId, Long courseId, Long taskId,
                                           String completionType, Consumer<TaskCompletion> modifier) {
        TaskCompletion tc = getOneCompletion(taskId, userId);
        boolean isNew = false;
        boolean wasCompleted = tc != null && Boolean.TRUE.equals(tc.getCompleted());
        if (tc == null) {
            tc = new TaskCompletion();
            tc.setStudentId(userId);
            tc.setTaskId(taskId);
            tc.setCourseId(courseId);
            // WP7 快照：完成记录创建时即归属当时的教学班，转班后历史不漂移
            tc.setTeachingClassId(teachingClassSnapshotResolver.resolveTeachingClassId(userId, courseId));
            Task t = taskService.getById(taskId);
            if (t != null) {
                tc.setChapterId(t.getChapterId());
                tc.setTaskType(t.getTaskType());
            }
            isNew = true;
        }
        if (completionType != null) {
            tc.setCompletionType(completionType);
        }
        modifier.accept(tc);
        if (isNew) {
            try {
                taskCompletionMapper.insert(tc);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 修复 P1-17：视频心跳/测验提交并发插入冲突，捕获后转更新已有记录
                TaskCompletion existing = getOneCompletion(taskId, userId);
                if (existing == null) {
                    throw e;
                }
                tc.setId(existing.getId());
                taskCompletionMapper.updateById(tc);
            }
        } else {
            taskCompletionMapper.updateById(tc);
        }
        // 修复 P1-25：任务由「未完成 → 完成」的瞬间刷新课程进度（仅完成态翻转时触发一次）
        boolean justCompleted = Boolean.TRUE.equals(tc.getCompleted()) && !wasCompleted;
        if (justCompleted && courseId != null) {
            refreshEnrollmentProgress(userId, courseId);
        }
        return tc;
    }

    /**
     * 基于课程任务完成数聚合刷新 {@link CourseEnrollment#getProgress()}（0-100 百分制）。
     */
    private void refreshEnrollmentProgress(Long userId, Long courseId) {
        try {
            long total = taskService.count(new LambdaQueryWrapper<Task>()
                    .eq(Task::getCourseId, courseId));
            if (total <= 0) {
                return;
            }
            long done = taskCompletionMapper.selectCount(new LambdaQueryWrapper<TaskCompletion>()
                    .eq(TaskCompletion::getStudentId, userId)
                    .eq(TaskCompletion::getCourseId, courseId)
                    .eq(TaskCompletion::getCompleted, true));
            int pct = (int) Math.min(100, Math.round(done * 100.0 / total));
            enrollmentService.update(new LambdaUpdateWrapper<CourseEnrollment>()
                    .eq(CourseEnrollment::getStudentId, userId)
                    .eq(CourseEnrollment::getCourseId, courseId)
                    .eq(CourseEnrollment::getIsActive, true)
                    .set(CourseEnrollment::getProgress, BigDecimal.valueOf(pct)));
        } catch (Exception e) {
            log.warn("课程进度刷新失败: userId={}, courseId={}, err={}",
                    userId, courseId, e.getMessage());
        }
    }

    /** 答案归一化：分隔符切分 + 大写 + 排序（支持中英文逗号/分号） */
    public String normalize(String s) {
        if (s == null) {
            return "";
        }
        return Arrays.stream(s.split("[,，;；]"))
                .map(t -> t.trim().toUpperCase())
                .filter(t -> !t.isEmpty())
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    /**
     * 判断题目是否为可自动判定的客观题（兼容两套 type 命名约定）。
     */
    public boolean isObjectiveQuestion(String type) {
        if (type == null) {
            return false;
        }
        String t = type.trim().toUpperCase();
        return "SINGLE".equals(t) || "SINGLE_CHOICE".equals(t)
                || "MULTIPLE".equals(t) || "MULTIPLE_CHOICE".equals(t)
                || "JUDGE".equals(t) || "TRUE_FALSE".equals(t);
    }
}
