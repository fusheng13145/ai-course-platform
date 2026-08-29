package com.aicourse.service.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.constant.ExamSessionStatus;

import com.aicourse.entity.*;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 课程/考试视角数据分析协作者
 * <p>承接原 {@link AnalyticsServiceImpl} 中课程与考试维度的统计职责：成绩分布（基础/直方图）、
 * 课程综合分析、热门课程排行、考试统计详情。</p>
 */
@Component
public class CourseAnalyticsComponent {

    private static final Logger log = LoggerFactory.getLogger(CourseAnalyticsComponent.class);

    private final CourseEnrollmentService enrollmentService;
    private final CourseService courseService;
    private final TaskService taskService;
    private final ExamPaperService examPaperService;
    private final ExamSessionService examSessionService;

    public CourseAnalyticsComponent(
            CourseEnrollmentService enrollmentService,
            CourseService courseService,
            TaskService taskService,
            ExamPaperService examPaperService,
            ExamSessionService examSessionService
    ) {
        this.enrollmentService = enrollmentService;
        this.courseService = courseService;
        this.taskService = taskService;
        this.examPaperService = examPaperService;
        this.examSessionService = examSessionService;
    }


    /** 查询课程分析 */
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
        gradeData.put("average", Math.round(avgScore * 100.0) / 100.0);

        // 及格人数
        long passCount = enrollmentList.stream()
                .filter(e -> e.getFinalScore() != null && e.getFinalScore().doubleValue() >= 60)
                .count();
        gradeData.put("passed", passCount);

