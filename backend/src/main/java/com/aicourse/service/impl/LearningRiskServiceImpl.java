package com.aicourse.service.impl;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.LearningRecord;
import com.aicourse.entity.LoginLog;
import com.aicourse.entity.User;
import com.aicourse.mapper.LearningRecordMapper;
import com.aicourse.mapper.LoginLogMapper;
import com.aicourse.service.LearningRiskService;
import com.aicourse.service.UserService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.vo.CourseRiskPredictionVO;
import com.aicourse.vo.StudentRiskVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学情风险预测服务实现（P2 预测性学情预警）。
 * <p>信号与权重（风险分上限 100）：
 * <ul>
 *   <li>成绩 &lt; 60（百分制）：+35</li>
 *   <li>进度 &lt; 30%：+25；进度 30%-50%：+15；进度缺失：+15</li>
 *   <li>近 7 天无登录：+15；近 14 天登录 &lt; 3 次：+10</li>
 *   <li>近 14 天课程内学习行为 = 0：+15；&lt; 2 次：+8</li>
 *   <li>累计学习时长 &lt; 60 分钟：+10</li>
 * </ul>
 * 等级阈值：HIGH ≥ 60，MID ≥ 35，LOW &lt; 35。
 * 批量拉取登录/学习行为后内存聚合，避免按学生循环查库的 N+1。</p>
 */
@Service
@Slf4j
public class LearningRiskServiceImpl implements LearningRiskService {

    /** 活跃度统计窗口（天） */
    private static final int WINDOW_DAYS = 14;
    /** 近期活跃判定窗口（天） */
    private static final int RECENT_DAYS = 7;
    /** 及格线（百分制） */
    private static final BigDecimal PASS_SCORE = BigDecimal.valueOf(60);
    /** 学习时长偏短阈值（分钟） */
    private static final int SHORT_STUDY_MINUTES = 60;

    private final CourseEnrollmentService enrollmentService;
    private final LoginLogMapper loginLogMapper;
    private final LearningRecordMapper learningRecordMapper;
    private final UserService userService;

    public LearningRiskServiceImpl(CourseEnrollmentService enrollmentService,
                                   LoginLogMapper loginLogMapper,
                                   LearningRecordMapper learningRecordMapper,
                                   UserService userService) {
        this.enrollmentService = enrollmentService;
        this.loginLogMapper = loginLogMapper;
        this.learningRecordMapper = learningRecordMapper;
        this.userService = userService;
    }

