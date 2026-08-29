package com.aicourse.constant;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 常量类覆盖率补测（A 速赢）。
 * 常量类此前 INSTRUCTION 覆盖率为 0%，引用静态字段触发类初始化（含 Set.of 调用）即可拉满。
 */
class ConstantsCoverageTest {

    @Test
    void examSessionStatus() {
        assertNotNull(ExamSessionStatus.STARTED);
        assertNotNull(ExamSessionStatus.SUBMITTED);
        assertNotNull(ExamSessionStatus.AUTO_SUBMITTED);
        assertNotNull(ExamSessionStatus.GRADED);
        assertNotNull(ExamSessionStatus.FORCE_SUBMITTED);
        assertNotNull(ExamSessionStatus.TIMEOUT);
        Set<String> s = ExamSessionStatus.SUBMITTED_STATES;
        assertNotNull(s);
        assertTrue(s.contains("STARTED") || s.contains("SUBMITTED"));
    }

    @Test
    void submissionStatus() {
        assertNotNull(SubmissionStatus.SUBMITTED);
        assertNotNull(SubmissionStatus.GRADED);
        assertNotNull(SubmissionStatus.RETURNED);
        assertNotNull(SubmissionStatus.RESUBMITTED);
        assertTrue(SubmissionStatus.PENDING_GRADING_STATES.contains("SUBMITTED"));
        assertTrue(SubmissionStatus.FINAL_STATES.contains("GRADED"));
    }
}
