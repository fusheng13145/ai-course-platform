package com.aicourse.vo;

import com.aicourse.entity.WrongQuestion;
import lombok.Data;

import java.util.List;

/**
 * 错题「按知识点分组」出参。
 *
 * <p><b>修复背景（2026-09-23，P2-8）</b>：原实现返回 {@code Map<String, List<WrongQuestion>>}，
 * 且 Map 的键直接取 {@code question.knowledgePoints} 的<b>原始 JSON 文本</b>
 * （如 {@code ["Python基础"]}——带方括号与引号，非人可读，也无法携带知识点 ID）。</p>
 *
 * <p>而前端唯一消费方 {@code useWrongQuestions} 按
 * {@code Array<{knowledgePointId, knowledgePointName, questions}>} 消费：
 * {@code Array.isArray(Map)} 恒为 false，导致整段「按知识点补标签」增强逻辑
 * <b>静默跳过</b>（不报错、无效果）。本 VO 将结构显式化以对齐前端契约。</p>
 *
 * <p>说明：一道题可关联多个知识点，故会同时出现在多个分组中（符合「按知识点归类」语义）。</p>
 */
@Data
public class WrongQuestionGroupVO {

    /** 知识点ID；未能与 knowledge_points 记录匹配时为 null */
    private Long knowledgePointId;

    /** 知识点名称（已从 JSON 文本中解析出的人类可读名称） */
    private String knowledgePointName;

    /** 该分组下的错题数量 */
    private Integer count;

    /** 该分组下的错题列表 */
    private List<WrongQuestion> questions;

    public WrongQuestionGroupVO() {
    }

    public WrongQuestionGroupVO(Long knowledgePointId, String knowledgePointName,
                                List<WrongQuestion> questions) {
        this.knowledgePointId = knowledgePointId;
        this.knowledgePointName = knowledgePointName;
        this.questions = questions;
        this.count = questions == null ? 0 : questions.size();
    }
}
