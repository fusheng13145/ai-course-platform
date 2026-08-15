package com.aicourse.constant;

import java.util.Set;

/**
 * 作业提交状态常量（统一收口魔法字符串）
 * <p>
 * 状态机：SUBMITTED(已提交) → GRADED(已批改) / RETURNED(已退回重做)；RESUBMITTED(重新提交)。
 * 收敛后杜绝散落字面量拼写不一致（如 "SUBMITTED"/"SUBMITTED " 混用导致统计遗漏）。
 */
public final class SubmissionStatus {

    private SubmissionStatus() {
    }

    /** 已提交（待批改） */
    public static final String SUBMITTED = "SUBMITTED";
    /** 已批改 */
    public static final String GRADED = "GRADED";
    /** 已退回（重做） */
    public static final String RETURNED = "RETURNED";
    /** 重新提交 */
    public static final String RESUBMITTED = "RESUBMITTED";

    /** 待批改（未批改、可重做）状态集合 */
    public static final Set<String> PENDING_GRADING_STATES = Set.of(SUBMITTED, RESUBMITTED);
    /** 已进入最终态（批改闭环）的状态集合 */
    public static final Set<String> FINAL_STATES = Set.of(GRADED, RETURNED);
}
