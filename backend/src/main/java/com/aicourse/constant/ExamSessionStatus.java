package com.aicourse.constant;

import java.util.Set;

/**
 * 考试会话状态常量（统一收口魔法字符串）
 * <p>
 * 考试状态机：STARTED(进行中) → SUBMITTED(主动提交) / AUTO_SUBMITTED(超时自动收卷) /
 * FORCE_SUBMITTED(强制收卷) → GRADED(已阅卷)。TIMEOUT 为历史遗留的超时标记。
 * 收敛后杜绝散落字面量拼写不一致（如 "SUBMITTED"/"SUBMITTED " 混用）。
 */
public final class ExamSessionStatus {

    private ExamSessionStatus() {
    }

    /** 进行中 */
    public static final String STARTED = "STARTED";
    /** 学生主动提交 */
    public static final String SUBMITTED = "SUBMITTED";
    /** 超时自动收卷 */
    public static final String AUTO_SUBMITTED = "AUTO_SUBMITTED";
    /** 已阅卷 */
    public static final String GRADED = "GRADED";
    /** 强制收卷（异常兜底） */
    public static final String FORCE_SUBMITTED = "FORCE_SUBMITTED";
    /** 历史遗留超时标记 */
    public static final String TIMEOUT = "TIMEOUT";

    /** 已提交（进入阅卷前置条件）的状态集合 */
    public static final Set<String> SUBMITTED_STATES =
            Set.of(SUBMITTED, AUTO_SUBMITTED, FORCE_SUBMITTED, TIMEOUT);
}
