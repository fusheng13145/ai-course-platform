package com.aicourse.scheduler;

import com.aicourse.constant.ExamSessionStatus;

import com.aicourse.entity.ExamSession;
import com.aicourse.service.ExamSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 考试超时自动交卷调度器
 * 周期性扫描处于 STARTED 状态的考试会话，对超过时限（开始时间+考试时长 / 试卷结束时间，取较早者）
 * 仍未提交的会话，调用 ExamSessionService.timeoutSubmit 自动收卷并阅卷，杜绝"客户端不提交"的作弊漏洞。
 */
@Component
public class ExamTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExamTimeoutScheduler.class);

    private final ExamSessionService examSessionService;

    public ExamTimeoutScheduler(ExamSessionService examSessionService) {
        this.examSessionService = examSessionService;
    }

    /** 每 30 秒扫描一次进行中的考试；分布式锁保证多实例部署下只有一个节点执行收卷 */
    @Scheduled(fixedDelay = 30000)
    @SchedulerLock(name = "examTimeoutAutoSubmit", lockAtMostFor = "PT28S", lockAtLeastFor = "PT20S")
    public void autoSubmitExpiredExams() {
        try {
            List<ExamSession> active = examSessionService.list(
                    new LambdaQueryWrapper<ExamSession>().eq(ExamSession::getStatus, ExamSessionStatus.STARTED));
            if (active == null || active.isEmpty()) return;
            int failed = 0;
            for (ExamSession s : active) {
                try {
                    examSessionService.timeoutSubmit(s.getId());
                    // 超时收卷后立即自动阅卷，避免 AUTO_SUBMITTED 卷滞留永不评分（修复 P1-14 闭环）。
                    // 学生全程未作答（answers 为空）时阅卷会拒绝，此时跳过并在下方记录，不阻塞调度。
                    examSessionService.autoGrade(s.getId());
                } catch (Exception e) {
                    // 单场异常不影响其他场次，但必须记录以便运维排障（原静默吞异常会造成收卷失败的运维盲区）
                    failed++;
                    log.error("考试超时自动收卷/阅卷失败 sessionId={}: {}", s.getId(), e.getMessage(), e);
                }
            }
            if (failed > 0) {
                log.warn("本轮考试超时收卷完成，共 {} 场，其中 {} 场失败（详见上方 error 日志）", active.size(), failed);
            }
        } catch (Exception e) {
            // 调度本身异常（如 DB 不可用）不应中断后续周期执行，但必须记录告警而非静默吞掉
            log.error("考试超时收卷调度异常，本轮跳过，等待下一周期重试: {}", e.getMessage(), e);
        }
    }
}
