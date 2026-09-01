package com.aicourse.service.impl;

import com.aicourse.common.RoleConstants;
import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.DiscussionPostLike;
import com.aicourse.entity.DiscussionTopic;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.DiscussionPostLikeMapper;
import com.aicourse.mapper.DiscussionPostMapper;
import com.aicourse.mapper.DiscussionTopicMapper;
import com.aicourse.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DiscussionServiceImpl 指令覆盖率强化测试。
 * 覆盖创建/查询/回复/点赞/关闭/置顶/精华/删除及权限校验、XSS 编码、@提及通知全部分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscussionServiceImplBoostTest {

    @Mock
    private DiscussionTopicMapper topicMapper;
    @Mock
    private DiscussionPostMapper postMapper;
    @Mock
    private DiscussionPostLikeMapper postLikeMapper;
    @Mock
    private NotificationService notificationService;

    private DiscussionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DiscussionServiceImpl(topicMapper, postMapper, postLikeMapper, notificationService);
        ReflectionTestUtils.setField(service, "baseMapper", topicMapper);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), DiscussionTopic.class);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), DiscussionPost.class);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), DiscussionPostLike.class);
    }

    // ==================== createTopic ====================

    @Test
    @DisplayName("createTopic: 标题/内容转义后保存")
    void createTopic_withContent() {
        DiscussionTopic t = new DiscussionTopic();
        t.setTitle("<b>标题</b>");
        t.setContent("<script>内容</script>");
        DiscussionTopic r = service.createTopic(t, 7L, RoleConstants.STUDENT);
        assertEquals(DiscussionTopic.STATUS_OPEN, r.getStatus());
        assertFalse(r.getIsPinned());
        assertEquals(0, r.getReplyCount());
        assertEquals(7L, r.getCreatorId());
        assertTrue(r.getTitle().contains("&lt;"));
        verify(topicMapper).insert(any(DiscussionTopic.class));
    }

    @Test
    @DisplayName("createTopic: 标题/内容为空不转义")
    void createTopic_nullContent() {
        DiscussionTopic t = new DiscussionTopic();
        t.setTitle(null);
        t.setContent(null);
        DiscussionTopic r = service.createTopic(t, 7L, RoleConstants.STUDENT);
        assertNull(r.getTitle());
        verify(topicMapper).insert(any(DiscussionTopic.class));
    }

    // ==================== listTopics ====================

    @Test
    @DisplayName("listTopics: 无筛选条件")
    void listTopics_noFilter() {
        DiscussionTopic t = new DiscussionTopic();
        t.setTitle("标题");
        when(topicMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(t, null));
        List<DiscussionTopic> list = service.listTopics(null, null, null, null);
        assertEquals(2, list.size());
        assertTrue(list.get(0).getTitle().contains("标题")); // 转义覆盖
    }

    @Test
    @DisplayName("listTopics: 带课程/章节/关键词/精华筛选")
    void listTopics_withFilter() {
        DiscussionTopic t = new DiscussionTopic();
        t.setTitle(null);
        when(topicMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(t));
        List<DiscussionTopic> list = service.listTopics(1L, 2L, "关键词", true);
        assertEquals(1, list.size());
    }

    // ==================== getTopicDetail ====================

    @Test
    @DisplayName("getTopicDetail: 话题不存在抛异常")
    void getTopicDetail_notFound() {
        when(topicMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getTopicDetail(1L));
        assertTrue(ex.getMessage().contains("话题不存在"));
    }

    @Test
    @DisplayName("getTopicDetail: 返回话题与回复，含 XSS 编码（含 null 回复分支）")
    void getTopicDetail_success() {
        DiscussionTopic t = new DiscussionTopic();
        t.setId(1L);
        t.setTitle("标题");
        when(topicMapper.selectById(1L)).thenReturn(t);

        DiscussionPost p1 = new DiscussionPost();
        p1.setContent("<script>内容</script>");
        DiscussionPost p2 = new DiscussionPost();
        p2.setContent(null);
        when(postMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(p1, p2, null));

        com.aicourse.vo.TopicDetailVO res = service.getTopicDetail(1L);
        assertEquals(t, res.getTopic());
        assertEquals(3, res.getReplyCount());
        assertTrue(res.getPosts().get(0).getContent().contains("&lt;"));
    }

    // ==================== reply ====================

    @Test
    @DisplayName("reply: 话题不存在抛异常")
    void reply_topicNotFound() {
        when(topicMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reply(1L, new DiscussionPost(), 7L, RoleConstants.STUDENT));
        assertTrue(ex.getMessage().contains("话题不存在"));
    }

    @Test
    @DisplayName("reply: 话题已关闭抛异常")
    void reply_topicClosed() {
        DiscussionTopic t = new DiscussionTopic();
        t.setId(1L);
        t.setStatus(DiscussionTopic.STATUS_CLOSED);
        when(topicMapper.selectById(1L)).thenReturn(t);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reply(1L, new DiscussionPost(), 7L, RoleConstants.STUDENT));
        assertTrue(ex.getMessage().contains("话题已关闭"));
    }

    @Test
    @DisplayName("reply: 顶层回复+@提及，通知发起者与提及者（含跳过自己/空id）")
    void reply_topLevel_withMentions() {
        DiscussionTopic t = new DiscussionTopic();
        t.setId(1L);
        t.setStatus(DiscussionTopic.STATUS_OPEN);
        t.setCreatorId(99L);
        t.setReplyCount(0);
        when(topicMapper.selectById(1L)).thenReturn(t);

        DiscussionPost maxFloor = new DiscussionPost();
        maxFloor.setFloor(3);
        when(postMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(maxFloor);

        DiscussionPost post = new DiscussionPost();
        post.setContent("<b>回复</b>");
        post.setMentionUserIds(Arrays.asList(8L, 7L, null)); // 通知8，跳过自己7，跳过null

        DiscussionPost r = service.reply(1L, post, 7L, RoleConstants.STUDENT);

        assertEquals(4, r.getFloor());
        assertEquals("8,7", r.getMentions()); // 7被加入mentions字符串（mentions不跳过自己）
        verify(postMapper).insert(any(DiscussionPost.class));
        verify(topicMapper).updateById(any(DiscussionTopic.class));
        // REPLIED -> 99, MENTIONED -> 8
        verify(notificationService, times(2)).onEvent(anyString(), anyMap());
    }

    @Test
    @DisplayName("reply: 楼中楼回复，通知父回复作者；无@提及")
    void reply_nested_noMentions() {
        DiscussionTopic t = new DiscussionTopic();
        t.setId(1L);
        t.setStatus(DiscussionTopic.STATUS_OPEN);
        t.setCreatorId(99L);
        t.setReplyCount(5);
        when(topicMapper.selectById(1L)).thenReturn(t);

        when(postMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null); // maxFloor null -> floor=1

        DiscussionPost parent = new DiscussionPost();
        parent.setAuthorId(50L);
        when(postMapper.selectById(5L)).thenReturn(parent);

        DiscussionPost post = new DiscussionPost();
        post.setParentId(5L);
        post.setContent("回复");
        post.setMentionUserIds(null); // 无@提及

        service.reply(1L, post, 7L, RoleConstants.STUDENT);
        verify(notificationService, times(1)).onEvent(eq("DISCUSSION_REPLIED"), anyMap());
    }

    @Test
    @DisplayName("reply: 通知失败被捕获不影响主流程")
    void reply_notifyFails() {
        DiscussionTopic t = new DiscussionTopic();
        t.setId(1L);
        t.setStatus(DiscussionTopic.STATUS_OPEN);
        t.setCreatorId(99L);
        when(topicMapper.selectById(1L)).thenReturn(t);
        when(postMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doThrow(new RuntimeException("notify down")).when(notificationService).onEvent(anyString(), anyMap());

        DiscussionPost post = new DiscussionPost();
        post.setContent("回复");
        post.setMentionUserIds(Collections.singletonList(8L));
        assertDoesNotThrow(() -> service.reply(1L, post, 7L, RoleConstants.STUDENT));
    }

    // ==================== likePost ====================

    @Test
    @DisplayName("likePost: 回复不存在抛异常")
    void likePost_notFound() {
        when(postMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.likePost(1L, 7L));
        assertTrue(ex.getMessage().contains("回复不存在"));
    }

    @Test
    @DisplayName("likePost: 未点赞 -> 点赞+1")
    void likePost_add() {
        DiscussionPost p = new DiscussionPost();
        p.setId(1L);
        p.setLikeCount(null);
        when(postMapper.selectById(1L)).thenReturn(p);
        when(postLikeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        com.aicourse.vo.LikePostVO res = service.likePost(1L, 7L);
        assertTrue(res.getLiked());
        assertEquals(1, res.getLikeCount());
        verify(postLikeMapper).insert(any(DiscussionPostLike.class));
        verify(postMapper).updateById(any(DiscussionPost.class));
    }

    @Test
    @DisplayName("likePost: 已点赞 -> 取消点赞-1（likeCount 为空边界）")
    void likePost_remove() {
        DiscussionPost p = new DiscussionPost();
        p.setId(1L);
        p.setLikeCount(null);
        when(postMapper.selectById(1L)).thenReturn(p);
        DiscussionPostLike existing = new DiscussionPostLike();
        existing.setId(9L);
        when(postLikeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        com.aicourse.vo.LikePostVO res = service.likePost(1L, 7L);
        assertFalse(res.getLiked());
        assertEquals(0, res.getLikeCount());
        verify(postLikeMapper).deleteById(9L);
    }

    // ==================== closeTopic / pinTopic ====================

    @Test
    @DisplayName("closeTopic: 话题不存在/无权抛异常，否则关闭")
    void closeTopic() {
        when(topicMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.closeTopic(1L, 7L, RoleConstants.STUDENT));

        DiscussionTopic t = new DiscussionTopic();
        t.setId(1L);
        t.setCreatorId(99L);
        when(topicMapper.selectById(1L)).thenReturn(t);
        assertThrows(BusinessException.class, () -> service.closeTopic(1L, 7L, RoleConstants.STUDENT));

        // 管理员可操作
        DiscussionTopic t2 = new DiscussionTopic();
        t2.setId(1L);
        t2.setCreatorId(99L);
        when(topicMapper.selectById(1L)).thenReturn(t2);
        DiscussionTopic r = service.closeTopic(1L, 7L, RoleConstants.ADMIN);
        assertEquals(DiscussionTopic.STATUS_CLOSED, r.getStatus());

        // 拥有者可操作
        DiscussionTopic t3 = new DiscussionTopic();
        t3.setId(1L);
        t3.setCreatorId(7L);
        when(topicMapper.selectById(1L)).thenReturn(t3);
        service.closeTopic(1L, 7L, RoleConstants.STUDENT);
        verify(topicMapper, atLeast(1)).updateById(any(DiscussionTopic.class));
    }

    @Test
    @DisplayName("pinTopic: 置顶/取消置顶")
    void pinTopic() {
        DiscussionTopic t = new DiscussionTopic();
        t.setId(1L);
        t.setCreatorId(7L);
        when(topicMapper.selectById(1L)).thenReturn(t);
        DiscussionTopic r = service.pinTopic(1L, true, 7L, RoleConstants.STUDENT);
        assertTrue(r.getIsPinned());

        DiscussionTopic t2 = new DiscussionTopic();
        t2.setId(1L);
        t2.setCreatorId(7L);
        when(topicMapper.selectById(1L)).thenReturn(t2);
        DiscussionTopic r2 = service.pinTopic(1L, false, 7L, RoleConstants.STUDENT);
        assertFalse(r2.getIsPinned());
    }

    // ==================== markEssence ====================

    @Test
    @DisplayName("markEssence: 不存在/非教师管理员抛异常，否则标记")
    void markEssence() {
        when(topicMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.markEssence(1L, true, 7L, RoleConstants.STUDENT));

        DiscussionTopic t = new DiscussionTopic();
        t.setId(1L);
        when(topicMapper.selectById(1L)).thenReturn(t);
        assertThrows(BusinessException.class, () -> service.markEssence(1L, true, 7L, RoleConstants.STUDENT));

        DiscussionTopic t2 = new DiscussionTopic();
        t2.setId(1L);
        when(topicMapper.selectById(1L)).thenReturn(t2);
        DiscussionTopic r = service.markEssence(1L, true, 7L, RoleConstants.TEACHER);
        assertTrue(r.getIsEssence());
    }

    // ==================== deletePost ====================

    @Test
    @DisplayName("deletePost: 回复不存在抛异常")
    void deletePost_notFound() {
        when(postMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.deletePost(1L, 7L, RoleConstants.STUDENT));
        assertTrue(ex.getMessage().contains("回复不存在"));
    }

    @Test
    @DisplayName("deletePost: 无权删除抛异常")
    void deletePost_noPermission() {
        DiscussionPost p = new DiscussionPost();
        p.setId(1L);
        p.setAuthorId(99L);
        when(postMapper.selectById(1L)).thenReturn(p);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.deletePost(1L, 7L, RoleConstants.STUDENT));
        assertTrue(ex.getMessage().contains("无权删除"));
    }

    @Test
    @DisplayName("deletePost: 管理员删除，话题回复数-1")
    void deletePost_admin() {
        DiscussionPost p = new DiscussionPost();
        p.setId(1L);
        p.setAuthorId(99L);
        p.setTopicId(2L);
        when(postMapper.selectById(1L)).thenReturn(p);

        DiscussionTopic topic = new DiscussionTopic();
        topic.setId(2L);
        topic.setReplyCount(5);
        when(topicMapper.selectById(2L)).thenReturn(topic);

        service.deletePost(1L, 7L, RoleConstants.ADMIN);
        verify(postMapper).deleteById(1L);
        verify(topicMapper).updateById(any(DiscussionTopic.class));
        assertEquals(4, topic.getReplyCount());
    }

    @Test
    @DisplayName("deletePost: 作者删除，话题回复数<=0 不更新")
    void deletePost_author_noDecrement() {
        DiscussionPost p = new DiscussionPost();
        p.setId(1L);
        p.setAuthorId(7L);
        p.setTopicId(2L);
        when(postMapper.selectById(1L)).thenReturn(p);

        when(topicMapper.selectById(2L)).thenReturn(null); // 话题不存在 -> 跳过更新
        service.deletePost(1L, 7L, RoleConstants.STUDENT);
        verify(postMapper).deleteById(1L);
        verify(topicMapper, never()).updateById(any(DiscussionTopic.class));
    }

    @Test
    @DisplayName("deletePost: 话题 replyCount 为空不更新")
    void deletePost_author_nullReplyCount() {
        DiscussionPost p = new DiscussionPost();
        p.setId(1L);
        p.setAuthorId(7L);
        p.setTopicId(2L);
        when(postMapper.selectById(1L)).thenReturn(p);

        DiscussionTopic topic = new DiscussionTopic();
        topic.setId(2L);
        topic.setReplyCount(null);
        when(topicMapper.selectById(2L)).thenReturn(topic);

        service.deletePost(1L, 7L, RoleConstants.STUDENT);
        verify(topicMapper, never()).updateById(any(DiscussionTopic.class));
    }
}
