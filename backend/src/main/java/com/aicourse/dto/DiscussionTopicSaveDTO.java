package com.aicourse.dto;

import com.aicourse.entity.DiscussionTopic;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 讨论话题发布入参（入参收实体加固）。
 *
 * <p><b>为什么不绑 {@code DiscussionTopic} 实体</b>：{@code creatorId}/{@code creatorRole} 是身份字段、
 * {@code status}/{@code isPinned}/{@code isEssence}/{@code replyCount}/{@code lastReplyAt} 是状态与运营字段。
 * 服务层目前会把它们覆盖成服务端值，但依靠「服务层记得覆盖」来保证安全，等于把注入防护交给实现细节——
 * 一旦某天新增一条更新路径（例如草稿编辑），客户端就能直接写这些列。白名单入参让这层保护变成结构性约束。</p>
 */
@Schema(name = "DiscussionTopicSaveDTO", description = "讨论话题发布入参；身份/状态/运营字段不接受客户端传入")
@Data
public class DiscussionTopicSaveDTO {

    @Schema(description = "课程ID", example = "12")
    private Long courseId;

    @Schema(description = "章节ID（可选）", example = "3")
    private Long chapterId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文（HTML 会被转义）")
    private String content;

    @Schema(description = "话题类型，如 QUESTION/DISCUSSION", example = "DISCUSSION")
    private String type;

    @Schema(description = "限定教学班ID（可选，空表示全课程可见）")
    private Long teachingClassId;

    public DiscussionTopic toEntity() {
        DiscussionTopic topic = new DiscussionTopic();
        topic.setCourseId(courseId);
        topic.setChapterId(chapterId);
        topic.setTitle(title);
        topic.setContent(content);
        topic.setType(type);
        topic.setTeachingClassId(teachingClassId);
        return topic;
    }
}
