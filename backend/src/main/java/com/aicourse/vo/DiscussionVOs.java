package com.aicourse.vo;

import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.DiscussionTopic;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 讨论区出参：主题 / 楼层回复。
 *
 * <p>字段与实体一一对应、零删减。`DiscussionPost` 的审核字段
 * （`moderationStatus` / `moderationReason` / `moderatedAt` / `moderatedBy`）是治理痕迹，
 * 改动前就对所有人可见，本批不做收紧——收紧属产品决策，需单独确认。
 */
public final class DiscussionVOs {

    private DiscussionVOs() {
    }

    /** 讨论主题出参。 */
    @Data
    public static class TopicVO {
        private Long id;
        private Long courseId;
        private Long chapterId;
        private String title;
        private String content;
        private String type;
        private String status;
        private Long creatorId;
        private String creatorRole;
        private Boolean isPinned;
        private Boolean isEssence;
        private Integer replyCount;
        private LocalDateTime lastReplyAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Long teachingClassId;

        public static TopicVO from(DiscussionTopic topic) {
            if (topic == null) {
                return null;
            }
            TopicVO vo = new TopicVO();
            vo.setId(topic.getId());
            vo.setCourseId(topic.getCourseId());
            vo.setChapterId(topic.getChapterId());
            vo.setTitle(topic.getTitle());
            vo.setContent(topic.getContent());
            vo.setType(topic.getType());
            vo.setStatus(topic.getStatus());
            vo.setCreatorId(topic.getCreatorId());
            vo.setCreatorRole(topic.getCreatorRole());
            vo.setIsPinned(topic.getIsPinned());
            vo.setIsEssence(topic.getIsEssence());
            vo.setReplyCount(topic.getReplyCount());
            vo.setLastReplyAt(topic.getLastReplyAt());
            vo.setCreatedAt(topic.getCreatedAt());
            vo.setUpdatedAt(topic.getUpdatedAt());
            vo.setTeachingClassId(topic.getTeachingClassId());
            return vo;
        }

        public static List<TopicVO> from(List<DiscussionTopic> topics) {
            return topics == null ? List.of() : topics.stream().map(TopicVO::from).toList();
        }
    }

    /** 讨论回复出参。 */
    @Data
    public static class PostVO {
        private Long id;
        private Long topicId;
        private Long parentId;
        private Long authorId;
        private String authorRole;
        private String content;
        private Integer likeCount;
        private Integer floor;
        private String mentions;
        /** 非表字段：解析后的被 @ 用户 ID 列表。 */
        private List<Long> mentionUserIds;
        private String moderationStatus;
        private String moderationReason;
        private LocalDateTime moderatedAt;
        private Long moderatedBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static PostVO from(DiscussionPost post) {
            if (post == null) {
                return null;
            }
            PostVO vo = new PostVO();
            vo.setId(post.getId());
            vo.setTopicId(post.getTopicId());
            vo.setParentId(post.getParentId());
            vo.setAuthorId(post.getAuthorId());
            vo.setAuthorRole(post.getAuthorRole());
            vo.setContent(post.getContent());
            vo.setLikeCount(post.getLikeCount());
            vo.setFloor(post.getFloor());
            vo.setMentions(post.getMentions());
            vo.setMentionUserIds(post.getMentionUserIds());
            vo.setModerationStatus(post.getModerationStatus());
            vo.setModerationReason(post.getModerationReason());
            vo.setModeratedAt(post.getModeratedAt());
            vo.setModeratedBy(post.getModeratedBy());
            vo.setCreatedAt(post.getCreatedAt());
            vo.setUpdatedAt(post.getUpdatedAt());
            return vo;
        }
    }
}
