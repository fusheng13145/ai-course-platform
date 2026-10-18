package com.aicourse.scheduler;

import com.aicourse.entity.LearningRecord;
import com.aicourse.entity.OperationLog;
import com.aicourse.entity.UserSession;
import com.aicourse.entity.VerificationCode;
import com.aicourse.entity.VideoWatchSession;
import com.aicourse.mapper.LearningRecordMapper;
import com.aicourse.mapper.OperationLogMapper;
import com.aicourse.mapper.UserSessionMapper;
import com.aicourse.mapper.VerificationCodeMapper;
import com.aicourse.mapper.VideoWatchSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 数据保留策略调度器（每日清理，防表无限膨胀）
 * <p>
 * 此前以下表只增不清：验证码（消费后仅置 is_used）、失效用户会话、操作日志（大表膨胀风险最高）、
 * 学习记录与观看会话（心跳级累积）。保留策略：
 * <ul>
 *   <li>验证码：创建超过 {@code 7} 天（已用/过期）删除；</li>
 *   <li>失效用户会话：is_active=false 且创建超过 {@code 90} 天删除（活跃会话由 expires_at 管理，不受影响）；</li>
 *   <li>操作日志：创建超过 {@code 90} 天删除；</li>
 *   <li>学习记录 / 观看会话：创建超过 {@code 180} 天删除。</li>
 *   <li>上传会话：expires_at 早于当前时刻即删行，并回收其在 {@code file.chunk-dir} 下的分片目录
 *       （由 {@code FileUploadSessionService.cleanExpiredSessions()} 承担，本处为唯一调用方）。</li>
 * </ul>
 * 每项独立事务、失败隔离互不影响；删除行数计入日志。开关 {@code app.maintenance.enabled}（默认 true）。
 */
