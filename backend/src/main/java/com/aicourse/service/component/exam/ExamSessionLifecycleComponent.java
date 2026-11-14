package com.aicourse.service.component.exam;

import com.aicourse.constant.ExamSessionStatus;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamPaperMapper;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.component.org.TeachingClassSnapshotResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 考试会话生命周期组件：开始 / 自动保存 / 提交 / 强制交卷 / 超时收卷 / 剩余时间 / 最新会话。
 * <p>拆分自原 ExamSessionServiceImpl（2026-08-29），组件持有 ExamSessionMapper 直接落库。</p>
 */
@Slf4j
@Component
public class ExamSessionLifecycleComponent {

    private final ExamPaperService examPaperService;
    private final ExamSessionMapper examSessionMapper;
    private final TeachingClassSnapshotResolver teachingClassSnapshotResolver;
    private final ObjectMapper objectMapper;
    private final ExamPaperMapper examPaperMapper;

    public ExamSessionLifecycleComponent(ExamPaperService examPaperService,
                                         ExamSessionMapper examSessionMapper,
                                         TeachingClassSnapshotResolver teachingClassSnapshotResolver,
                                         ObjectMapper objectMapper,
                                         ExamPaperMapper examPaperMapper) {
        this.examPaperService = examPaperService;
        this.examSessionMapper = examSessionMapper;
        this.teachingClassSnapshotResolver = teachingClassSnapshotResolver;
        this.objectMapper = objectMapper;
        this.examPaperMapper = examPaperMapper;
    }

    /**
     * 开始考试（含规则校验：时间窗口、尝试次数限制）
     */
    public ExamSession startExam(Long examPaperId, Long studentId, String ipAddress, String userAgent) {
        // 检查试卷是否存在且已发布
        ExamPaper examPaper = examPaperService.getById(examPaperId);
        if (examPaper == null) {
            throw new BusinessException("试卷不存在");
        }
        if (!"PUBLISHED".equals(examPaper.getStatus())) {
            throw new BusinessException("试卷尚未发布，无法开始考试");
        }

        // 检查时间窗口
        LocalDateTime now = LocalDateTime.now();
        if (examPaper.getStartTime() != null && now.isBefore(examPaper.getStartTime())) {
            throw new BusinessException("考试尚未开始");
        }
        if (examPaper.getEndTime() != null && now.isAfter(examPaper.getEndTime())) {
            throw new BusinessException("考试已结束");
        }

        // 修复 A-01 #4：对试卷行加悲观锁（FOR UPDATE），使同卷并发开考的
        // 「检查尝试次数/活跃会话 → insert 会话」串行化，杜绝双击并发开出两个 STARTED 会话绕过 maxAttempts。
        // 事务由门面 ExamSessionServiceImpl#startExam 提供（@Transactional），锁随事务提交释放。
        examPaperMapper.selectByIdForUpdate(examPaperId);

        // 检查尝试次数限制
        Integer maxAttempts = examPaper.getMaxAttempts();
        if (maxAttempts != null && maxAttempts > 0) {
            LambdaQueryWrapper<ExamSession> countWrapper = new LambdaQueryWrapper<ExamSession>()
                    .eq(ExamSession::getExamPaperId, examPaperId)
                    .eq(ExamSession::getStudentId, studentId)
                    .in(ExamSession::getStatus, ExamSessionStatus.STARTED, ExamSessionStatus.SUBMITTED, ExamSessionStatus.GRADED, ExamSessionStatus.TIMEOUT);
            long existingAttempts = examSessionMapper.selectCount(countWrapper);
            if (existingAttempts >= maxAttempts) {
                throw new BusinessException("已达到最大尝试次数限制（" + maxAttempts + "次）");
            }
        }

        // 检查是否有正在进行的考试
        LambdaQueryWrapper<ExamSession> activeWrapper = new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, examPaperId)
                .eq(ExamSession::getStudentId, studentId)
                .eq(ExamSession::getStatus, ExamSessionStatus.STARTED);
        ExamSession activeSession = examSessionMapper.selectOne(activeWrapper, false);
        if (activeSession != null) {
            throw new BusinessException("您有正在进行的考试，请先完成或结束当前考试");
        }

