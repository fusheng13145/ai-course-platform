package com.aicourse.service.impl;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.LearningRecord;
import com.aicourse.entity.LoginLog;
import com.aicourse.entity.User;
import com.aicourse.mapper.LearningRecordMapper;
import com.aicourse.mapper.LoginLogMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.LearningRiskService;
import com.aicourse.service.UserService;
import com.aicourse.vo.CourseRiskPredictionVO;
import com.aicourse.vo.StudentRiskVO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * LearningRiskServiceImpl 单元测试（P2 预测性学情预警）。
 * 锁定五维信号加权 → 风险分/等级/原因的派生逻辑。
 */
@ExtendWith(MockitoExtension.class)
class LearningRiskServiceImplTest {

    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private LoginLogMapper loginLogMapper;
    @Mock private LearningRecordMapper learningRecordMapper;
    @Mock private UserService userService;

    private LearningRiskService riskService;

    @BeforeEach
    void setUp() {
        riskService = new LearningRiskServiceImpl(enrollmentService, loginLogMapper, learningRecordMapper, userService);
    }

    private CourseEnrollment enrollment(Long courseId, Long studentId, BigDecimal progress, BigDecimal finalScore, Integer studyTime) {
        CourseEnrollment e = new CourseEnrollment();
        e.setCourseId(courseId);
        e.setStudentId(studentId);
        e.setProgress(progress);
        e.setFinalScore(finalScore);
        e.setTotalStudyTime(studyTime);
        e.setIsActive(true);
        return e;
    }

    private LoginLog login(Long userId, int daysAgo) {
        LoginLog l = new LoginLog();
        l.setUserId(userId);
        l.setLoginStatus("SUCCESS");
        l.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
        return l;
    }

    private LearningRecord rec(Long userId, Long courseId, int daysAgo) {
        LearningRecord r = new LearningRecord();
        r.setUserId(userId);
        r.setCourseId(courseId);
        r.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
        return r;
    }

    @Test
    @DisplayName("courseId 为空 → 返回空结果")
    void nullCourseId_returnsEmpty() {
        CourseRiskPredictionVO vo = riskService.predictCourseRisk(null);
        assertNotNull(vo);
        assertEquals(0, vo.getTotal());
        assertTrue(vo.getStudents().isEmpty());
    }

    @Test
    @DisplayName("无在修学生 → 返回空结果")
    void noEnrollments_returnsEmpty() {
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of());
        CourseRiskPredictionVO vo = riskService.predictCourseRisk(1L);
        assertEquals(0, vo.getTotal());
        assertTrue(vo.getStudents().isEmpty());
    }

    @Test
    @DisplayName("成绩不及格+进度极低+无登录+无学习行为+时长短 → HIGH(100)")
    void highRisk_fullSignal() {
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(
                enrollment(1L, 10L, new BigDecimal("0.20"), new BigDecimal("50"), 30)));
        when(userService.listByIds(any())).thenReturn(List.of(user(10L, "张三", "2021001")));
        // 1 条登录，距今天 10 天（落在 14 天窗口，但不在近 7 天）
        when(loginLogMapper.selectList(any())).thenReturn(List.of(login(10L, 10)));
        when(learningRecordMapper.selectList(any())).thenReturn(List.of());

        CourseRiskPredictionVO vo = riskService.predictCourseRisk(1L);
        assertEquals(1, vo.getTotal());
        assertEquals(1, vo.getHighCount());
        StudentRiskVO s = vo.getStudents().get(0);
        assertEquals(100, s.getRiskScore());
        assertEquals("HIGH", s.getRiskTier());
        assertTrue(s.getReasons().contains("综合成绩低于及格线(60分)"));
        assertTrue(s.getReasons().contains("课程学习进度不足30%"));
        assertTrue(s.getReasons().contains("近7天无登录记录"));
        assertTrue(s.getReasons().contains("近14天无课程学习行为"));
        assertTrue(s.getReasons().contains("课程累计学习时长偏短(<60分钟)"));
        assertEquals("张三", s.getStudentName());
        assertEquals("2021001", s.getStudentNo());
    }

    @Test
    @DisplayName("成绩优+进度高+高频登录+高频学习+时长足 → LOW(0)")
    void lowRisk_clean() {
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(
                enrollment(1L, 11L, new BigDecimal("0.90"), new BigDecimal("85"), 200)));
        when(userService.listByIds(any())).thenReturn(List.of(user(11L, "李四", "2021002")));
        when(loginLogMapper.selectList(any())).thenReturn(List.of(login(11L, 1), login(11L, 2), login(11L, 3), login(11L, 4), login(11L, 5)));
        when(learningRecordMapper.selectList(any())).thenReturn(List.of(rec(11L, 1L, 1), rec(11L, 1L, 2), rec(11L, 1L, 3)));

        CourseRiskPredictionVO vo = riskService.predictCourseRisk(1L);
        assertEquals(1, vo.getTotal());
        assertEquals(1, vo.getLowCount());
        StudentRiskVO s = vo.getStudents().get(0);
        assertEquals(0, s.getRiskScore());
        assertEquals("LOW", s.getRiskTier());
        assertTrue(s.getReasons().isEmpty());
    }

    @Test
    @DisplayName("进度极低+低频登录+低频学习(成绩合格) → MID(43)")
    void midRisk_partialSignal() {
        // 成绩 80（合格，不计分）；进度 0.2(+25)；2 次登录近 2 天(+10)；1 次学习(+8)；时长 120(不计分) = 43 → MID
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(
                enrollment(1L, 12L, new BigDecimal("0.20"), new BigDecimal("80"), 120)));
        when(userService.listByIds(any())).thenReturn(List.of(user(12L, "王五", "2021003")));
        when(loginLogMapper.selectList(any())).thenReturn(List.of(login(12L, 2), login(12L, 3)));
        when(learningRecordMapper.selectList(any())).thenReturn(List.of(rec(12L, 1L, 2)));

        CourseRiskPredictionVO vo = riskService.predictCourseRisk(1L);
        assertEquals(1, vo.getMidCount());
        StudentRiskVO s = vo.getStudents().get(0);
        assertEquals(43, s.getRiskScore());
        assertEquals("MID", s.getRiskTier());
        assertTrue(s.getReasons().contains("课程学习进度不足30%"));
        assertTrue(s.getReasons().contains("近14天登录次数少于3次"));
        assertTrue(s.getReasons().contains("近14天课程学习行为偏少(<2次)"));
    }

    private User user(Long id, String name, String studentNo) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setStudentId(studentNo);
        return u;
    }
}
