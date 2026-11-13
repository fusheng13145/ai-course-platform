package com.aicourse.service.component.exam;

import com.aicourse.constant.ExamSessionStatus;
import com.aicourse.entity.Course;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.UserService;
import com.aicourse.vo.ActiveExamSessionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 考试会话查询组件：成绩报告 / 活跃会话 / 会话题目 / 我的会话与课程排名。
 * <p>拆分自原 ExamSessionServiceImpl（2026-08-29）。</p>
 */
@Slf4j
@Component
public class ExamSessionQueryComponent {

    private final ExamPaperService examPaperService;
    private final ExamQuestionService examQuestionService;
    private final ExamSessionMapper examSessionMapper;
    private final ObjectMapper objectMapper;
    private final CourseService courseService;
    private final UserService userService;

    public ExamSessionQueryComponent(ExamPaperService examPaperService,
                                     ExamQuestionService examQuestionService,
                                     ExamSessionMapper examSessionMapper,
                                     ObjectMapper objectMapper,
                                     CourseService courseService,
                                     UserService userService) {
        this.examPaperService = examPaperService;
        this.examQuestionService = examQuestionService;
        this.examSessionMapper = examSessionMapper;
        this.objectMapper = objectMapper;
        this.courseService = courseService;
        this.userService = userService;
    }

    /**
     * 生成成绩报告
     */
    public Map<String, Object> generateScoreReport(Long sessionId) {
        log.info("生成考试会话");

        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        ExamPaper examPaper = examPaperService.getById(session.getExamPaperId());

        Map<String, Object> report = new HashMap<>();
        report.put("sessionId", session.getId());
        report.put("examPaperId", session.getExamPaperId());
        report.put("examPaperTitle", examPaper != null ? examPaper.getTitle() : "未知试卷");
        report.put("studentId", session.getStudentId());
        report.put("status", session.getStatus());
        report.put("startTime", session.getStartTime());
        report.put("submitTime", session.getSubmitTime());
        report.put("timeSpent", session.getTimeSpent());
        report.put("attemptNumber", session.getAttemptNumber());
        report.put("totalScore", session.getTotalScore());
        report.put("percentage", session.getPercentage());

        // 解析每题得分详情
        if (session.getQuestionScores() != null) {
            Map<Long, BigDecimal> scores = parseScoresJson(session.getQuestionScores());
            report.put("questionDetails", scores);
        }

        // 等级评定
        if (session.getPercentage() != null) {
            double pct = session.getPercentage().doubleValue();
            String gradeLevel;
            if (pct >= 90) {
                gradeLevel = "优秀";
            } else if (pct >= 80) {
                gradeLevel = "良好";
            } else if (pct >= 70) {
                gradeLevel = "中等";
            } else if (pct >= 60) {
                gradeLevel = "及格";
            } else {
                gradeLevel = "不及格";
            }
            report.put("gradeLevel", gradeLevel);
        }

        return report;
    }

