package com.aicourse.service.impl;

import com.aicourse.entity.*;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {
    private final UserService userService;
    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final LearningRecordService learningRecordService;

    public AnalyticsServiceImpl(UserService userService, CourseService courseService,
                                CourseEnrollmentService enrollmentService, TaskService taskService,
                                SubmissionService submissionService, LearningRecordService learningRecordService) {
        this.userService = userService;
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.learningRecordService = learningRecordService;
    }

    @Override
    public Map<String, Object> getDashboard() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalUsers", userService.count());
        data.put("activeUsers", userService.count(new LambdaQueryWrapper<User>().eq(User::getIsActive, true)));
        data.put("totalCourses", courseService.count());
        data.put("activeCourses", courseService.count(new LambdaQueryWrapper<Course>().eq(Course::getIsActive, true)));
        data.put("totalEnrollments", enrollmentService.count());
        data.put("totalTasks", taskService.count());
        return data;
    }

    @Override
    public Map<String, Object> getUserDashboard(Long userId) {
        Map<String, Object> data = new HashMap<>();
        data.put("enrolledCourses", enrollmentService.count(
            new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getStudentId, userId)));
        List<CourseEnrollment> enrollments = enrollmentService.list(
            new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getStudentId, userId));
        double avgProgress = enrollments.stream()
            .mapToDouble(e -> e.getProgress() != null ? e.getProgress().doubleValue() : 0)
            .average().orElse(0);
        data.put("averageProgress", Math.round(avgProgress * 100.0) / 100.0);
        data.put("totalStudyTime", learningRecordService.list(
            new LambdaQueryWrapper<LearningRecord>().eq(LearningRecord::getUserId, userId))
            .stream().mapToInt(LearningRecord::getDuration).sum());
        return data;
    }

    @Override
    public List<Map<String, Object>> getLearningTrends(Long userId) {
        List<LearningRecord> records = learningRecordService.list(
            new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .orderByAsc(LearningRecord::getCreatedAt));
        return records.stream().collect(Collectors.groupingBy(
            r -> r.getCreatedAt().toLocalDate().toString(),
            LinkedHashMap::new,
            Collectors.summingInt(LearningRecord::getDuration)))
            .entrySet().stream()
            .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("date", e.getKey()); m.put("duration", e.getValue()); return m; })
            .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getGradeDistribution(Long courseId) {
        List<CourseEnrollment> enrollments = enrollmentService.list(
            new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));
        Map<String, Object> data = new HashMap<>();
        data.put("total", enrollments.size());
        data.put("average", enrollments.stream()
            .filter(e -> e.getFinalScore() != null)
            .mapToDouble(e -> e.getFinalScore().doubleValue())
            .average().orElse(0));
        data.put("passed", enrollments.stream()
            .filter(e -> e.getFinalScore() != null && e.getFinalScore().doubleValue() >= 60)
            .count());
        return data;
    }

    @Override
    public Map<String, Object> getCourseAnalysis(Long courseId) {
        Course course = courseService.getById(courseId);
        if (course == null) return Map.of("error", "课程不存在");
        Map<String, Object> data = new HashMap<>();
        data.put("courseInfo", course);
        data.put("enrollmentCount", enrollmentService.count(
            new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId)));
        data.put("taskCount", taskService.count(
            new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId)));
        return data;
    }
}
