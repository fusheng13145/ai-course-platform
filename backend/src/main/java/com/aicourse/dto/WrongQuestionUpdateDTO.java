package com.aicourse.dto;

import com.aicourse.entity.WrongQuestion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 错题本修正入参（入参收实体加固）。
 *
 * <p>控制器本来就只取 {@code reason}/{@code aiAnalysis}/{@code knowledgePointName} 三个字段做白名单更新，
 * 但「先收实体再手工挑字段」的写法有两个问题：请求体里塞 {@code studentId}/{@code courseId}/{@code mastered}
 * 不会报错、只是静默丢弃（客户端与后端对契约的理解不一致），以及任何人改动控制器时都可能顺手多拷一列。
 * 改成 DTO 后，契约与实现是同一份字段清单。</p>
 */
@Schema(name = "WrongQuestionUpdateDTO", description = "错题修正入参；学生只能修正错因/AI分析/知识点名称")
@Data
public class WrongQuestionUpdateDTO {

    @Schema(description = "错因描述")
    private String reason;

    @Schema(description = "AI 分析结论")
    private String aiAnalysis;

    @Schema(description = "知识点名称", example = "二叉树的遍历")
    private String knowledgePointName;

    public WrongQuestion toEntity(Long id) {
        WrongQuestion update = new WrongQuestion();
        update.setId(id);
        update.setReason(reason);
        update.setAiAnalysis(aiAnalysis);
        update.setKnowledgePointName(knowledgePointName);
        return update;
    }
}
