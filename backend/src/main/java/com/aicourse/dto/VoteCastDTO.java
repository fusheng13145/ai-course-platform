package com.aicourse.dto;

import com.aicourse.entity.VoteRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 投票提交入参（入参收实体加固）。
 *
 * <p><b>这处是本批次里唯一有真实越权面的</b>：{@code VoteRecord} 上的 {@code studentId}（投票人）、
 * {@code voteId}（投的是哪场）、{@code ip}/{@code userAgent}（防代投留痕）原本都可从请求体传入。
 * 服务层对 {@code voteId}/{@code studentId}/{@code ip}/{@code ua} 有覆盖，但一旦某条路径漏覆盖，
 * 就能伪造他人投票或伪造 IP。白名单后，请求体只剩选项本身。</p>
 */
@Schema(name = "VoteCastDTO", description = "投票提交入参；投票人/IP/UA 由服务端填充")
@Data
public class VoteCastDTO {

    @Schema(description = "选择的选项标识（单/多选）", example = "A")
    private String choice;

    @Schema(description = "文字作答（TEXT 模式）")
    private String textAnswer;

    public VoteRecord toEntity() {
        VoteRecord record = new VoteRecord();
        record.setChoice(choice);
        record.setTextAnswer(textAnswer);
        return record;
    }
}
