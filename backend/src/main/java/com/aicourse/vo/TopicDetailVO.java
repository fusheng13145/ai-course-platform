package com.aicourse.vo;

import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.DiscussionTopic;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 讨论话题详情出参（话题 + 全部回复 + 回复数）。
 * 字段名与历史 Map 键一致（topic / posts / replyCount），保证前端零改动。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TopicDetailVO {

    private DiscussionTopic topic;
    private List<DiscussionPost> posts;
    private Integer replyCount;

    public TopicDetailVO() {
    }

    public TopicDetailVO(DiscussionTopic topic, List<DiscussionPost> posts, Integer replyCount) {
        this.topic = topic;
        this.posts = posts;
        this.replyCount = replyCount;
    }
}