@Component
public class DataMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataMaintenanceScheduler.class);

    /** 验证码保留天数 */
    private static final int VERIFICATION_CODE_DAYS = 7;
    /** 失效会话保留天数 */
    private static final int INACTIVE_SESSION_DAYS = 90;
    /** 操作日志保留天数 */
    private static final int OPERATION_LOG_DAYS = 90;
    /** 学习记录 / 观看会话保留天数 */
    private static final int LEARNING_RECORD_DAYS = 180;

    private final VerificationCodeMapper verificationCodeMapper;
    private final UserSessionMapper userSessionMapper;
    private final OperationLogMapper operationLogMapper;
    private final LearningRecordMapper learningRecordMapper;
    private final VideoWatchSessionMapper videoWatchSessionMapper;
    private final com.aicourse.service.FileUploadSessionService fileUploadSessionService;

    @Value("${app.maintenance.enabled:true}")
    private boolean enabled;

    public DataMaintenanceScheduler(VerificationCodeMapper verificationCodeMapper,
                                    UserSessionMapper userSessionMapper,
                                    OperationLogMapper operationLogMapper,
                                    LearningRecordMapper learningRecordMapper,
                                    VideoWatchSessionMapper videoWatchSessionMapper,
                                    com.aicourse.service.FileUploadSessionService fileUploadSessionService) {
        this.verificationCodeMapper = verificationCodeMapper;
        this.userSessionMapper = userSessionMapper;
        this.operationLogMapper = operationLogMapper;
        this.learningRecordMapper = learningRecordMapper;
        this.videoWatchSessionMapper = videoWatchSessionMapper;
        this.fileUploadSessionService = fileUploadSessionService;
    }

    /** 每日 03:30 执行（避开业务高峰）；ShedLock 保证多实例部署下仅一个节点执行 */
    @Scheduled(cron = "0 30 3 * * ?")
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(
            name = "dataMaintenanceCleanup",
            lockAtMostFor = "PT15M",
            lockAtLeastFor = "PT10S")
    public void runCleanup() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        log.info("[maintenance] 数据保留清理开始");
        cleanupVerificationCodes(now);
        cleanupInactiveSessions(now);
        cleanupOperationLogs(now);
        cleanupLearningRecords(now);
        cleanupExpiredUploadSessions();
        sweepOrphanUploadChunkDirs();
        log.info("[maintenance] 数据保留清理结束");
    }

    /**
     * 过期上传会话清理——同时是分片暂存目录的唯一回收入口。
     * <p>会话表按 expires_at 过期，但分片落在 {@code file.chunk-dir} 下的目录里；
     * 本任务此前无任何调用方，故分片目录只增不清。删除行与回收目录都在 service 内完成。</p>
     */
    private void cleanupExpiredUploadSessions() {
        try {
            int removed = fileUploadSessionService.cleanExpiredSessions();
            if (removed > 0) {
                log.info("[maintenance] 清理过期上传会话 {} 条并回收其分片目录", removed);
            }
        } catch (Exception e) {
            log.warn("[maintenance] 上传会话清理失败（已隔离）: {}", e.getMessage());
        }
    }

    /**
     * 无主分片目录兜底回收——上一项只清「行还在」的会话，行被直接删掉时磁盘没人管。
     * 与上一项刻意分开：两者失败的处置不同（删行失败要查库，回收失败只看磁盘）。
     */
    private void sweepOrphanUploadChunkDirs() {
        try {
            int swept = fileUploadSessionService.sweepOrphanChunkDirs();
            if (swept > 0) {
                log.info("[maintenance] 回收无主分片目录 {} 个", swept);
            }
        } catch (Exception e) {
            log.warn("[maintenance] 无主分片目录回收失败（已隔离）: {}", e.getMessage());
        }
    }

    private void cleanupVerificationCodes(LocalDateTime now) {
        try {
            LocalDateTime cutoff = now.minusDays(VERIFICATION_CODE_DAYS);
            int removed = verificationCodeMapper.delete(new LambdaQueryWrapper<VerificationCode>()
                    .lt(VerificationCode::getCreatedAt, cutoff));
            if (removed > 0) {
                log.info("[maintenance] 清理过期验证码 {} 条（<{}d）", removed, VERIFICATION_CODE_DAYS);
            }
        } catch (Exception e) {
            log.warn("[maintenance] 验证码清理失败（已隔离）: {}", e.getMessage());
        }
    }

    private void cleanupInactiveSessions(LocalDateTime now) {
        try {
            LocalDateTime cutoff = now.minusDays(INACTIVE_SESSION_DAYS);
            int removed = userSessionMapper.delete(new LambdaQueryWrapper<UserSession>()
                    .eq(UserSession::getIsActive, false)
                    .lt(UserSession::getCreatedAt, cutoff));
            if (removed > 0) {
                log.info("[maintenance] 清理失效会话 {} 条（is_active=false 且 <{}d）", removed, INACTIVE_SESSION_DAYS);
            }
        } catch (Exception e) {
            log.warn("[maintenance] 失效会话清理失败（已隔离）: {}", e.getMessage());
        }
    }

    private void cleanupOperationLogs(LocalDateTime now) {
        try {
            LocalDateTime cutoff = now.minusDays(OPERATION_LOG_DAYS);
            int removed = operationLogMapper.delete(new LambdaQueryWrapper<OperationLog>()
                    .lt(OperationLog::getCreatedAt, cutoff));
            if (removed > 0) {
                log.info("[maintenance] 清理操作日志 {} 条（<{}d）", removed, OPERATION_LOG_DAYS);
            }
        } catch (Exception e) {
            log.warn("[maintenance] 操作日志清理失败（已隔离）: {}", e.getMessage());
        }
    }

    private void cleanupLearningRecords(LocalDateTime now) {
        try {
            LocalDateTime cutoff = now.minusDays(LEARNING_RECORD_DAYS);
            int removed = learningRecordMapper.delete(new LambdaQueryWrapper<LearningRecord>()
                    .lt(LearningRecord::getCreatedAt, cutoff));
            if (removed > 0) {
                log.info("[maintenance] 清理学习记录 {} 条（<{}d）", removed, LEARNING_RECORD_DAYS);
            }
            int removedWatch = videoWatchSessionMapper.delete(new LambdaQueryWrapper<VideoWatchSession>()
                    .lt(VideoWatchSession::getStartedAt, cutoff));
            if (removedWatch > 0) {
                log.info("[maintenance] 清理观看会话 {} 条（<{}d）", removedWatch, LEARNING_RECORD_DAYS);
            }
        } catch (Exception e) {
            log.warn("[maintenance] 学习记录清理失败（已隔离）: {}", e.getMessage());
        }
    }
}