    /**
     * 获取正在进行的考试列表（B-13 ⑤：VO 化，批量补全试卷名/课程/学生姓名）。
     *
     * <p>participantCount 口径＝同一 examPaperId 当前处于 STARTED 的会话数（不是课程总人数）。
     * 试卷、课程或用户缺失时对应展示字段为 null，会话行本身仍返回，不因关联缺失丢行。</p>
     */
    public List<ActiveExamSessionVO> getActiveExams() {
        List<ExamSession> sessions = examSessionMapper.selectList(new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getStatus, ExamSessionStatus.STARTED)
                .orderByDesc(ExamSession::getStartTime));
        if (sessions.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> paperIds = new HashSet<>();
        Set<Long> studentIds = new HashSet<>();
        for (ExamSession s : sessions) {
            if (s.getExamPaperId() != null) paperIds.add(s.getExamPaperId());
            if (s.getStudentId() != null) studentIds.add(s.getStudentId());
        }

        Map<Long, ExamPaper> paperById = new HashMap<>();
        Set<Long> courseIds = new HashSet<>();
        if (!paperIds.isEmpty()) {
            for (ExamPaper p : examPaperService.listByIds(paperIds)) {
                paperById.put(p.getId(), p);
                if (p.getCourseId() != null) courseIds.add(p.getCourseId());
            }
        }
        Map<Long, Course> courseById = new HashMap<>();
        if (!courseIds.isEmpty()) {
            for (Course c : courseService.listByIds(courseIds)) {
                courseById.put(c.getId(), c);
            }
        }
        Map<Long, User> userById = new HashMap<>();
        if (!studentIds.isEmpty()) {
            for (User u : userService.listByIds(studentIds)) {
                userById.put(u.getId(), u);
            }
        }

        Map<Long, Integer> participantByPaper = new HashMap<>();
        for (ExamSession s : sessions) {
            if (s.getExamPaperId() != null) {
                participantByPaper.merge(s.getExamPaperId(), 1, Integer::sum);
            }
        }

        List<ActiveExamSessionVO> result = new ArrayList<>(sessions.size());
        for (ExamSession s : sessions) {
            ActiveExamSessionVO vo = new ActiveExamSessionVO();
            vo.setId(s.getId());
            vo.setExamPaperId(s.getExamPaperId());
            vo.setStudentId(s.getStudentId());
            vo.setStatus(s.getStatus());
            vo.setAttemptNumber(s.getAttemptNumber());
            vo.setStartTime(s.getStartTime());
            vo.setSubmitTime(s.getSubmitTime());
            vo.setTimeSpent(s.getTimeSpent());
            Integer participantCount = participantByPaper.get(s.getExamPaperId());
            vo.setParticipantCount(participantCount == null ? Integer.valueOf(0) : participantCount);

            ExamPaper paper = s.getExamPaperId() == null ? null : paperById.get(s.getExamPaperId());
            if (paper != null) {
                vo.setPaperTitle(paper.getTitle());
                vo.setCourseId(paper.getCourseId());
                Course course = paper.getCourseId() == null ? null : courseById.get(paper.getCourseId());
                if (course != null) {
                    vo.setCourseName(course.getName());
                }
            }
            User student = s.getStudentId() == null ? null : userById.get(s.getStudentId());
            if (student != null) {
                vo.setStudentName(student.getFullName() != null && !student.getFullName().isBlank()
                        ? student.getFullName() : student.getUsername());
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 获取会话题目列表（试卷下题目按顺序映射为 questionId/orderIndex/score）
     */
    public List<Map<String, Object>> getSessionQuestions(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) return Collections.emptyList();
        return examQuestionService.list(
            new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, session.getExamPaperId())
                .orderByAsc(ExamQuestion::getOrderIndex))
            .stream().map(eq -> {
                Map<String, Object> item = new HashMap<>();
                item.put("questionId", eq.getQuestionId());
                item.put("orderIndex", eq.getOrderIndex());
                item.put("score", eq.getPoints());
                return item;
            }).collect(Collectors.toList());
    }

    /**
     * 查询学生全部考试会话并附带课程维度排名。
     * 排名口径：同一课程下，学生所有含得分率（percentage）会话的平均得分率，
     * 与该课程全部参与学生同口径比较，降序得名次（并列同名次，占用位次）。
     */
    public List<Map<String, Object>> getMySessionsWithCourseRank(Long studentId) {
        List<ExamSession> sessions = examSessionMapper.selectList(new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getStudentId, studentId)
                .orderByDesc(ExamSession::getCreatedAt));
        if (sessions.isEmpty()) {
            return new ArrayList<>();
        }

        // 试卷 → 课程 映射
        Set<Long> paperIds = new HashSet<>();
        sessions.forEach(s -> {
            if (s.getExamPaperId() != null) paperIds.add(s.getExamPaperId());
        });
        Map<Long, Long> paperToCourse = new HashMap<>();
        Map<Long, List<Long>> courseToPapers = new HashMap<>();
        if (!paperIds.isEmpty()) {
            examPaperService.listByIds(paperIds).forEach(p -> {
                if (p.getCourseId() != null) {
                    paperToCourse.put(p.getId(), p.getCourseId());
                    courseToPapers.computeIfAbsent(p.getCourseId(), k -> new ArrayList<>()).add(p.getId());
                }
            });
        }

        // 课程 → (studentId → 平均得分率) → 排名
        Map<Long, int[]> courseRankInfo = computeCourseRank(studentId, courseToPapers);

        // 组装返回：原字段 + courseId / courseRank / courseTotalStudents
        List<Map<String, Object>> result = new ArrayList<>(sessions.size());
        for (ExamSession s : sessions) {
            Map<String, Object> item = objectMapper.convertValue(s,
                    new TypeReference<Map<String, Object>>() {});
            Long courseId = paperToCourse.get(s.getExamPaperId());
            item.put("courseId", courseId);
            int[] rank = courseId != null ? courseRankInfo.get(courseId) : null;
            item.put("courseRank", rank != null ? rank[0] : null);
            item.put("courseTotalStudents", rank != null ? rank[1] : null);
            result.add(item);
        }
        return result;
    }

