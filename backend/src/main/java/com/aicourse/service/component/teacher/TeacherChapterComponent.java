package com.aicourse.service.component.teacher;

import com.aicourse.constant.SubmissionStatus;
import com.aicourse.entity.Classroom;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.User;
import com.aicourse.service.ClassroomService;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.UserService;
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
 * 教师章节/班级协作者：完整章节树（提交率聚合）与课程关联班级列表。
 * <p>由 {@code TeacherCourseServiceImpl} 门面委托调用。</p>
 */
@Component
public class TeacherChapterComponent {

    private final CourseChapterService chapterService;
    private final CourseEnrollmentService enrollmentService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final UserService userService;
    private final ClassroomService classroomService;
    private final TeacherCourseComponent support;

    public TeacherChapterComponent(CourseChapterService chapterService,
                                   CourseEnrollmentService enrollmentService,
                                   TaskService taskService,
                                   SubmissionService submissionService,
                                   UserService userService,
                                   ClassroomService classroomService,
                                   TeacherCourseComponent support) {
        this.chapterService = chapterService;
        this.enrollmentService = enrollmentService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.userService = userService;
        this.classroomService = classroomService;
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

    /** 课程关联班级列表（按班级聚合学生 + 待批改数） */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCourseClasses(Long courseId) {
        List<CourseEnrollment> enrollments = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));

        List<Long> studentIds = enrollments.stream()
                .map(CourseEnrollment::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Long> studentClassMap = new HashMap<>();
        if (!studentIds.isEmpty()) {
            List<User> users = userService.list(new LambdaQueryWrapper<User>()
                    .in(User::getId, studentIds)
                    .select(User::getId, User::getClassId));
            for (User u : users) {
                if (u.getClassId() != null) {
                    studentClassMap.put(u.getId(), u.getClassId());
                }
            }
        }

        Map<Long, List<Long>> classStudentIds = new LinkedHashMap<>();
        for (CourseEnrollment e : enrollments) {
            Long clsId = studentClassMap.get(e.getStudentId());
            if (clsId != null) {
                classStudentIds.computeIfAbsent(clsId, k -> new ArrayList<>()).add(e.getStudentId());
            }
        }

        Map<Long, Classroom> classroomMap = new HashMap<>();
        if (!classStudentIds.isEmpty()) {
            List<Classroom> classrooms = classroomService.listByIds(new ArrayList<>(classStudentIds.keySet()));
            for (Classroom c : classrooms) {
                if (c != null) {
                    classroomMap.put(c.getId(), c);
                }
            }
        }

        List<Long> taskIds = taskService.list(new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId))
                .stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());

        Map<Long, Long> pendingByClass = new HashMap<>();
        if (!taskIds.isEmpty()) {
            for (Map.Entry<Long, List<Long>> entry : classStudentIds.entrySet()) {
                Long pending = submissionService.count(new LambdaQueryWrapper<Submission>()
                        .in(Submission::getTaskId, taskIds)
                        .in(Submission::getStudentId, entry.getValue())
                        .eq(Submission::getStatus, SubmissionStatus.SUBMITTED));
                pendingByClass.put(entry.getKey(), pending);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : classStudentIds.entrySet()) {
            Classroom c = classroomMap.get(entry.getKey());
            if (c == null) {
                continue;
            }
            List<Long> students = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", String.valueOf(c.getId()));
            item.put("name", c.getName());
            item.put("code", c.getCode());
            item.put("students", students.size());
            item.put("pending", pendingByClass.getOrDefault(entry.getKey(), 0L));
            item.put("schedule", c.getDescription() != null ? c.getDescription() : "");
            result.add(item);
        }

        result.sort(Comparator.comparing(m -> {
            String code = (String) m.get("code");
            return code != null && !code.isBlank() ? code : String.valueOf(m.get("name"));
        }));
        return result;
    }
}
