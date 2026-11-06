package com.aicourse.service.impl;

import com.aicourse.entity.AiCallLog;
import com.aicourse.mapper.AiCallLogMapper;
import com.aicourse.service.AiCallLogService;
import com.aicourse.vo.AiUsageByCourseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiCallLogService} 实现：AI 调用落库（失败安全）+ 按课程聚合统计。
 *
 * <p>token 估算直接复用 {@link AiQuotaService} 的既有口径（1 token ≈ 1.5 字符），
 * 不再另造一套折算规则——否则"配额扣的 token"与"统计显示的 token"会对不上。</p>
 */
@Service
@Slf4j
public class AiCallLogServiceImpl implements AiCallLogService {

    /** error_msg 列宽 255：超长截断，避免插入失败把埋点本身变成异常源。 */
    private static final int ERROR_MSG_MAX = 255;

    /** 未绑定课程的分组展示名（course_id IS NULL）。 */
    private static final String UNBOUND_COURSE_LABEL = "未绑定课程";

    /** 对使用者的口径声明：与 V8 迁移注释、管理端页面文案三处必须一致。 */
    private static final String COLLECTION_NOTE =
            "仅采集自 2026-10-13 埋点上线后的调用；此前的历史调用未留痕，不可回填。";

    private final AiCallLogMapper aiCallLogMapper;
    private final AiQuotaService aiQuotaService;

    public AiCallLogServiceImpl(AiCallLogMapper aiCallLogMapper, AiQuotaService aiQuotaService) {
        this.aiCallLogMapper = aiCallLogMapper;
        this.aiQuotaService = aiQuotaService;
    }

    @Override
    public void record(Long userId, Long courseId, String scene, String provider,
                       long promptTokens, String outputText, long durationMs,
                       boolean success, String errorMsg) {
        try {
            AiCallLog callLog = new AiCallLog();
            callLog.setUserId(userId);
            callLog.setCourseId(courseId);
            callLog.setScene(scene);
            callLog.setProvider(provider);
            callLog.setPromptTokens(toInt(promptTokens));
            callLog.setCompletionTokens(toInt(aiQuotaService.estimateTokens(outputText)));
            callLog.setDurationMs(toInt(durationMs));
            callLog.setSuccess(success ? 1 : 0);
            callLog.setErrorMsg(truncate(errorMsg));
            aiCallLogMapper.insert(callLog);
        } catch (Exception e) {
            // 埋点失败绝不外抛：统计是旁路，不能因它把 AI 主流程搞挂
            log.warn("AI 调用日志落库失败（不影响主流程）: scene={}, courseId={}, err={}",
                    scene, courseId, e.getMessage());
        }
    }

    @Override
    public Map<String, Object> statistics(int days) {
        return statistics(days, null);
    }

    /**
     * 管理端统计（院系口径）：仅统计归属指定学院课程的调用。
     */
    @Override
    public Map<String, Object> statistics(int days, Long collegeId) {
        int rangeDays = Math.min(Math.max(days, 1), 365);
        LocalDateTime until = LocalDateTime.now();
        LocalDateTime since = LocalDate.now().minusDays(rangeDays - 1L).atStartOfDay();
        List<AiUsageByCourseVO> rows = collegeId == null
                ? aiCallLogMapper.selectUsageByCourse(since, until)
                : aiCallLogMapper.selectUsageByCourseForCollege(since, until, collegeId);

        Map<String, Object> result = aggregate(rangeDays, since, until, rows);
        if (collegeId != null) {
            result.put("scope", "college");
            result.put("collegeId", collegeId);
            result.put("scopeNote",
                    "院系口径：仅统计本院课程；未绑定课程的调用无学院归属，不计入本院视图。");
        }
        return result;
    }

    private Map<String, Object> aggregate(int rangeDays, LocalDateTime since,
                                          LocalDateTime until, List<AiUsageByCourseVO> rows) {
        long totalCalls = 0L;
        long totalTokens = 0L;
        long successCalls = 0L;
        long unboundCalls = 0L;
        long distinctCourses = 0L;
        double weightedDuration = 0d;

        for (AiUsageByCourseVO row : rows) {
            long calls = row.getCalls() == null ? 0L : row.getCalls();
            long rowSuccess = row.getSuccessCalls() == null ? 0L : row.getSuccessCalls();
            double avgDuration = row.getAvgDurationMs() == null ? 0d : row.getAvgDurationMs();

            totalCalls += calls;
            totalTokens += row.getTokens() == null ? 0L : row.getTokens();
            successCalls += rowSuccess;
            // 本查询按 COUNT 分组，用次数加权还原整体均值（直接对分组的 AVG 再平均是错的）
            weightedDuration += avgDuration * calls;

            if (row.getCourseId() == null) {
                unboundCalls += calls;
                row.setCourseName(UNBOUND_COURSE_LABEL);
            } else {
                distinctCourses += 1L;
            }
            row.setSuccessRate(calls == 0 ? null : round2((double) rowSuccess / calls));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rangeDays", rangeDays);
        result.put("since", since.toString());
        result.put("until", until.toString());
        result.put("collectionNote", COLLECTION_NOTE);
        result.put("totalCalls", totalCalls);
        result.put("totalTokens", totalTokens);
        result.put("distinctCourses", distinctCourses);
        result.put("unboundCalls", unboundCalls);
        result.put("avgDurationMs", totalCalls == 0 ? 0d : round2(weightedDuration / totalCalls));
        result.put("successRate", totalCalls == 0 ? null : round2((double) successCalls / totalCalls));
        result.put("courses", rows);
        return result;
    }

    private int toInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= ERROR_MSG_MAX ? text : text.substring(0, ERROR_MSG_MAX);
    }

    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
