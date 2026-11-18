package com.aicourse.service.component.teacher;

import com.aicourse.constant.SubmissionStatus;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.TeachingClass;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 教师章节/班级协作者：完整章节树（提交率聚合）与课程教学班列表。
 * <p>由 {@code TeacherCourseServiceImpl} 门面委托调用。</p>
 */
@Component
public class TeacherChapterComponent {

    private final CourseChapterService chapterService;
    private final CourseEnrollmentService enrollmentService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final TeachingClassMapper teachingClassMapper;
    private final TeacherCourseComponent support;

    public TeacherChapterComponent(CourseChapterService chapterService,
                                   CourseEnrollmentService enrollmentService,
                                   TaskService taskService,
                                   SubmissionService submissionService,
                                   TeachingClassMapper teachingClassMapper,
                                   TeacherCourseComponent support) {
        this.chapterService = chapterService;
        this.enrollmentService = enrollmentService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.teachingClassMapper = teachingClassMapper;
        this.support = support;
    }

    /** 完整章节树（含任务点提交率聚合） */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFullChapterTree(Long courseId) {
        List<CourseChapter> all = chapterService.list(new LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getCourseId, courseId).orderByAsc(CourseChapter::getOrderIndex));

        long studentCount = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));

        List<Task> courseTasks = taskService.list(new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId));
        Map<Long, List<Task>> tasksByChapter = courseTasks.stream()
                .filter(t -> t.getChapterId() != null)
                .collect(Collectors.groupingBy(Task::getChapterId));

        final Map<Long, Long> submissionCountByTask;
        if (!courseTasks.isEmpty()) {
            List<Long> taskIds = courseTasks.stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
            if (!taskIds.isEmpty()) {
                List<Submission> subs = submissionService.list(new LambdaQueryWrapper<Submission>()
                        .in(Submission::getTaskId, taskIds)
                        .in(Submission::getStatus, SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED));
                submissionCountByTask = subs.stream()
                        .collect(Collectors.groupingBy(Submission::getTaskId, Collectors.counting()));
            } else {
                submissionCountByTask = Collections.emptyMap();
            }
        } else {
            submissionCountByTask = Collections.emptyMap();
        }

        Map<Long, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (CourseChapter c : all) {
            List<Task> chTasks = tasksByChapter.getOrDefault(c.getId(), Collections.emptyList());
            long taskCount = chTasks.size();
            long submitted = chTasks.stream()
                    .mapToLong(t -> submissionCountByTask.getOrDefault(t.getId(), 0L)).sum();
            long expected = taskCount * studentCount;
            double completionRate = expected > 0 ? support.round2(submitted * 100.0 / expected) : 0;

            List<Map<String, Object>> taskPoints = chTasks.stream().map(t -> {
                Map<String, Object> tp = new LinkedHashMap<>(6);
                tp.put("id", t.getId());
                tp.put("title", t.getTitle());
                tp.put("taskType", t.getTaskType());
                tp.put("isPublished", t.getIsPublished());
                long sc = submissionCountByTask.getOrDefault(t.getId(), 0L);
                tp.put("submittedCount", sc);
                tp.put("completionRate", studentCount > 0 ? support.round2(sc * 100.0 / studentCount) : 0);
                return tp;
            }).collect(Collectors.toList());

            Map<String, Object> node = new LinkedHashMap<>(10);
            node.put("id", c.getId());
            node.put("title", c.getTitle());
            node.put("content", c.getContent());
            node.put("orderIndex", c.getOrderIndex());
            node.put("duration", c.getDuration());
            node.put("isPublished", c.getIsPublished());
            node.put("parentId", c.getParentId());
            node.put("taskCount", taskCount);
            node.put("submittedCount", submitted);
            node.put("completionRate", completionRate);
            node.put("taskPoints", taskPoints);
            node.put("children", new ArrayList<Map<String, Object>>());
            nodeMap.put(c.getId(), node);
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (CourseChapter c : all) {
            Map<String, Object> node = nodeMap.get(c.getId());
            if (c.getParentId() != null && nodeMap.containsKey(c.getParentId())) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get(c.getParentId()).get("children");
                children.add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    /** 课程教学班列表（按教学班聚合有效学生 + 待批改数）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCourseClasses(Long courseId) {
        List<TeachingClass> classes = teachingClassMapper.selectList(new LambdaQueryWrapper<TeachingClass>()
                .eq(TeachingClass::getCourseId, courseId)
                .orderByAsc(TeachingClass::getCode)
                .orderByAsc(TeachingClass::getId));

        List<CourseEnrollment> enrollments = enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getIsActive, true)
                .eq(CourseEnrollment::getApprovalStatus, "APPROVED"));

        Map<Long, List<Long>> classStudentIds = new LinkedHashMap<>();
        for (CourseEnrollment enrollment : enrollments) {
            Long classId = enrollment.getTeachingClassId();
            if (classId != null) {
                classStudentIds.computeIfAbsent(classId, key -> new ArrayList<>())
                        .add(enrollment.getStudentId());
            }
        }

        List<Long> taskIds = taskService.list(new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId))
                .stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, Long> pendingByStudent = new HashMap<>();
        if (!taskIds.isEmpty()) {
            submissionService.list(new LambdaQueryWrapper<Submission>()
                            .in(Submission::getTaskId, taskIds)
                            .eq(Submission::getStatus, SubmissionStatus.SUBMITTED))
                    .forEach(submission -> pendingByStudent.merge(submission.getStudentId(), 1L, Long::sum));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (TeachingClass teachingClass : classes) {
            List<Long> students = classStudentIds.getOrDefault(teachingClass.getId(), List.of());
            long pending = students.stream()
                    .mapToLong(studentId -> pendingByStudent.getOrDefault(studentId, 0L))
                    .sum();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", String.valueOf(teachingClass.getId()));
            item.put("name", teachingClass.getName());
            item.put("code", teachingClass.getCode());
            item.put("students", students.size());
            item.put("pending", pending);
            item.put("schedule", "");
            item.put("status", teachingClass.getStatus());
            result.add(item);
        }
        result.sort(Comparator.comparing(m -> {
            String code = (String) m.get("code");
            return code != null && !code.isBlank() ? code : String.valueOf(m.get("name"));
        }));
        return result;
    }
}
