package com.aicourse.dto;

import com.aicourse.entity.Vote;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发起投票入参（入参收实体加固）。
 *
 * <p>{@code creatorId}/{@code status}/{@code startedAt}/{@code closedAt} 由服务端写入；
 * 实体直绑时客户端可自带创建者与状态，投票归属与「未发布即进行中」的异常状态都能被构造出来。</p>
 */
@Schema(name = "VoteSaveDTO", description = "发起投票入参；创建者与状态由服务端写入")
@Data
public class VoteSaveDTO {

    @Schema(description = "课程ID", example = "12")
    private Long courseId;

    @Schema(description = "章节ID（可选）")
    private Long chapterId;

    @Schema(description = "投票标题", example = "下次上机时间")
    @NotBlank(message = "投票标题不能为空")
    private String title;

    @Schema(description = "投票说明")
    private String description;

    @Schema(description = "投票模式：SINGLE/MULTI/TEXT", example = "SINGLE")
    private String voteMode;

    @Schema(description = "选项配置（JSON 字符串）")
    private String options;

    public Vote toEntity() {
        Vote vote = new Vote();
        vote.setCourseId(courseId);
        vote.setChapterId(chapterId);
        vote.setTitle(title);
        vote.setDescription(description);
        vote.setVoteMode(voteMode);
        vote.setOptions(options);
        return vote;
    }
}
