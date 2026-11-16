package com.aicourse.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 知识点掌握度出参。
 *
 * <p>掌握度按“已作答题目”的真实证据计算：考试会话的题目得分优先，其次使用错题本
 * 的已掌握状态；没有任何作答证据时返回 {@code answered=false}、{@code score=null}，
 * 不再把“未作答”当成 0 分或 100 分，也不再用前端占位文案掩盖契约错位。</p>
 */
@Data
public class MasteryVO {

    /** 掌握度百分比（0-100）；无任何作答证据时为 null */
    private BigDecimal score;

    /** 掌握等级：优秀 / 良好 / 合格 / 待巩固 / 未评估 */
    private String level;

    /** 是否已找到真实作答证据 */
    private boolean answered;

    /** 该知识点在当前课程题库中的关联题目总数 */
    private int totalQuestions;

    /** 有真实作答证据的题目数 */
    private int answeredQuestions;

    /** 完全答对的题目数（部分得分按未完全答对计） */
    private int correctQuestions;

    /** 未完全答对或答错的题目数 */
    private int wrongQuestions;

    /** 数据说明，供前端展示“为什么没有分数”或“基于多少题计算” */
    private String note;

    public MasteryVO() {
    }

    /**
     * 无作答证据时的真实空值结果。
     */
    public static MasteryVO unknown(int totalQuestions, String note) {
        MasteryVO vo = new MasteryVO();
        vo.setScore(null);
        vo.setLevel("未评估");
        vo.setAnswered(false);
        vo.setTotalQuestions(Math.max(totalQuestions, 0));
        vo.setAnsweredQuestions(0);
        vo.setCorrectQuestions(0);
        vo.setWrongQuestions(0);
        vo.setNote(note);
        return vo;
    }
}
