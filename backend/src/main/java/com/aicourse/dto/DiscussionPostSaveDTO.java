package com.aicourse.dto;

import com.aicourse.entity.DiscussionPost;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 讨论回复入参（入参收实体加固）。
 *
 * <p><b>为什么不直接绑 {@code DiscussionPost} 实体</b>：{@code topicId}／{@code authorId}／
 * {@code authorRole}／{@code likeCount}／{@code floor}／{@code mentions} 由
 * {@code DiscussionServiceImpl#reply} 写入；{@code moderationStatus}／{@code moderationReason}／
 * {@code moderatedAt}／{@code moderatedBy} 属于审核链服务端字段——实体直绑等于允许发帖人自带
 * 「已审核／已豁免」结论，绕过内容审核。</p>
 */
@Schema(name = "DiscussionPostSaveDTO", description = "讨论回复入参；作者、楼层与审核字段不接受客户端传入")
@Data
public class DiscussionPostSaveDTO {

    @Schema(description = "回复内容；落库前做 HTML 转义")
    private String content;

    @Schema(description = "父回复ID（楼中楼，顶层回复留空）", example = "0")
    private Long parentId;

    @Schema(description = "@提及的用户ID列表；落库为逗号分隔字符串并发通知")
    private List<Long> mentionUserIds;

    public DiscussionPost toEntity() {
        DiscussionPost post = new DiscussionPost();
        applyTo(post);
        return post;
    }

    public void applyTo(DiscussionPost post) {
        post.setContent(content);
        post.setParentId(parentId);
        post.setMentionUserIds(mentionUserIds);
    }
}
