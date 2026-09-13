package com.aicourse.service.component.student;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.TaskCompletion;
import com.aicourse.constant.SubmissionStatus;
import com.aicourse.mapper.TaskCompletionMapper;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseService;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学生章节学习中心协作者：章节任务点树（闯关解锁/完成态批量预取）、课程进度摘要。
 * <p>由 {@code StudentLearningServiceImpl} 门面委托调用。</p>
 */
@Component
public class StudentLearningCenterComponent {

    private static final Comparator<CourseChapter> CHAPTER_ORDER = (a, b) -> {
        int oa = a.getOrderIndex() != null ? a.getOrderIndex() : Integer.MAX_VALUE;
        int ob = b.getOrderIndex() != null ? b.getOrderIndex() : Integer.MAX_VALUE;
        return Integer.compare(oa, ob);
    };

    private final CourseService courseService;
    private final CourseChapterService courseChapterService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final TaskCompletionMapper taskCompletionMapper;
    private final StudentTaskCompletionComponent taskSupport;

    public StudentLearningCenterComponent(CourseService courseService,
                                          CourseChapterService courseChapterService,
                                          TaskService taskService,
                                          SubmissionService submissionService,
                                          TaskCompletionMapper taskCompletionMapper,
                                          StudentTaskCompletionComponent taskSupport) {
        this.courseService = courseService;
        this.courseChapterService = courseChapterService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.taskCompletionMapper = taskCompletionMapper;
        this.taskSupport = taskSupport;
    }

