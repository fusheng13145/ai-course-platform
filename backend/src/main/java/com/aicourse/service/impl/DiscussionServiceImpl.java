package com.aicourse.service.impl;

import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.DiscussionPostLike;
import com.aicourse.entity.DiscussionTopic;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.DiscussionPostLikeMapper;
import com.aicourse.mapper.DiscussionPostMapper;
import com.aicourse.mapper.DiscussionTopicMapper;
import com.aicourse.service.DiscussionService;
import com.aicourse.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import com.aicourse.common.RoleConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 课程讨论服务实现
 */
@Service
public class DiscussionServiceImpl extends ServiceImpl<DiscussionTopicMapper, DiscussionTopic> implements DiscussionService {

    private static final Logger log = LoggerFactory.getLogger(DiscussionServiceImpl.class);

    private final DiscussionTopicMapper topicMapper;
    private final DiscussionPostMapper postMapper;
    private final DiscussionPostLikeMapper postLikeMapper;
    private final NotificationService notificationService;

    public DiscussionServiceImpl(DiscussionTopicMapper topicMapper, DiscussionPostMapper postMapper,
                                 DiscussionPostLikeMapper postLikeMapper, NotificationService notificationService) {
        this.topicMapper = topicMapper;
        this.postMapper = postMapper;
        this.postLikeMapper = postLikeMapper;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionTopic createTopic(DiscussionTopic topic, Long userId, String role) {
        // XSS防护：对用户输入的标题和内容进行HTML转义
        if (topic.getTitle() != null) {
            topic.setTitle(HtmlUtils.htmlEscape(topic.getTitle()));
        }
        if (topic.getContent() != null) {
            topic.setContent(HtmlUtils.htmlEscape(topic.getContent()));
        }
        topic.setCreatorId(userId);
        topic.setCreatorRole(role);
        topic.setStatus(DiscussionTopic.STATUS_OPEN);
        topic.setIsPinned(false);
        topic.setReplyCount(0);
        this.save(topic);
        return topic;
    }

    @Override
    public List<DiscussionTopic> listTopics(Long courseId, Long chapterId, String keyword, Boolean essence) {
        LambdaQueryWrapper<DiscussionTopic> qw = new LambdaQueryWrapper<DiscussionTopic>()
                .eq(courseId != null, DiscussionTopic::getCourseId, courseId)
                .eq(chapterId != null, DiscussionTopic::getChapterId, chapterId)
                .like(keyword != null && !keyword.isBlank(), DiscussionTopic::getTitle, keyword)
                .eq(essence != null, DiscussionTopic::getIsEssence, essence)
                .orderByDesc(DiscussionTopic::getIsPinned)
                .orderByDesc(DiscussionTopic::getIsEssence)
                .orderByDesc(DiscussionTopic::getLastReplyAt)
                .orderByDesc(DiscussionTopic::getCreatedAt);
        List<DiscussionTopic> topics = this.list(qw);
        // XSS输出编码
        for (DiscussionTopic topic : topics) {
            encodeTopicOutput(topic);
        }
        return topics;
    }

    /**
     * 对讨论话题列表中的标题和内容进行XSS输出编码
     */
    private void encodeTopicOutput(DiscussionTopic topic) {
        if (topic == null) return;
        if (topic.getTitle() != null) {
            topic.setTitle(HtmlUtils.htmlEscape(topic.getTitle()));
        }
        if (topic.getContent() != null) {
            topic.setContent(HtmlUtils.htmlEscape(topic.getContent()));
        }
    }

    /**
     * 对讨论回复内容进行XSS输出编码
     */
    private void encodePostOutput(DiscussionPost post) {
        if (post == null) return;
        if (post.getContent() != null) {
            post.setContent(HtmlUtils.htmlEscape(post.getContent()));
        }
    }

    @Override
    public Map<String, Object> getTopicDetail(Long id) {
        DiscussionTopic topic = this.getById(id);
        if (topic == null) {
            throw new BusinessException("话题不存在");
        }
        // XSS输出编码
        encodeTopicOutput(topic);
        LambdaQueryWrapper<DiscussionPost> qw = new LambdaQueryWrapper<DiscussionPost>()
                .eq(DiscussionPost::getTopicId, id)
                .orderByAsc(DiscussionPost::getFloor)
                .orderByAsc(DiscussionPost::getCreatedAt);
        List<DiscussionPost> posts = postMapper.selectList(qw);
        // XSS输出编码：对回复内容进行转义
        for (DiscussionPost post : posts) {
            encodePostOutput(post);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("topic", topic);
        res.put("posts", posts);
        res.put("replyCount", posts.size());
        return res;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionPost reply(Long topicId, DiscussionPost post, Long userId, String role) {
        DiscussionTopic topic = this.getById(topicId);
        if (topic == null) {
            throw new BusinessException("话题不存在");
        }
        if (DiscussionTopic.STATUS_CLOSED.equals(topic.getStatus())) {
            throw new BusinessException("话题已关闭，无法回复");
        }
        post.setTopicId(topicId);
        post.setAuthorId(userId);
        post.setAuthorRole(role);
        post.setLikeCount(0);
        // XSS防护：对回复内容进行HTML转义
        if (post.getContent() != null) {
            post.setContent(HtmlUtils.htmlEscape(post.getContent()));
        }
        // 楼层号：按话题内现有回复的最大楼层 +1（对标超星楼层顺序）
        DiscussionPost maxFloorRec = postMapper.selectOne(new LambdaQueryWrapper<DiscussionPost>()
                .select(DiscussionPost::getFloor)
                .eq(DiscussionPost::getTopicId, topicId)
                .orderByDesc(DiscussionPost::getFloor)
                .last("LIMIT 1"));
        int floor = (maxFloorRec != null && maxFloorRec.getFloor() != null ? maxFloorRec.getFloor() : 0) + 1;
        post.setFloor(floor);
        // @提及：落库为逗号分隔的用户ID列表，供前端渲染与通知
        if (post.getMentionUserIds() != null && !post.getMentionUserIds().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < post.getMentionUserIds().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(post.getMentionUserIds().get(i));
            }
            post.setMentions(sb.toString());
        }
        postMapper.insert(post);
        topic.setReplyCount((topic.getReplyCount() == null ? 0 : topic.getReplyCount()) + 1);
        topic.setLastReplyAt(LocalDateTime.now());
        this.updateById(topic);

        // 修复 P1-5：回复后通知相关人——被回复者（楼中楼）与话题发起者（顶层回复）。
        try {
            notifyRepliedUsers(topic, post, userId);
        } catch (RuntimeException ignore) {
            // 通知失败不影响回复主流程
            log.warn("回复后通知相关人失败: topicId={}, postId={}", topicId, post.getId(), ignore);
        }
        // 讨论区进阶（#70）：@提及通知被提及用户
        try {
            notifyMentionedUsers(topic, post, userId);
        } catch (RuntimeException ignore) {
            // 通知失败不影响回复主流程
            log.warn("@提及通知失败: topicId={}, postId={}", topicId, post.getId(), ignore);
        }
        return post;
    }

    /**
     * @提及通知：将回复中 @ 的用户（来自 mentionUserIds，落库于 mentions 列）逐一通知（跳过自己）。
     */
    private void notifyMentionedUsers(DiscussionTopic topic, DiscussionPost post, Long userId) {
        if (post.getMentionUserIds() == null || post.getMentionUserIds().isEmpty()) return;
        String topicTitle = topic.getTitle() != null ? topic.getTitle() : "讨论";
        for (Long mentionedId : post.getMentionUserIds()) {
            if (mentionedId == null || mentionedId.equals(userId)) continue;
            Map<String, Object> eventData = new LinkedHashMap<>();
            eventData.put("recipientId", mentionedId);
            eventData.put("topicId", topic.getId());
            eventData.put("topicTitle", topicTitle);
            eventData.put("postId", post.getId());
            eventData.put("mentionerId", userId);
            notificationService.onEvent("DISCUSSION_MENTIONED", eventData);
        }
    }

    /**
     * 回复通知相关人：楼中楼回复通知其父回复作者；顶层回复通知话题发起者。
     * 跳过自己与被重复通知者。
     */
    private void notifyRepliedUsers(DiscussionTopic topic, DiscussionPost post, Long userId) {
        Set<Long> recipients = new HashSet<>();
        if (post.getParentId() != null) {
            DiscussionPost parent = postMapper.selectById(post.getParentId());
            if (parent != null && parent.getAuthorId() != null && !parent.getAuthorId().equals(userId)) {
                recipients.add(parent.getAuthorId());
            }
        } else if (topic.getCreatorId() != null && !topic.getCreatorId().equals(userId)) {
            recipients.add(topic.getCreatorId());
        }
        if (recipients.isEmpty()) return;

        String topicTitle = topic.getTitle() != null ? topic.getTitle() : "讨论";
        for (Long recipientId : recipients) {
            Map<String, Object> eventData = new LinkedHashMap<>();
            eventData.put("recipientId", recipientId);
            eventData.put("topicId", topic.getId());
            eventData.put("topicTitle", topicTitle);
            eventData.put("postId", post.getId());
            eventData.put("replierId", userId);
            notificationService.onEvent("DISCUSSION_REPLIED", eventData);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> likePost(Long postId, Long userId) {
        DiscussionPost post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("回复不存在");
        }
        // 修复 P1-5：按用户去重——已赞则取消（点赞数-1），未赞则点赞（点赞数+1）。
        LambdaQueryWrapper<DiscussionPostLike> likeWrapper = new LambdaQueryWrapper<DiscussionPostLike>()
                .eq(DiscussionPostLike::getPostId, postId)
                .eq(DiscussionPostLike::getUserId, userId);
        DiscussionPostLike existing = postLikeMapper.selectOne(likeWrapper);
        boolean liked;
        if (existing != null) {
            postLikeMapper.deleteById(existing.getId());
            post.setLikeCount(Math.max(0, (post.getLikeCount() == null ? 0 : post.getLikeCount()) - 1));
            liked = false;
        } else {
            DiscussionPostLike like = new DiscussionPostLike();
            like.setPostId(postId);
            like.setUserId(userId);
            postLikeMapper.insert(like);
            post.setLikeCount((post.getLikeCount() == null ? 0 : post.getLikeCount()) + 1);
            liked = true;
        }
        postMapper.updateById(post);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("likeCount", post.getLikeCount());
        result.put("liked", liked);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionTopic closeTopic(Long id, Long userId, String role) {
        DiscussionTopic topic = requireOwner(id, userId, role);
        topic.setStatus(DiscussionTopic.STATUS_CLOSED);
        this.updateById(topic);
        return topic;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionTopic pinTopic(Long id, boolean pinned, Long userId, String role) {
        DiscussionTopic topic = requireOwner(id, userId, role);
        topic.setIsPinned(pinned);
        this.updateById(topic);
        return topic;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionTopic markEssence(Long id, boolean essence, Long userId, String role) {
        // 精华仅教师/管理员可标记（学生无权限）
        DiscussionTopic topic = this.getById(id);
        if (topic == null) {
            throw new BusinessException("话题不存在");
        }
        boolean isTeacherOrAdmin = RoleConstants.TEACHER.equals(role) || RoleConstants.ADMIN.equals(role);
        if (!isTeacherOrAdmin) {
            throw new BusinessException("仅教师或管理员可标记精华");
        }
        topic.setIsEssence(essence);
        this.updateById(topic);
        return topic;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long postId, Long userId, String role) {
        DiscussionPost post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("回复不存在");
        }
        boolean isAdmin = RoleConstants.ADMIN.equals(role);
        if (!isAdmin && !userId.equals(post.getAuthorId())) {
            throw new BusinessException("无权删除该回复");
        }
        postMapper.deleteById(postId);
        DiscussionTopic topic = this.getById(post.getTopicId());
        if (topic != null && topic.getReplyCount() != null && topic.getReplyCount() > 0) {
            topic.setReplyCount(topic.getReplyCount() - 1);
            this.updateById(topic);
        }
    }

    private DiscussionTopic requireOwner(Long id, Long userId, String role) {
        DiscussionTopic topic = this.getById(id);
        if (topic == null) {
            throw new BusinessException("话题不存在");
        }
        boolean isAdmin = RoleConstants.ADMIN.equals(role);
        if (!isAdmin && !userId.equals(topic.getCreatorId())) {
            throw new BusinessException("无权操作该话题");
        }
        return topic;
    }
}
