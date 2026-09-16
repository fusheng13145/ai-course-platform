package com.aicourse.scheduler;

import com.aicourse.constant.ExamSessionStatus;

import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试超时自动交卷调度器
 * 周期性扫描处于 STARTED 状态的考试会话，对**已超过考试时限**（开始时间 + 考试时长 /
 * 试卷结束时间，取较早者）仍未提交的会话，调用 {@link ExamSessionService#timeoutSubmit(Long)}
 * 自动收卷并阅卷，杜绝「客户端不提交」的作弊漏洞。
 *
 * <p><b>修复记录（2026-09-14，P0）</b>：原实现取出全部 {@code STARTED} 会话后**不经任何时限判定**
 * 就逐场调用 {@code timeoutSubmit}，等价于「每 30 秒把所有进行中的考试强制收卷」——
 * 45 分钟的考试会在最长 30 秒内被判超时，考试时长形同虚设（实测：开考后 40s 状态即变
 * {@code AUTO_SUBMITTED}）。本次补上 {@link #resolveDeadline(ExamSession)} 时限判定：
 * 未到期的会话一律跳过；并在会话无作答时跳过自动阅卷（原实现会对空作答抛
 * 「未找到学生作答记录」并以 ERROR 级别每 30 秒刷屏）。</p>
 */
@Component
public class ExamTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExamTimeoutScheduler.class);

    private final ExamSessionService examSessionService;
    private final ExamPaperService examPaperService;

    /**
     * 兜底时长（分钟）：试卷既无 time_limit/duration 也无 end_time 时使用，
     * 使「无期限依据」的考试仍有确定的收卷时点（原策略是永不收卷）。
     * 设为 0 表示显式关闭兜底，此时这类会话不会被自动收卷，需管理端 force-submit。
     */
    @Value("${exam.timeout.default-time-limit-minutes:120}")
    private int defaultTimeLimitMinutes;

    public ExamTimeoutScheduler(ExamSessionService examSessionService,
                                ExamPaperService examPaperService) {
        this.examSessionService = examSessionService;
        this.examPaperService = examPaperService;
    }

    /** 每 30 秒扫描一次进行中的考试；分布式锁保证多实例部署下只有一个节点执行收卷 */
    @Scheduled(fixedDelay = 30000)
    @SchedulerLock(name = "examTimeoutAutoSubmit", lockAtMostFor = "PT28S", lockAtLeastFor = "PT20S")
    public void autoSubmitExpiredExams() {
        try {
            List<ExamSession> active = examSessionService.list(
                    new LambdaQueryWrapper<ExamSession>().eq(ExamSession::getStatus, ExamSessionStatus.STARTED));
            if (active == null || active.isEmpty()) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            int expired = 0;
            int graded = 0;
            int failed = 0;
            for (ExamSession s : active) {
                try {
                    // 关键修复：未超过时限的会话必须原样放过，不得收卷
                    if (!isExpired(s, now)) {
                        continue;
                    }
                    examSessionService.timeoutSubmit(s.getId());
                    expired++;
                    // 超时收卷后立即自动阅卷，避免 AUTO_SUBMITTED 卷滞留永不评分（修复 P1-14 闭环）。
                    // 学生全程未作答（answers 为空）时阅卷会拒绝，此处直接跳过并记 WARN，
                    // 不再以 ERROR 级别刷屏（原实现每 30 秒重复报错）。
                    if (hasAnswers(s)) {
                        examSessionService.autoGrade(s.getId());
                        graded++;
                    } else {
                        log.warn("[ExamTimeout] 会话无有效作答，已收卷并跳过自动阅卷：sessionId={}", s.getId());
                    }
                } catch (Exception e) {
                    // 单场异常不影响其他场次，但必须记录以便运维排障（原静默吞异常会造成收卷失败的运维盲区）
                    failed++;
                    log.error("考试超时自动收卷/阅卷失败 sessionId={}: {}", s.getId(), e.getMessage(), e);
                }
            }
            if (expired > 0 || failed > 0) {
                log.warn("本轮考试超时收卷完成：扫描 {} 场，到期收卷 {} 场，自动阅卷 {} 场，失败 {} 场",
                        active.size(), expired, graded, failed);
            }
        } catch (Exception e) {
            // 调度本身异常（如 DB 不可用）不应中断后续周期执行，但必须记录告警而非静默吞掉
            log.error("考试超时收卷调度异常，本轮跳过，等待下一周期重试: {}", e.getMessage(), e);
        }
    }

    /**
     * 判定会话是否已超过考试时限。
     *
     * <p>时限取「开始时间 + 考试时长」与「试卷结束时间」两者中较早者；
     * 若试卷未配置任何时限（time_limit / duration / end_time 皆空），则使用配置兜底时长
     * {@code exam.timeout.default-time-limit-minutes}，避免出现「永不收卷」的悬空会话。
     * 兜底时长配置为 0（或不大于 0）且无其它依据时，**保守返回 null（不收卷）**，交人工处理。</p>
     */
    private boolean isExpired(ExamSession session, LocalDateTime now) {
        LocalDateTime deadline = resolveDeadline(session);
        return deadline != null && now.isAfter(deadline);
    }

    private LocalDateTime resolveDeadline(ExamSession session) {
        if (session.getExamPaperId() == null) {
            return null;
        }
        ExamPaper paper = examPaperService.getById(session.getExamPaperId());
        if (paper == null) {
            return null;
        }
        LocalDateTime deadline = null;
        Integer minutes = paper.getTimeLimit() != null ? paper.getTimeLimit() : paper.getDuration();
        if (minutes == null || minutes <= 0) {
            // 试卷无时长配置 → 退到兜底时长（0 表示关闭兜底）
            minutes = defaultTimeLimitMinutes > 0 ? defaultTimeLimitMinutes : null;
        }
        if (minutes != null && minutes > 0 && session.getStartTime() != null) {
            deadline = session.getStartTime().plusMinutes(minutes);
        }
        // 试卷结束时间为硬边界：更早则取结束时间
        if (paper.getEndTime() != null && (deadline == null || paper.getEndTime().isBefore(deadline))) {
            deadline = paper.getEndTime();
        }
        return deadline;
    }

    /** 会话是否存在有效作答（用于决定是否触发自动阅卷）。 */
    private boolean hasAnswers(ExamSession session) {
        String answers = session.getAnswers();
        if (answers == null) {
            return false;
        }
        String trimmed = answers.trim();
        return !trimmed.isEmpty() && !"{}".equals(trimmed) && !"null".equalsIgnoreCase(trimmed);
    }
}