        return gradeData;
    }

    /**
     * 获取课程成绩分布直方图（增强版）
     * 按分数段（0-59, 60-69, 70-79, 80-89, 90-100）统计人数分布
     */
    public Map<String, Object> getGradeDistributionHistogram(Long courseId) {
        List<CourseEnrollment> enrollmentList = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getCourseId, courseId)
                        .isNotNull(CourseEnrollment::getFinalScore)
        );

        Map<String, Object> histogram = new HashMap<>(8);
        histogram.put("total", enrollmentList.size());

        // 计算平均分、最高分、最低分
        DoubleSummaryStatistics scoreStats = enrollmentList.stream()
                .filter(e -> e.getFinalScore() != null)
                .mapToDouble(e -> e.getFinalScore().doubleValue())
                .summaryStatistics();

        histogram.put("average", Math.round(scoreStats.getAverage() * 100.0) / 100.0);
        histogram.put("maxScore", Math.round(scoreStats.getMax() * 100.0) / 100.0);
        histogram.put("minScore", Math.round(scoreStats.getMin() * 100.0) / 100.0);

        // 按分数段统计人数
        Map<String, Long> distribution = enrollmentList.stream()
                .filter(e -> e.getFinalScore() != null)
                .collect(Collectors.groupingBy(e -> {
                    double score = e.getFinalScore().doubleValue();
                    if (score < 60) return "0-59（不及格）";
                    if (score < 70) return "60-69（及格）";
                    if (score < 80) return "70-79（中等）";
                    if (score < 90) return "80-89（良好）";
                    return "90-100（优秀）";
                }, Collectors.counting()));

        histogram.put("distribution", distribution);

        // 及格率和优秀率
        long totalWithScore = enrollmentList.size();
        long passCount = enrollmentList.stream()
                .filter(e -> e.getFinalScore() != null && e.getFinalScore().doubleValue() >= 60)
                .count();
        long excellentCount = enrollmentList.stream()
                .filter(e -> e.getFinalScore() != null && e.getFinalScore().doubleValue() >= 90)
                .count();

        histogram.put("passRate", totalWithScore > 0 ?
                Math.round(passCount * 100.0 / totalWithScore * 100.0) / 100.0 : 0);
        histogram.put("excellentRate", totalWithScore > 0 ?
                Math.round(excellentCount * 100.0 / totalWithScore * 100.0) / 100.0 : 0);

        return histogram;
    }


    /** 查询课程分析 */
    public Map<String, Object> getCourseAnalysis(Long courseId) {
        Course course = courseService.getById(courseId);
        if (course == null) {
            return Collections.singletonMap("error", "课程不存在");
        }

        Map<String, Object> courseData = new HashMap<>(6);
        courseData.put("courseInfo", course);
        courseData.put("enrollmentCount", enrollmentService.count(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId)
        ));
        courseData.put("taskCount", taskService.count(
                new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId)
        ));

        // 计算课程平均进度和平均成绩
        List<CourseEnrollment> enrollments = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId)
        );
        double avgProgress = enrollments.stream()
                .mapToDouble(e -> e.getProgress() != null ? e.getProgress().doubleValue() : 0D)
                .average()
                .orElse(0D);
        courseData.put("averageProgress", Math.round(avgProgress * 100.0) / 100.0);

        double avgScore = enrollments.stream()
                .filter(e -> e.getFinalScore() != null)
                .mapToDouble(e -> e.getFinalScore().doubleValue())
                .average()
                .orElse(0D);
        courseData.put("averageScore", Math.round(avgScore * 100.0) / 100.0);

        return courseData;
    }

    /**
     * 获取热门课程排行
     * 按选课人数降序排列，返回前20门课程
     */
    public List<Map<String, Object>> getCourseRanking() {
        // 获取所有活跃课程
        List<Course> courses = courseService.list(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getIsActive, Boolean.TRUE)
                        .orderByDesc(Course::getCreatedAt)
        );

        // 统计每门课程的选课人数并排序
        return courses.stream()
                .map(course -> {
                    long enrollmentCount = enrollmentService.count(
                            new LambdaQueryWrapper<CourseEnrollment>()
                                    .eq(CourseEnrollment::getCourseId, course.getId())
                    );
                    Map<String, Object> item = new HashMap<>(6);
                    item.put("courseId", course.getId());
                    item.put("courseName", course.getName());
                    item.put("courseCode", course.getCode());
                    item.put("teacherId", course.getTeacherId());
                    item.put("enrollmentCount", enrollmentCount);
                    item.put("category", course.getCategory());
                    item.put("difficulty", course.getDifficulty());
                    return item;
                })
                .sorted((a, b) -> ((Long) b.get("enrollmentCount")).compareTo((Long) a.get("enrollmentCount")))
                .limit(20)
                .collect(Collectors.toList());
    }

    /**
     * 获取考试统计详情
     * 包含参考人数、平均分、最高/最低分、及格率、分数分布等
     */
    public Map<String, Object> getExamStatistics(Long examPaperId) {
        ExamPaper examPaper = examPaperService.getById(examPaperId);
        if (examPaper == null) {
            return Collections.singletonMap("error", "试卷不存在");
        }

        Map<String, Object> statistics = new HashMap<>(12);
        statistics.put("examPaperId", examPaperId);
        statistics.put("examTitle", examPaper.getTitle());
        statistics.put("totalScore", examPaper.getTotalScore());
        statistics.put("questionCount", examPaper.getQuestionCount());
        statistics.put("duration", examPaper.getDuration());

        // 获取该试卷的所有考试会话（已提交或已阅卷）
        List<ExamSession> sessions = examSessionService.list(
                new LambdaQueryWrapper<ExamSession>()
                        .eq(ExamSession::getExamPaperId, examPaperId)
                        .in(ExamSession::getStatus, ExamSessionStatus.SUBMITTED, ExamSessionStatus.GRADED)
        );

        statistics.put("totalParticipants", sessions.size());

        // 统计分数数据
        if (!sessions.isEmpty()) {
            DoubleSummaryStatistics scoreStats = sessions.stream()
                    .filter(s -> s.getTotalScore() != null)
                    .mapToDouble(s -> s.getTotalScore().doubleValue())
                    .summaryStatistics();

            statistics.put("averageScore", Math.round(scoreStats.getAverage() * 100.0) / 100.0);
            statistics.put("maxScore", Math.round(scoreStats.getMax() * 100.0) / 100.0);
            statistics.put("minScore", Math.round(scoreStats.getMin() * 100.0) / 100.0);

            // 及格率（假设60%为及格线）
            BigDecimal passLine = examPaper.getTotalScore() != null ?
                    examPaper.getTotalScore().multiply(BigDecimal.valueOf(0.6)) : BigDecimal.valueOf(60);
            long passCount = sessions.stream()
                    .filter(s -> s.getTotalScore() != null && s.getTotalScore().compareTo(passLine) >= 0)
                    .count();
            statistics.put("passRate", Math.round(passCount * 100.0 / sessions.size() * 100.0) / 100.0);

            // 优秀率（85%以上）
            BigDecimal excellentLine = examPaper.getTotalScore() != null ?
                    examPaper.getTotalScore().multiply(BigDecimal.valueOf(0.85)) : BigDecimal.valueOf(85);
            long excellentCount = sessions.stream()
                    .filter(s -> s.getTotalScore() != null && s.getTotalScore().compareTo(excellentLine) >= 0)
                    .count();
            statistics.put("excellentRate", Math.round(excellentCount * 100.0 / sessions.size() * 100.0) / 100.0);

            // 分数段分布
            Map<String, Long> scoreDistribution = sessions.stream()
                    .filter(s -> s.getTotalScore() != null)
                    .collect(Collectors.groupingBy(s -> {
                        double percentage = s.getPercentage() != null ?
                                s.getPercentage().doubleValue() :
                                (s.getTotalScore().doubleValue() /
                                        (examPaper.getTotalScore() != null ? examPaper.getTotalScore().doubleValue() : 100) * 100);
                        if (percentage < 60) return "0-59（不及格）";
                        if (percentage < 70) return "60-69（及格）";
                        if (percentage < 80) return "70-79（中等）";
                        if (percentage < 90) return "80-89（良好）";
                        return "90-100（优秀）";
                    }, Collectors.counting()));
            statistics.put("scoreDistribution", scoreDistribution);

            // 平均用时（分钟）
            double avgTimeSpent = sessions.stream()
                    .filter(s -> s.getTimeSpent() != null)
                    .mapToInt(ExamSession::getTimeSpent)
                    .average()
                    .orElse(0);
            statistics.put("avgTimeSpentMinutes", Math.round(avgTimeSpent));

        } else {
            statistics.put("averageScore", 0);
            statistics.put("maxScore", 0);
            statistics.put("minScore", 0);
            statistics.put("passRate", 0);
            statistics.put("excellentRate", 0);
            statistics.put("scoreDistribution", Collections.emptyMap());
            statistics.put("avgTimeSpentMinutes", 0);
        }

        return statistics;
    }
}
