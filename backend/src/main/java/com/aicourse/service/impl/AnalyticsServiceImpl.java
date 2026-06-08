package com.aicourse.service.impl;

import com.aicourse.entity.*;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据统计分析服务实现类
 * 提供平台总览、用户个人数据、学习趋势、成绩分布、课程分析等统计功能
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final UserService userService;
    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final LearningRecordService learningRecordService;

    public AnalyticsServiceImpl(
            UserService userService,
            CourseService courseService,
            CourseEnrollmentService enrollmentService,
            TaskService taskService,
            SubmissionService submissionService,
            LearningRecordService learningRecordService
    ) {
        this.userService = userService;
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.learningRecordService = learningRecordService;
    }



    @Override
    public Map<String, Object> getDashboard() {
        Map<String, Object> dashboard = new HashMap<>(6);
        dashboard.put("totalUsers", userService.count());
        dashboard.put("activeUsers", userService.count(new LambdaQueryWrapper<User>().eq(User::getIsActive, Boolean.TRUE)));
        dashboard.put("totalCourses", courseService.count());
        dashboard.put("activeCourses", courseService.count(new LambdaQueryWrapper<Course>().eq(Course::getIsActive, Boolean.TRUE)));
        dashboard.put("totalEnrollments", enrollmentService.count());
        dashboard.put("totalTasks", taskService.count());
        return dashboard;
    }

    @Override
    public Map<String, Object> getUserDashboard(Long userId) {
        Map<String, Object> userData = new HashMap<>(3);

        // 已选课程数量
        long enrolledCount = enrollmentService.count(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getStudentId, userId)
        );
        userData.put("enrolledCourses", enrolledCount);

        // 平均学习进度
        List<CourseEnrollment> enrollmentList = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getStudentId, userId)
        );
        double avgProgress = enrollmentList.stream()
                .mapToDouble(e -> e.getProgress() != null ? e.getProgress().doubleValue() : 0D)
                .average()
                .orElse(0D);
        userData.put("averageProgress", Math.round(avgProgress * 100.0) / 100.0);

        // 累计学习时长
        int totalStudyTime = learningRecordService.list(
                new LambdaQueryWrapper<LearningRecord>().eq(LearningRecord::getUserId, userId)
        ).stream().mapToInt(LearningRecord::getDuration).sum();
        userData.put("totalStudyTime", totalStudyTime);

        return userData;
    }

    @Override
    public List<Map<String, Object>> getLearningTrends(Long userId) {
        List<LearningRecord> recordList = learningRecordService.list(
                new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getUserId, userId)
                        .orderByAsc(LearningRecord::getCreatedAt)
        );

        // 按日期分组并累加时长，有序Map保证日期顺序
        Map<String, Integer> dateDurationMap = recordList.stream()
                .collect(Collectors.groupingBy(
                        record -> record.getCreatedAt().toLocalDate().toString(),
                        LinkedHashMap::new,
                        Collectors.summingInt(LearningRecord::getDuration)
                ));

        return dateDurationMap.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>(2);
                    item.put("date", entry.getKey());
                    item.put("duration", entry.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getGradeDistribution(Long courseId) {
        List<CourseEnrollment> enrollmentList = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId)
        );

        Map<String, Object> gradeData = new HashMap<>(3);
        gradeData.put("total", enrollmentList.size());

        // 平均分
        double avgScore = enrollmentList.stream()
                .filter(e -> e.getFinalScore() != null)
                .mapToDouble(e -> e.getFinalScore().doubleValue())
                .average()
                .orElse(0D);
        gradeData.put("average", avgScore);

        // 及格人数
        long passCount = enrollmentList.stream()
                .filter(e -> e.getFinalScore() != null && e.getFinalScore().doubleValue() >= 60)
                .count();
        gradeData.put("passed", passCount);

        return gradeData;
    }

    @Override
    public Map<String, Object> getCourseAnalysis(Long courseId) {
        Course course = courseService.getById(courseId);
        if (course == null) {
            return Collections.singletonMap("error", "课程不存在");
        }

        Map<String, Object> courseData = new HashMap<>(3);
        courseData.put("courseInfo", course);
        courseData.put("enrollmentCount", enrollmentService.count(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId)
        ));
        courseData.put("taskCount", taskService.count(
                new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId)
        ));
        return courseData;
    }
}