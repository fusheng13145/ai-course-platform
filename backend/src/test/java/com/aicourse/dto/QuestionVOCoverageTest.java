package com.aicourse.dto;

import com.aicourse.entity.Question;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Coverage test for {@link QuestionVO}, the only DTO in the package that carries
 * non-trivial logic: the static {@link QuestionVO#from(Question)} factory maps a
 * difficulty {@code String} ("EASY"/"MEDIUM"/"HARD"/default) to an {@code Integer}
 * and parses an options JSON array into a {@code List<String>}.
 */
public class QuestionVOCoverageTest {

    private Question baseQuestion() {
        Question q = new Question();
        q.setId(1L);
        q.setCourseId(2L);
        q.setType("SINGLE_CHOICE");
        q.setContent("which is a primitive type?");
        q.setScore(BigDecimal.TEN);
        q.setCorrectRate(BigDecimal.valueOf(0.8));
        q.setUsageCount(7);
        return q;
    }

    @Test
    void fromNullReturnsNull() {
        assertNull(QuestionVO.from(null));
    }

    @Test
    void fromEasyDifficulty() {
        Question q = baseQuestion();
        q.setDifficulty("EASY");
        q.setOptions("[\"A\",\"B\",\"C\"]");
        QuestionVO vo = QuestionVO.from(q);
        assertNotNull(vo);
        assertEquals(1, vo.getDifficulty());
        assertEquals(List.of("A", "B", "C"), vo.getOptions());
        assertGetters(vo);
    }

    @Test
    void fromMediumDifficulty() {
        Question q = baseQuestion();
        q.setDifficulty("MEDIUM");
        q.setOptions("[\"x\"]");
        QuestionVO vo = QuestionVO.from(q);
        assertEquals(3, vo.getDifficulty());
        assertEquals(List.of("x"), vo.getOptions());
        assertGetters(vo);
    }

    @Test
    void fromHardDifficulty() {
        Question q = baseQuestion();
        q.setDifficulty("HARD");
        QuestionVO vo = QuestionVO.from(q);
        assertEquals(5, vo.getDifficulty());
        assertNull(vo.getOptions());
        assertGetters(vo);
    }

    @Test
    void fromUnknownDifficultyDefaultsToMedium() {
        Question q = baseQuestion();
        q.setDifficulty("SUPER_HARD");
        QuestionVO vo = QuestionVO.from(q);
        assertEquals(3, vo.getDifficulty());
        assertGetters(vo);
    }

    @Test
    void fromNullDifficultyLeavesNull() {
        Question q = baseQuestion();
        q.setDifficulty(null);
        QuestionVO vo = QuestionVO.from(q);
        assertNull(vo.getDifficulty());
        assertGetters(vo);
    }

    @Test
    void fromNonJsonOptionsLeavesNull() {
        Question q = baseQuestion();
        q.setDifficulty("EASY");
        q.setOptions("not-a-json-array");
        QuestionVO vo = QuestionVO.from(q);
        assertEquals(1, vo.getDifficulty());
        assertNull(vo.getOptions());
        assertGetters(vo);
    }

    @Test
    void fromEmptyOptionsArray() {
        Question q = baseQuestion();
        q.setDifficulty("MEDIUM");
        q.setOptions("[]");
        QuestionVO vo = QuestionVO.from(q);
        assertEquals(3, vo.getDifficulty());
        assertNotNull(vo.getOptions());
        assertGetters(vo);
    }

    /** Exercise every getter so the VO's accessors are fully covered. */
    private void assertGetters(QuestionVO vo) {
        vo.getId();
        vo.getCourseId();
        vo.getType();
        vo.getContent();
        vo.getOptions();
        vo.getAnswer();
        vo.getScore();
        vo.getDifficulty();
        vo.getAnalysis();
        vo.getKnowledgePointIds();
        vo.getTags();
        vo.getSource();
        vo.getCreatedAt();
        vo.getUsageCount();
        vo.getCorrectRate();
        vo.getCreatorName();
    }
}