    /**
     * 计算各课程内指定学生的排名与参与总人数。
     * 口径：课程内每个学生取其全部含 percentage 会话的平均得分率，降序排名。
     * @return courseId → [rank, totalStudents]；该课程无有效得分时不含该键
     */
    private Map<Long, int[]> computeCourseRank(Long studentId, Map<Long, List<Long>> courseToPapers) {
        Map<Long, int[]> rankInfo = new HashMap<>();
        for (Map.Entry<Long, List<Long>> entry : courseToPapers.entrySet()) {
            Long courseId = entry.getKey();
            List<Long> papers = entry.getValue();
            // 课程全部参与学生的会话（含未评分，percentage 为空不参与均分）
            List<ExamSession> courseSessions = examSessionMapper.selectList(new LambdaQueryWrapper<ExamSession>()
                    .in(ExamSession::getExamPaperId, papers)
                    .isNotNull(ExamSession::getPercentage));
            if (courseSessions.isEmpty()) continue;

            Map<Long, List<Double>> byStudent = new HashMap<>();
            for (ExamSession cs : courseSessions) {
                if (cs.getPercentage() == null || cs.getStudentId() == null) continue;
                byStudent.computeIfAbsent(cs.getStudentId(), k -> new ArrayList<>())
                        .add(cs.getPercentage().doubleValue());
            }
            if (byStudent.isEmpty() || !byStudent.containsKey(studentId)) continue;

            // 学生平均得分率降序；并列取相同名次（占位次）
            List<double[]> avgs = new ArrayList<>();
            byStudent.forEach((sid, scores) -> {
                double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                avgs.add(new double[]{sid, avg});
            });
            avgs.sort((a, b) -> Double.compare(b[1], a[1]));
            double myAvg = byStudent.get(studentId).stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0);
            int rank = 1;
            for (double[] a : avgs) {
                if (a[1] > myAvg) rank++;
                else break;
            }
            rankInfo.put(courseId, new int[]{rank, avgs.size()});
        }
        return rankInfo;
    }

    /**
     * 解析题目得分JSON字符串为Map（Jackson 严格解析，替代原手写字符串分割）
     */
    public Map<Long, BigDecimal> parseScoresJson(String json) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (json == null || json.isEmpty() || "{}".equals(json.trim())) {
            return result;
        }
        try {
            Map<Long, BigDecimal> parsed = objectMapper.readValue(json, new TypeReference<Map<Long, BigDecimal>>() {});
            if (parsed != null) {
                result.putAll(parsed);
            }
        } catch (JsonProcessingException e) {
            log.error("查询考试会话失败: {}", e.getMessage(), e);
            // 历史非法数据容错：返回空集合，不向上抛导致成绩查询失败
        }
        return result;
    }
}