    @Override
    @Transactional(readOnly = true)
    public CourseRiskPredictionVO predictCourseRisk(Long courseId) {
        CourseRiskPredictionVO vo = new CourseRiskPredictionVO();
        vo.setGeneratedAt(LocalDateTime.now());
        List<StudentRiskVO> students = new ArrayList<>();

        if (courseId == null) {
            vo.setStudents(students);
            return vo;
        }
        vo.setCourseId(courseId);

        List<CourseEnrollment> enrollments = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getCourseId, courseId)
                        .eq(CourseEnrollment::getIsActive, true));
        if (enrollments.isEmpty()) {
            vo.setStudents(students);
            return vo;
        }

        Set<Long> studentIds = enrollments.stream()
                .map(CourseEnrollment::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = studentIds.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(studentIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusDays(WINDOW_DAYS);
        LocalDateTime recentStart = now.minusDays(RECENT_DAYS);

        // 批量拉取窗口内登录（SUCCESS）与课程内学习行为，按学生分组，避免 N+1
        Map<Long, List<LoginLog>> logsByUser = Collections.emptyMap();
        Map<Long, List<LearningRecord>> recsByUser = Collections.emptyMap();
        if (!studentIds.isEmpty()) {
            List<LoginLog> allLogs = loginLogMapper.selectList(
                    new LambdaQueryWrapper<LoginLog>()
                            .eq(LoginLog::getLoginStatus, "SUCCESS")
                            .in(LoginLog::getUserId, studentIds)
                            .ge(LoginLog::getCreatedAt, windowStart));
            logsByUser = allLogs.stream().collect(Collectors.groupingBy(LoginLog::getUserId));

            List<LearningRecord> allRecs = learningRecordMapper.selectList(
                    new LambdaQueryWrapper<LearningRecord>()
                            .eq(LearningRecord::getCourseId, courseId)
                            .in(LearningRecord::getUserId, studentIds)
                            .ge(LearningRecord::getCreatedAt, windowStart));
            recsByUser = allRecs.stream().collect(Collectors.groupingBy(LearningRecord::getUserId));
        }

        Map<Long, CourseEnrollment> enrollMap = enrollments.stream()
                .filter(e -> e.getStudentId() != null)
                .collect(Collectors.toMap(CourseEnrollment::getStudentId, e -> e, (a, b) -> a));

        for (Long sid : studentIds) {
            CourseEnrollment en = enrollMap.get(sid);
            if (en == null) continue;

            StudentRiskVO s = new StudentRiskVO();
            s.setStudentId(sid);
            User u = userMap.get(sid);
            s.setStudentName(u != null ? u.getFullName() : null);
            s.setStudentNo(u != null ? u.getStudentId() : null);
            s.setProgress(en.getProgress());
            BigDecimal scoreSnap = scoreSnapshot(en);
            s.setScoreSnapshot(scoreSnap);

            List<String> reasons = new ArrayList<>();
            int score = 0;

            // 1. 成绩风险
            if (scoreSnap != null && scoreSnap.compareTo(PASS_SCORE) < 0) {
                score += 35;
                reasons.add("综合成绩低于及格线(60分)");
            }

            // 2. 进度风险
            BigDecimal prog = en.getProgress();
            if (prog == null) {
                score += 15;
                reasons.add("学习进度数据缺失");
            } else {
                double p = prog.doubleValue();
                if (p < 0.3) {
                    score += 25;
                    reasons.add("课程学习进度不足30%");
                } else if (p < 0.5) {
                    score += 15;
                    reasons.add("课程学习进度不足50%");
                }
            }

            // 3. 登录活跃度
            List<LoginLog> logs = logsByUser.getOrDefault(sid, Collections.emptyList());
            boolean loginRecent = logs.stream()
                    .anyMatch(l -> l.getCreatedAt() != null && l.getCreatedAt().isAfter(recentStart));
            if (!loginRecent) {
                score += 15;
                reasons.add("近7天无登录记录");
            } else if (logs.size() < 3) {
                score += 10;
                reasons.add("近14天登录次数少于3次");
            }
            LocalDateTime lastLogin = logs.stream()
                    .map(LoginLog::getCreatedAt)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            // 4. 课程内学习行为
            List<LearningRecord> recs = recsByUser.getOrDefault(sid, Collections.emptyList());
            if (recs.isEmpty()) {
                score += 15;
                reasons.add("近14天无课程学习行为");
            } else if (recs.size() < 2) {
                score += 8;
                reasons.add("近14天课程学习行为偏少(<2次)");
            }
            LocalDateTime lastRec = recs.stream()
                    .map(LearningRecord::getCreatedAt)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            // 5. 累计学习时长
            Integer studyTime = en.getTotalStudyTime();
            if (studyTime != null && studyTime < SHORT_STUDY_MINUTES) {
                score += 10;
                reasons.add("课程累计学习时长偏短(<60分钟)");
            }

            // 最后活跃时间 = max(登录, 学习行为, 选课记录更新时间)
            LocalDateTime lastActive = maxOf(lastLogin, lastRec, en.getUpdatedAt());
            s.setLastActiveAt(lastActive);

            score = Math.min(score, 100);
            s.setRiskScore(score);
            s.setRiskTier(score >= 60 ? "HIGH" : score >= 35 ? "MID" : "LOW");
            s.setReasons(reasons);
            students.add(s);
        }

        int high = 0, mid = 0, low = 0;
        for (StudentRiskVO s : students) {
            if ("HIGH".equals(s.getRiskTier())) high++;
            else if ("MID".equals(s.getRiskTier())) mid++;
            else low++;
        }
        students.sort((a, b) -> Integer.compare(b.getRiskScore(), a.getRiskScore()));

        vo.setStudents(students);
        vo.setHighCount(high);
        vo.setMidCount(mid);
        vo.setLowCount(low);
        vo.setTotal(students.size());
        return vo;
    }

    /** 成绩快照：优先综合成绩；缺失时取考试/作业成绩的最小值；均无则返回 null。 */
    private BigDecimal scoreSnapshot(CourseEnrollment en) {
        if (en.getFinalScore() != null) return en.getFinalScore();
        List<BigDecimal> vals = new ArrayList<>(2);
        if (en.getExamScore() != null) vals.add(en.getExamScore());
        if (en.getAssignmentScore() != null) vals.add(en.getAssignmentScore());
        if (vals.isEmpty()) return null;
        return vals.stream().min(BigDecimal::compareTo).orElse(null);
    }

    /** 取非空时间的最大值。 */
    private LocalDateTime maxOf(LocalDateTime a, LocalDateTime b, LocalDateTime c) {
        LocalDateTime max = a;
        if (b != null && (max == null || b.isAfter(max))) max = b;
        if (c != null && (max == null || c.isAfter(max))) max = c;
        return max;
    }
}
