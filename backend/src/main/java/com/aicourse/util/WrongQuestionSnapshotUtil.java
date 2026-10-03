package com.aicourse.util;

import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 错题本「联表派生展示字段」填充工具。
 *
 * <p><b>修复背景（2026-09-23，P2-6）</b>：{@code WrongQuestion} 中的
 * {@code questionContent} / {@code questionOptions} / {@code correctAnswer} /
 * {@code questionAnalysis} / {@code knowledgePointName} 五个字段均标注
 * {@code @TableField(exist = false)}，实体注释写明其定位是
 * 「联表派生的展示字段（不入库，仅用于错题本列表展示题干/答案/解析）」。</p>
 *
 * <p>但<b>查询路径从未实现该派生填充</b>——字段恒为 null，导致错题本列表题干与正确答案为空
 * （前端 {@code useWrongQuestions} 依赖 {@code questionContent} 渲染，实测题干空白）。
 * 现由以下位置调用：</p>
 * <ul>
 *   <li>{@code WrongQuestionServiceImpl#getWrongQuestionBook} / {@code #getUnmastered}
 *       —— 查询后补全（<b>主要路径</b>）</li>
 *   <li>{@code ExamSessionGradingService#saveWrongQuestion} 与
 *       {@code WrongQuestionServiceImpl#recordWrongAnswer} —— 记录后返回给调用方时带上</li>
 * </ul>
 *
 * <p>注意：这些字段<b>不落库</b>，因此每次读取都会按当前题库内容实时派生；
 * 若题目被删除则派生值为 null（前端需自行兜底显示）。</p>
 */
public final class WrongQuestionSnapshotUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WrongQuestionSnapshotUtil() {
    }

    /**
     * 依据题库中的题目回填快照字段；题目为空时保持原样（不抛异常，不阻断错题记录）。
     */
    public static void fillFromQuestion(WrongQuestion target, Question source) {
        if (target == null || source == null) {
            return;
        }
        target.setQuestionContent(source.getContent());
        target.setQuestionOptions(source.getOptions());
        target.setCorrectAnswer(source.getCorrectAnswers());
        target.setQuestionAnalysis(source.getExplanation());
        target.setKnowledgePointName(firstKnowledgePointName(source.getKnowledgePoints()));
    }

    /**
     * 从 {@code knowledge_points} 取首个知识点名称。
     *
     * <p>该列为 JSON 数组字符串（如 {@code ["函数定义与调用"]}）；为兼容历史裸文本数据
     * （如 {@code 计算机网络基础} 或 {@code a,b}），非 JSON 时按逗号取首段。</p>
     */
    static String firstKnowledgePointName(String knowledgePoints) {
        if (knowledgePoints == null || knowledgePoints.isBlank()) {
            return null;
        }
        String s = knowledgePoints.trim();
        try {
            JsonNode node = MAPPER.readTree(s);
            if (node.isArray() && !node.isEmpty()) {
                JsonNode first = node.get(0);
                return first.isTextual() ? first.asText() : first.toString();
            }
            if (node.isTextual()) {
                return node.asText();
            }
        } catch (Exception notJson) {
            int idx = s.indexOf(',');
            return (idx > 0 ? s.substring(0, idx) : s).trim();
        }
        return null;
    }
}