        // 创建新的考试会话
        int attemptNumber = examSessionMapper.selectCount(new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, examPaperId)
                .eq(ExamSession::getStudentId, studentId)).intValue() + 1;

        ExamSession session = new ExamSession();
        session.setExamPaperId(examPaperId);
        session.setStudentId(studentId);
        // WP7 快照：开考即归属当时的教学班，转班后历史会话不漂移
        session.setTeachingClassId(
                teachingClassSnapshotResolver.resolveTeachingClassId(studentId, examPaper.getCourseId()));
        session.setStatus(ExamSessionStatus.STARTED);
        session.setStartTime(LocalDateTime.now());
        session.setAttemptNumber(attemptNumber);
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        examSessionMapper.insert(session);

        return session;
    }

    /**
     * 自动保存作答答案
     */
    public void autoSaveAnswers(Long sessionId, Map<String, Object> answers) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (!ExamSessionStatus.STARTED.equals(session.getStatus())) {
            throw new BusinessException("当前考试状态不允许保存答案");
        }

        // 修复 A-01 #1：改为定向 CAS 更新——只 set 答案与更新时间，且 WHERE 限定 status=STARTED。
        // 原实现「读整实体 → updateById 全字段回写」，与学生交卷（STARTED→SUBMITTED）并发时
        // 会用本地快照把已结束会话复活回 STARTED，答案/提交时间一并被旧值覆盖。
        // 影响行数为 0 说明并发窗口内会话已流转，按既有 API 契约抛业务异常（返回形状不变）。
        LambdaUpdateWrapper<ExamSession> updateWrapper = new LambdaUpdateWrapper<ExamSession>()
                .eq(ExamSession::getId, sessionId)
                .eq(ExamSession::getStatus, ExamSessionStatus.STARTED)
                .set(ExamSession::getAnswers, mapToJson(answers))
                .set(ExamSession::getUpdatedAt, LocalDateTime.now());
        if (examSessionMapper.update(null, updateWrapper) == 0) {
            throw new BusinessException("考试已结束，无法保存答案");
        }
    }

    /**
     * 提交试卷（含防重复提交）
     */
    public ExamSession submitExam(Long sessionId, Map<String, Object> answers) {
        log.info("提交考试会话");

        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        // 防止时钟回调导致时间倒退
        LocalDateTime now = LocalDateTime.now();

        // 原子更新：仅当状态为 STARTED 时才允许提交（消除 TOCTOU 竞态）
        LambdaUpdateWrapper<ExamSession> updateWrapper = new LambdaUpdateWrapper<ExamSession>()
                .eq(ExamSession::getId, sessionId)
                .eq(ExamSession::getStatus, ExamSessionStatus.STARTED)
                .set(ExamSession::getAnswers, mapToJson(answers))
                .set(ExamSession::getTimeSpent, (int) ChronoUnit.SECONDS.between(session.getStartTime(), now))
                .set(ExamSession::getStatus, ExamSessionStatus.SUBMITTED)
                .set(ExamSession::getSubmitTime, now);
        boolean updated = examSessionMapper.update(null, updateWrapper) > 0;
        if (!updated) {
            throw new BusinessException("该试卷已提交或已结束，无法重复提交");
        }

        // 重新读取最新状态返回
        return examSessionMapper.selectById(sessionId);
    }

    /**
     * 强制交卷（管理员操作）
     */
    public ExamSession forceSubmit(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (ExamSessionStatus.SUBMITTED.equals(session.getStatus()) || ExamSessionStatus.GRADED.equals(session.getStatus())) {
            throw new BusinessException("该试卷已经提交，无需强制交卷");
        }

        // 修复 A-01 #2：CAS 更新 STARTED → FORCE_SUBMITTED，不再整实体 updateById。
        // 原实现会把本地快照（含覆盖前的 answers/timeSpent）整行回写，与学生提交并发时
        // 会抹掉学生刚交的答案；CAS 失败（0 行）即并发窗口内状态已流转，不得覆盖，
        // 记 warn 后返回当前真实状态，由调用方自行呈现。
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ExamSession> updateWrapper = new LambdaUpdateWrapper<ExamSession>()
                .eq(ExamSession::getId, sessionId)
                .eq(ExamSession::getStatus, ExamSessionStatus.STARTED)
                .set(ExamSession::getStatus, ExamSessionStatus.FORCE_SUBMITTED)
                .set(ExamSession::getSubmitTime, now)
                .set(ExamSession::getTimeSpent, (int) ChronoUnit.SECONDS.between(session.getStartTime(), now));
        int updated = examSessionMapper.update(null, updateWrapper);
        if (updated == 0) {
            log.warn("强制交卷未生效：会话已在并发中流转（提交/强交/超时收卷），保留最新落库状态: sessionId={}", sessionId);
        }
        return examSessionMapper.selectById(sessionId);
    }

    /**
     * 获取考试剩余时间(秒)
     */
    public Long getRemainingTime(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (!ExamSessionStatus.STARTED.equals(session.getStatus())) {
            return 0L;
        }

        ExamPaper paper = examPaperService.getById(session.getExamPaperId());
        if (paper == null) {
            return 0L;
        }

        LocalDateTime now = LocalDateTime.now();
        Long remainingSeconds = null;

        // 基于考试时长计算
        if (paper.getDuration() != null && paper.getDuration() > 0) {
            long elapsed = ChronoUnit.SECONDS.between(session.getStartTime(), now);
            long durationSeconds = paper.getDuration() * 60L;
            remainingSeconds = Math.max(0, durationSeconds - elapsed);
        }

        // 基于考试结束时间计算
        if (paper.getEndTime() != null) {
            long endRemaining = ChronoUnit.SECONDS.between(now, paper.getEndTime());
            if (remainingSeconds == null) {
                remainingSeconds = Math.max(0, endRemaining);
            } else {
                remainingSeconds = Math.min(remainingSeconds, Math.max(0, endRemaining));
            }
        }

        return remainingSeconds != null ? remainingSeconds : 0L;
    }

    /**
     * 获取学生某试卷的最新会话
     */
    public ExamSession getLatestSession(Long examPaperId, Long studentId) {
        LambdaQueryWrapper<ExamSession> wrapper = new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, examPaperId)
                .eq(ExamSession::getStudentId, studentId)
                .orderByDesc(ExamSession::getCreatedAt)
                .last("LIMIT 1");
        return examSessionMapper.selectOne(wrapper, false);
    }

    /**
     * 超时收卷（只流转状态与提交时间，保留学生已保存的 answers）。
     * 修复 P0-2：不得覆盖 answers 为空，也不得清空作答内容。
     */
    public void timeoutSubmit(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null || !ExamSessionStatus.STARTED.equals(session.getStatus())) return;

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ExamSession> updateWrapper = new LambdaUpdateWrapper<ExamSession>()
                .eq(ExamSession::getId, sessionId)
                .eq(ExamSession::getStatus, ExamSessionStatus.STARTED)
                .set(ExamSession::getStatus, ExamSessionStatus.AUTO_SUBMITTED)
                .set(ExamSession::getSubmitTime, now)
                .set(ExamSession::getTimeSpent, (int) ChronoUnit.SECONDS.between(session.getStartTime(), now));
        examSessionMapper.update(null, updateWrapper);
    }

    /**
     * 将Map转换为JSON字符串。
     * 修复 P1-13：原手拼 StringBuilder 不转义引号/反斜杠/换行，答案含特殊字符即生成非法 JSON。
     * 现委托 Jackson 序列化，任何值均可正确转义。
     */
    public String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("查询考试会话失败: {}", e.getMessage(), e);
            throw new BusinessException("答案序列化失败");
        }
    }
}