    /** 章节任务点学习中心（章节树 + 任务完成态 + 解锁状态） */
    @Transactional(readOnly = true)
    public Map<String, Object> getCourseLearningCenter(Long userId, Long courseId) {
        taskSupport.assertEnrolled(userId, courseId);
        Course course = courseService.getById(courseId);

        List<CourseChapter> allChapters = courseChapterService.list(
                new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, courseId));
        List<Task> allTasks = taskService.list(
                new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId).isNotNull(Task::getChapterId));

        Map<Long, List<Task>> tasksByChapter = allTasks.stream()
                .filter(t -> t.getChapterId() != null)
                .collect(Collectors.groupingBy(Task::getChapterId));
        // 批量预取完成态（消除逐条查库 N+1）
        Set<Long> taskIds = allTasks.stream().map(Task::getId).collect(Collectors.toSet());
        Set<Long> submittedTaskIds = taskIds.isEmpty() ? Collections.emptySet()
                : submissionService.list(new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getStudentId, userId)
                        .in(Submission::getTaskId, taskIds)
                        .in(Submission::getStatus, SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED, SubmissionStatus.RETURNED))
                .stream().map(Submission::getTaskId).collect(Collectors.toSet());
        Set<Long> completedTcTaskIds = taskIds.isEmpty() ? Collections.emptySet()
                : taskCompletionMapper.selectList(new LambdaQueryWrapper<TaskCompletion>()
                        .eq(TaskCompletion::getStudentId, userId)
                        .in(TaskCompletion::getTaskId, taskIds)
                        .eq(TaskCompletion::getCompleted, true))
                .stream().map(TaskCompletion::getTaskId).collect(Collectors.toSet());
        Map<Long, Boolean> completedMap = new HashMap<>();
        for (Task t : allTasks) {
            boolean done = submittedTaskIds.contains(t.getId());
            if (!done && completedTcTaskIds.contains(t.getId())) {
                done = true;
            }
            completedMap.put(t.getId(), done);
        }

        Map<Long, List<CourseChapter>> childrenMap = allChapters.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() != 0)
                .collect(Collectors.groupingBy(CourseChapter::getParentId));
        List<CourseChapter> top = allChapters.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .sorted(CHAPTER_ORDER)
                .collect(Collectors.toList());

        boolean[] prevAllDone = {true};
        List<Map<String, Object>> tree = new ArrayList<>();
        int[] counters = {0, 0};
        buildLevel(top, childrenMap, tasksByChapter, completedMap, prevAllDone, tree, counters);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", courseId);
        result.put("courseName", course != null ? course.getName() : null);
        result.put("chapters", tree);
        result.put("totalTasks", counters[0]);
        result.put("completedTasks", counters[1]);
        result.put("completionPercent", counters[0] == 0 ? 100 : Math.round(counters[1] * 100.0 / counters[0]));
        return result;
    }

    /** 课程进度摘要（扁平化章节统计） */
    @Transactional(readOnly = true)
    public Map<String, Object> getCourseProgressSummary(Long userId, Long courseId) {
        taskSupport.assertEnrolled(userId, courseId);
        Map<String, Object> center = getCourseLearningCenter(userId, courseId);
        List<Map<String, Object>> chapters = (List<Map<String, Object>>) center.get("chapters");
        List<Map<String, Object>> summary = new ArrayList<>();
        if (chapters != null) {
            collectSummary(chapters, summary);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", courseId);
        result.put("chapters", summary);
        result.put("totalTasks", center.get("totalTasks"));
        result.put("completedTasks", center.get("completedTasks"));
        result.put("completionPercent", center.get("completionPercent"));
        return result;
    }

    private void buildLevel(List<CourseChapter> level, Map<Long, List<CourseChapter>> childrenMap,
                            Map<Long, List<Task>> tasksByChapter, Map<Long, Boolean> completedMap,
                            boolean[] prevAllDone, List<Map<String, Object>> outTree, int[] counters) {
        for (CourseChapter ch : level) {
            boolean chapterUnlocked = prevAllDone[0];
            List<Task> chTasks = tasksByChapter.getOrDefault(ch.getId(), Collections.emptyList());
            chTasks.sort(Comparator.comparing(Task::getId));
            List<Map<String, Object>> taskMaps = new ArrayList<>();
            boolean prevTaskDone = true;
            int done = 0;
            for (int i = 0; i < chTasks.size(); i++) {
                Task t = chTasks.get(i);
                boolean c = completedMap.getOrDefault(t.getId(), false);
                if (c) {
                    done++;
                }
                boolean unlocked = chapterUnlocked && (i == 0 || prevTaskDone);
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("id", t.getId());
                tm.put("title", t.getTitle());
                tm.put("taskType", t.getTaskType());
                tm.put("status", t.getStatus());
                tm.put("isPublished", t.getIsPublished());
                tm.put("dueDate", t.getDueDate() != null ? t.getDueDate().toString() : null);
                tm.put("completed", c);
                tm.put("unlocked", unlocked);
                taskMaps.add(tm);
                prevTaskDone = c;
                counters[0]++;
                if (c) {
                    counters[1]++;
                }
            }
            boolean allCompleted = chTasks.isEmpty() || done == chTasks.size();
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", ch.getId());
            node.put("title", ch.getTitle());
            node.put("orderIndex", ch.getOrderIndex());
            node.put("isPublished", ch.getIsPublished());
            node.put("hasContent", ch.getContent() != null && !ch.getContent().isEmpty());
            node.put("unlocked", chapterUnlocked);
            node.put("totalTasks", chTasks.size());
            node.put("completedTasks", done);
            node.put("allCompleted", allCompleted);
            node.put("tasks", taskMaps);
            List<Map<String, Object>> childTree = new ArrayList<>();
            List<CourseChapter> children = childrenMap.getOrDefault(ch.getId(), Collections.emptyList());
            children.sort(CHAPTER_ORDER);
            if (!children.isEmpty()) {
                // 修复 P1-16：子章节解锁必须继承「祖链锁定」——父章节本身未解锁（chapterUnlocked=false）
                // 时，即使其全部任务完成，子章节也不应显示为解锁
                buildLevel(children, childrenMap, tasksByChapter, completedMap,
                        new boolean[]{chapterUnlocked && allCompleted}, childTree, counters);
            }
            node.put("children", childTree);
            outTree.add(node);
            prevAllDone[0] = allCompleted;
        }
    }

    @SuppressWarnings("unchecked")
    private void collectSummary(List<Map<String, Object>> chapters, List<Map<String, Object>> out) {
        for (Map<String, Object> ch : chapters) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chapterId", ch.get("id"));
            m.put("title", ch.get("title"));
            m.put("totalTasks", ch.get("totalTasks"));
            m.put("completedTasks", ch.get("completedTasks"));
            m.put("percent", ((Number) ch.get("totalTasks")).intValue() == 0 ? 100
                    : Math.round(((Number) ch.get("completedTasks")).intValue() * 100.0 / ((Number) ch.get("totalTasks")).intValue()));
            out.add(m);
            List<Map<String, Object>> children = (List<Map<String, Object>>) ch.get("children");
            if (children != null && !children.isEmpty()) {
                collectSummary(children, out);
            }
        }
    }
}
