package com.aicourse.service.impl;

import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.DiscussionPostLike;
import com.aicourse.entity.DiscussionTopic;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.DiscussionPostLikeMapper;
import com.aicourse.mapper.DiscussionPostMapper;
import com.aicourse.mapper.DiscussionTopicMapper;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.NotificationService;
import com.aicourse.vo.TopicDetailVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DiscussionServiceImpl 覆盖率补测（I-04 第四批）。
 *
 * <p>覆盖 XSS 空值分支、列表筛选条件组合、楼层/提及/回复通知、课程 ID 查询、
 * 点赞计数空值、删除级联与管理权 fail-closed。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscussionServiceImplCoverageTest {

    @Mock private DiscussionTopicMapper topicMapper;
    @Mock private DiscussionPostMapper postMapper;
    @Mock private DiscussionPostLikeMapper postLikeMapper;
    @Mock private NotificationService notificationService;
    @Mock private CourseAccessControl courseAccessControl;
    @InjectMocks private DiscussionServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", topicMapper);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DiscussionPost.class);
        when(topicMapper.insert(any(DiscussionTopic.class))).thenReturn(1);
        when(topicMapper.updateById(any(DiscussionTopic.class))).thenReturn(1);
        when(topicMapper.deleteById(anyLong())).thenReturn(1);
        when(postMapper.insert(any(DiscussionPost.class))).thenReturn(1);
        when(postMapper.updateById(any(DiscussionPost.class))).thenReturn(1);
        when(postMapper.deleteById(anyLong())).thenReturn(1);
        when(postMapper.selectList(any())).thenReturn(List.of());
        when(postMapper.selectOne(any())).thenReturn(null);
    }

    private DiscussionTopic topic(Long id, String status) {
        DiscussionTopic topic = new DiscussionTopic();
        topic.setId(id);
        topic.setStatus(status);
        topic.setCreatorId(1L);
        topic.setCourseId(1L);
        topic.setReplyCount(0);
        return topic;
    }

    private DiscussionPost post(Long id) {
        DiscussionPost post = new DiscussionPost();
        post.setId(id);
        post.setTopicId(1L);
        post.setAuthorId(2L);
        post.setLikeCount(0);
        post.setFloor(1);
        return post;
    }

    private Authentication auth(Object principal) {
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    // ===================== 创建与列表 =====================

    @Test
    @DisplayName("创建讨论：标题/内容为 null 时不转义")
    void createTopic_nullTitleAndContent() {
        DiscussionTopic topic = topic(1L, DiscussionTopic.STATUS_OPEN);
        topic.setTitle(null);
        topic.setContent(null);

        DiscussionTopic saved = service.createTopic(topic, 1L, "STUDENT");

        assertNull(saved.getTitle());
        assertNull(saved.getContent());
        assertEquals(DiscussionTopic.STATUS_OPEN, saved.getStatus());
        verify(topicMapper).insert(topic);
    }

    @Test
    @DisplayName("列出讨论：四条件全关/空关键词/关键词命中，并处理 null 话题")
    void listTopics_filterBranchesAndNullOutput() {
        when(topicMapper.selectList(any())).thenAnswer(invocation -> {
            DiscussionTopic nullTitle = topic(1L, DiscussionTopic.STATUS_OPEN);
            nullTitle.setTitle(null);
            nullTitle.setContent(null);
            DiscussionTopic escaped = topic(2L, DiscussionTopic.STATUS_OPEN);
            escaped.setTitle("<b>标题</b>");
            escaped.setContent("<i>内容</i>");
            return Arrays.asList(null, nullTitle, escaped);
        });

        assertEquals(3, service.listTopics(null, null, null, null).size());
        assertEquals(3, service.listTopics(1L, 2L, " ", false).size());

        List<DiscussionTopic> hit = service.listTopics(1L, 2L, "标题", true);
        assertEquals("&lt;b&gt;标题&lt;/b&gt;", hit.get(2).getTitle());
        assertEquals("&lt;i&gt;内容&lt;/i&gt;", hit.get(2).getContent());
    }

    @Test
    @DisplayName("讨论详情：null 回复与 null 内容不抛异常")
    void getTopicDetail_nullPostAndContent() {
        when(topicMapper.selectById(1L)).thenReturn(topic(1L, DiscussionTopic.STATUS_OPEN));
        DiscussionPost noContent = post(2L);
        noContent.setContent(null);
        when(postMapper.selectList(any())).thenReturn(Arrays.asList(null, noContent));

        TopicDetailVO detail = service.getTopicDetail(1L);

        assertEquals(2, detail.getPosts().size());
    }

    @Test
    @DisplayName("讨论详情：回复内容非空时输出转义")
    void getTopicDetail_escapesPostContent() {
        when(topicMapper.selectById(1L)).thenReturn(topic(1L, DiscussionTopic.STATUS_OPEN));
        DiscussionPost content = post(2L);
        content.setContent("<b>回复</b>");
        when(postMapper.selectList(any())).thenReturn(List.of(content));

        TopicDetailVO detail = service.getTopicDetail(1L);

        assertEquals("&lt;b&gt;回复&lt;/b&gt;", detail.getPosts().get(0).getContent());
    }

    // ===================== 回复 =====================

    @Test
    @DisplayName("回复：话题不存在抛异常")
    void reply_topicNotFound() {
        when(topicMapper.selectById(9L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.reply(9L, new DiscussionPost(), 2L, "STUDENT"));
    }

    @Test
    @DisplayName("回复：楼层 null/有值、提及 null/空/含空元素/多人、回复数为 null")
    void reply_floorMentionsAndNullReplyCount() {
        DiscussionTopic nullCountTopic = topic(1L, DiscussionTopic.STATUS_OPEN);
        nullCountTopic.setReplyCount(null);
        nullCountTopic.setTitle(null);
        DiscussionPost floorNull = new DiscussionPost();
        floorNull.setFloor(null);
        DiscussionPost floorFive = new DiscussionPost();
        floorFive.setFloor(5);
        when(topicMapper.selectById(1L)).thenReturn(nullCountTopic);
        when(postMapper.selectOne(any())).thenReturn(null, floorNull, floorFive);

        DiscussionPost first = new DiscussionPost();
        first.setContent(null);
        first.setMentionUserIds(Arrays.asList(3L, null, 4L));
        DiscussionPost savedFirst = service.reply(1L, first, 2L, "STUDENT");
        assertEquals(1, savedFirst.getFloor());
        assertEquals("3,4", savedFirst.getMentions());

        DiscussionPost second = new DiscussionPost();
        second.setContent("x");
        second.setMentionUserIds(List.of());
        assertEquals(1, service.reply(1L, second, 2L, "STUDENT").getFloor());

        DiscussionPost third = new DiscussionPost();
        third.setContent("y");
        third.setMentionUserIds(null);
        assertEquals(6, service.reply(1L, third, 2L, "STUDENT").getFloor());
    }

    @Test
    @DisplayName("回复：发起人本人回复且无提及时不发通知")
    void reply_creatorSelfNoMentions() {
        DiscussionTopic topic = topic(1L, DiscussionTopic.STATUS_OPEN);
        topic.setCreatorId(2L);
        when(topicMapper.selectById(1L)).thenReturn(topic);

        DiscussionPost post = new DiscussionPost();
        post.setContent("self");
        service.reply(1L, post, 2L, "STUDENT");

        verify(notificationService, never()).onEvent(anyString(), anyMap());
    }

    @Test
    @DisplayName("回复：话题发起人 null、提及标题非空、跳过自己")
    void reply_nullCreatorMentionTitleAndSelfSkip() {
        DiscussionTopic topic = topic(1L, DiscussionTopic.STATUS_OPEN);
        topic.setCreatorId(null);
        topic.setTitle("标题");
        when(topicMapper.selectById(1L)).thenReturn(topic);

        DiscussionPost post = new DiscussionPost();
        post.setContent("mention");
        post.setMentionUserIds(Arrays.asList(3L, 2L));
        service.reply(1L, post, 2L, "STUDENT");

        verify(notificationService, times(1)).onEvent(eq("DISCUSSION_MENTIONED"), anyMap());
    }

    @Test
    @DisplayName("回复：楼中楼父回复 null/作者 null/作者本人/其他作者")
    void reply_parentNotificationBranches() {
        DiscussionTopic topic = topic(1L, DiscussionTopic.STATUS_OPEN);
        topic.setTitle("标题");
        when(topicMapper.selectById(1L)).thenReturn(topic);

        DiscussionPost parentNull = null;
        DiscussionPost parentNoAuthor = post(10L);
        parentNoAuthor.setAuthorId(null);
        DiscussionPost parentSelf = post(11L);
        parentSelf.setAuthorId(2L);
        DiscussionPost parentOther = post(12L);
        parentOther.setAuthorId(3L);
        when(postMapper.selectById(9L)).thenReturn(parentNull);
        when(postMapper.selectById(10L)).thenReturn(parentNoAuthor);
        when(postMapper.selectById(11L)).thenReturn(parentSelf);
        when(postMapper.selectById(12L)).thenReturn(parentOther);

        for (long parentId : new long[]{9L, 10L, 11L, 12L}) {
            DiscussionPost reply = new DiscussionPost();
            reply.setContent("child");
            reply.setParentId(parentId);
            service.reply(1L, reply, 2L, "STUDENT");
        }

        verify(notificationService, times(1)).onEvent(eq("DISCUSSION_REPLIED"), anyMap());
    }

    @Test
    @DisplayName("回复：通知异常不影响回复主流程")
    void reply_notificationFailuresAreSwallowed() {
        DiscussionTopic topic = topic(1L, DiscussionTopic.STATUS_OPEN);
        when(topicMapper.selectById(1L)).thenReturn(topic);
        doThrow(new RuntimeException("notify down"))
                .when(notificationService).onEvent(anyString(), anyMap());

        DiscussionPost post = new DiscussionPost();
        post.setContent("x");
        post.setMentionUserIds(List.of(3L));

        DiscussionPost saved = service.reply(1L, post, 2L, "STUDENT");

        assertEquals(1, saved.getFloor());
        verify(postMapper).insert(post);
    }

    // ===================== 课程 ID 与点赞 =====================

    @Test
    @DisplayName("回复所属课程：postId/回复/话题/课程逐级空值与命中")
    void courseIdOfPost_branches() {
        assertNull(service.courseIdOfPost(null));

        assertNull(service.courseIdOfPost(9L));

        DiscussionPost noTopic = post(10L);
        noTopic.setTopicId(null);
        when(postMapper.selectById(10L)).thenReturn(noTopic);
        assertNull(service.courseIdOfPost(10L));

        DiscussionPost topicMissing = post(11L);
        topicMissing.setTopicId(12L);
        when(postMapper.selectById(11L)).thenReturn(topicMissing);
        when(topicMapper.selectById(12L)).thenReturn(null);
        assertNull(service.courseIdOfPost(11L));

        DiscussionPost courseTopic = post(13L);
        courseTopic.setTopicId(14L);
        DiscussionTopic topic = topic(14L, DiscussionTopic.STATUS_OPEN);
        topic.setCourseId(15L);
        when(postMapper.selectById(13L)).thenReturn(courseTopic);
        when(topicMapper.selectById(14L)).thenReturn(topic);
        assertEquals(15L, service.courseIdOfPost(13L));
    }

    @Test
    @DisplayName("点赞：likeCount 为 null 时按 0 起算")
    void likePost_nullLikeCount() {
        DiscussionPost likedPost = post(1L);
        likedPost.setLikeCount(null);
        when(postMapper.selectById(1L)).thenReturn(likedPost);
        when(postLikeMapper.selectOne(any())).thenReturn(null);
        when(postLikeMapper.insert(any(DiscussionPostLike.class))).thenReturn(1);

        assertEquals(1, service.likePost(1L, 2L).getLikeCount());

        DiscussionPost unlikedPost = post(2L);
        unlikedPost.setLikeCount(null);
        when(postMapper.selectById(2L)).thenReturn(unlikedPost);
        DiscussionPostLike existing = new DiscussionPostLike();
        existing.setId(9L);
        when(postLikeMapper.selectOne(any())).thenReturn(existing);
        when(postLikeMapper.deleteById(9L)).thenReturn(1);

        assertEquals(0, service.likePost(2L, 2L).getLikeCount());
    }

    // ===================== 管理动作 =====================

    @Test
    @DisplayName("标记精华：话题不存在与无课程归属均拒绝")
    void markEssence_notFoundAndNoCourse() {
        when(topicMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.markEssence(9L, true, auth(1L)));

        DiscussionTopic publicTopic = topic(1L, DiscussionTopic.STATUS_OPEN);
        publicTopic.setCourseId(null);
        when(topicMapper.selectById(1L)).thenReturn(publicTopic);
        assertThrows(BusinessException.class, () -> service.markEssence(1L, true, auth(1L)));
        verify(courseAccessControl, never()).assertCanManageCourse(anyLong(), any());
    }

    @Test
    @DisplayName("删除回复：回复不存在、话题缺失、回复数 null、回复数 5")
    void deletePost_notFoundAndReplyCountBranches() {
        when(postMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.deletePost(9L, auth(2L)));

        DiscussionTopic noTopic = null;
        DiscussionTopic nullReplyCount = topic(1L, DiscussionTopic.STATUS_OPEN);
        nullReplyCount.setReplyCount(null);
        DiscussionTopic fiveReplies = topic(1L, DiscussionTopic.STATUS_OPEN);
        fiveReplies.setReplyCount(5);
        when(topicMapper.selectById(1L)).thenReturn(noTopic, nullReplyCount, fiveReplies);

        when(postMapper.selectById(1L)).thenReturn(post(1L));
        when(postMapper.selectById(2L)).thenReturn(post(2L));
        when(postMapper.selectById(3L)).thenReturn(post(3L));

        service.deletePost(1L, auth(2L));
        service.deletePost(2L, auth(2L));
        service.deletePost(3L, auth(2L));

        verify(topicMapper, times(1)).updateById(fiveReplies);
    }

    @Test
    @DisplayName("删除回复：他人回复且话题缺失时 fail-closed")
    void deletePost_otherAuthorNoTopicDenied() {
        when(postMapper.selectById(1L)).thenReturn(post(1L));
        when(topicMapper.selectById(1L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.deletePost(1L, auth(9L)));
        verify(postMapper, never()).deleteById(1L);
    }

    @Test
    @DisplayName("关闭话题：未认证主体按 UNAUTHORIZED 拒绝")
    void closeTopic_invalidPrincipal() {
        when(topicMapper.selectById(1L)).thenReturn(topic(1L, DiscussionTopic.STATUS_OPEN));

        assertThrows(BusinessException.class, () -> service.closeTopic(1L, null));
        assertThrows(BusinessException.class, () -> service.closeTopic(1L, auth("not-a-long")));
        verify(topicMapper, never()).updateById(any(DiscussionTopic.class));
    }
}
