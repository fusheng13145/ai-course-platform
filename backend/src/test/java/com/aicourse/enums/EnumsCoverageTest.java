package com.aicourse.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 枚举覆盖率补测（A 速赢）。
 * 枚举此前 INSTRUCTION 覆盖率为 0%，本测试通过 values()/getDesc()/getByName() 将其拉满。
 */
class EnumsCoverageTest {

    @Test
    void approvalStatus() {
        for (ApprovalStatus e : ApprovalStatus.values()) {
            assertNotNull(e.getDesc());
            assertEquals(e, ApprovalStatus.getByName(e.name()));
        }
        assertNull(ApprovalStatus.getByName("__NOPE__"));
    }

    @Test
    void difficultyLevel() {
        for (DifficultyLevel e : DifficultyLevel.values()) {
            assertNotNull(e.getDesc());
            assertEquals(e, DifficultyLevel.getByName(e.name()));
        }
        assertNull(DifficultyLevel.getByName("__NOPE__"));
    }

    @Test
    void enrollmentType() {
        for (EnrollmentType e : EnrollmentType.values()) {
            assertNotNull(e.getDesc());
            assertEquals(e, EnrollmentType.getByName(e.name()));
        }
        assertNull(EnrollmentType.getByName("__NOPE__"));
    }

    @Test
    void examSessionStatus() {
        for (ExamSessionStatus e : ExamSessionStatus.values()) {
            assertNotNull(e.getDesc());
            assertEquals(e, ExamSessionStatus.getByName(e.name()));
        }
        assertNull(ExamSessionStatus.getByName("__NOPE__"));
    }

    @Test
    void examStatus() {
        for (ExamStatus e : ExamStatus.values()) {
            assertNotNull(e.getDesc());
            assertEquals(e, ExamStatus.getByName(e.name()));
        }
        assertNull(ExamStatus.getByName("__NOPE__"));
    }

    @Test
    void notificationCategory() {
        for (NotificationCategory e : NotificationCategory.values()) {
            assertNotNull(e.getDesc());
            assertEquals(e, NotificationCategory.getByName(e.name()));
        }
        assertNull(NotificationCategory.getByName("__NOPE__"));
    }

    @Test
    void questionType() {
        for (QuestionType e : QuestionType.values()) {
            assertNotNull(e.getDesc());
            assertEquals(e, QuestionType.getByName(e.name()));
        }
        assertNull(QuestionType.getByName("__NOPE__"));
    }

    @Test
    void relationType() {
        for (RelationType e : RelationType.values()) {
            assertNotNull(e.getDesc());
            assertEquals(e, RelationType.getByName(e.name()));
        }
        assertNull(RelationType.getByName("__NOPE__"));
    }

    @Test
    void submissionStatus() {
        for (SubmissionStatus e : SubmissionStatus.values()) {
            assertNotNull(e.getDesc());
            assertEquals(e, SubmissionStatus.getByName(e.name()));
        }
        assertNull(SubmissionStatus.getByName("__NOPE__"));
    }

    @Test
    void taskStatus() {
        for (TaskStatus e : TaskStatus.values()) {
            assertNotNull(e.getDesc());
            assertEquals(e, TaskStatus.getByName(e.name()));
        }
        assertNull(TaskStatus.getByName("__NOPE__"));
    }

    @Test
    void userRole() {
        for (UserRole e : UserRole.values()) {
            assertNotNull(e.getDesc());
            assertEquals(e, UserRole.getByName(e.name()));
        }
        assertNull(UserRole.getByName("__NOPE__"));
    }
}
