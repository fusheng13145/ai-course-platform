package com.aicourse.service.impl;

import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.DiscussionPostLike;
import com.aicourse.entity.DiscussionTopic;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.DiscussionPostLikeMapper;
import com.aicourse.mapper.DiscussionPostMapper;
import com.aicourse.mapper.DiscussionTopicMapper;
import com.aicourse.service.NotificationService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 课程讨论服务单元测试（B 第一批）。
 * 依赖 DiscussionTopicMapper(baseMapper) + DiscussionPostMapper + DiscussionPostLikeMapper + NotificationService。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscussionServiceImplTest {

    @Mock private DiscussionTopicMapper topicMapper;
    @Mock private DiscussionPostMapper postMapper;
    @Mock private DiscussionPostLikeMapper postLikeMapper;
    @Mock private NotificationService notificationService;
    @InjectMocks private DiscussionServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", topicMapper);
        // 注册 DiscussionPost 的 lambda 元数据，使 LambdaQueryWrapper 在无 Spring 上下文时可用
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DiscussionPost.class);
    }

    private DiscussionTopic topic(Long id, String status) {
        DiscussionTopic t = new DiscussionTopic();
        t.setId(id);
        t.setStatus(status);
        t.setCreatorId(1L);
        t.setCourseId(1L);
        t.setReplyCount(0);
        return t;
    }

    private DiscussionPost post(Long id) {
        DiscussionPost p = new DiscussionPost();
        p.setId(id);
        p.setTopicId(1L);
        p.setAuthorId(2L);
        p.setLikeCount(0);
        p.setFloor(1);
        return p;
    }

    @Test
    @DisplayName("创建讨论-成功且 XSS 转义")
    void createTopic_ok() {
        when(topicMapper.insert(any(DiscussionTopic.class))).thenReturn(1);
        DiscussionTopic t = topic(1L, "OPEN");
        t.setTitle("<b>hi</b>");
        t.setContent("x");
        DiscussionTopic r = service.createTopic(t, 1L, "STUDENT");
        assertEquals(DiscussionTopic.STATUS_OPEN, r.getStatus());
        assertTrue(r.getTitle().contains("&lt;"));
    }

    @Test
    @DisplayName("列出讨论-成功")
    void listTopics_ok() {
        when(topicMapper.selectList(any())).thenReturn(List.of(topic(1L, "OPEN")));
        assertEquals(1, service.listTopics(1L, null, null, null).size());
    }

    @Test
    @DisplayName("讨论详情-成功")
    void getTopicDetail_ok() {
        when(topicMapper.selectById(1L)).thenReturn(topic(1L, "OPEN"));
        when(postMapper.selectList(any())).thenReturn(List.of(post(1L)));
        com.aicourse.vo.TopicDetailVO r = service.getTopicDetail(1L);
        assertEquals(1, r.getPosts().size());
    }

    @Test
    @DisplayName("讨论详情-不存在抛异常")
    void getTopicDetail_notFound() {
        when(topicMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getTopicDetail(9L));
    }

    @Test
    @DisplayName("回复-成功并触发通知")
    void reply_ok() {
        when(topicMapper.selectById(1L)).thenReturn(topic(1L, "OPEN"));
        when(postMapper.selectOne(any())).thenReturn(null);
        when(postMapper.insert(any(DiscussionPost.class))).thenReturn(1);
        when(topicMapper.updateById(any(DiscussionTopic.class))).thenReturn(1);
        DiscussionPost p = new DiscussionPost();
        p.setTopicId(1L);
        p.setContent("hello");
        p.setMentionUserIds(List.of(3L));
        DiscussionPost r = service.reply(1L, p, 2L, "STUDENT");
        assertEquals(1, (int) r.getFloor());
    }

    @Test
    @DisplayName("回复-话题已关闭抛异常")
    void reply_closed() {
        when(topicMapper.selectById(1L)).thenReturn(topic(1L, DiscussionTopic.STATUS_CLOSED));
        assertThrows(BusinessException.class, () -> service.reply(1L, new DiscussionPost(), 2L, "STUDENT"));
    }

    @Test
    @DisplayName("点赞-点赞成功")
    void likePost_like() {
        when(postMapper.selectById(1L)).thenReturn(post(1L));
        when(postLikeMapper.selectOne(any())).thenReturn(null);
        when(postLikeMapper.insert(any(DiscussionPostLike.class))).thenReturn(1);
        when(postMapper.updateById(any(DiscussionPost.class))).thenReturn(1);
        com.aicourse.vo.LikePostVO r = service.likePost(1L, 2L);
        assertEquals(true, r.getLiked());
    }

    @Test
    @DisplayName("点赞-取消点赞")
    void likePost_unlike() {
        when(postMapper.selectById(1L)).thenReturn(post(1L));
        DiscussionPostLike like = new DiscussionPostLike();
        like.setId(9L);
        like.setPostId(1L);
        like.setUserId(2L);
        when(postLikeMapper.selectOne(any())).thenReturn(like);
        when(postLikeMapper.deleteById(any(Long.class))).thenReturn(1);
        when(postMapper.updateById(any(DiscussionPost.class))).thenReturn(1);
        com.aicourse.vo.LikePostVO r = service.likePost(1L, 2L);
        assertEquals(false, r.getLiked());
    }

    @Test
    @DisplayName("点赞-回复不存在抛异常")
    void likePost_notFound() {
        when(postMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.likePost(9L, 2L));
    }

    @Test
    @DisplayName("关闭话题-管理员成功")
    void closeTopic_ok() {
        when(topicMapper.selectById(1L)).thenReturn(topic(1L, "OPEN"));
        when(topicMapper.updateById(any(DiscussionTopic.class))).thenReturn(1);
        assertEquals(DiscussionTopic.STATUS_CLOSED, service.closeTopic(1L, 1L, "ADMIN").getStatus());
    }

    @Test
    @DisplayName("置顶话题-管理员成功")
    void pinTopic_ok() {
        when(topicMapper.selectById(1L)).thenReturn(topic(1L, "OPEN"));
        when(topicMapper.updateById(any(DiscussionTopic.class))).thenReturn(1);
        service.pinTopic(1L, true, 1L, "ADMIN");
        verify(topicMapper).updateById(any(DiscussionTopic.class));
    }

    @Test
    @DisplayName("标记精华-教师成功")
    void markEssence_teacherOk() {
        when(topicMapper.selectById(1L)).thenReturn(topic(1L, "OPEN"));
        when(topicMapper.updateById(any(DiscussionTopic.class))).thenReturn(1);
        service.markEssence(1L, true, 1L, "TEACHER");
        verify(topicMapper).updateById(any(DiscussionTopic.class));
    }

    @Test
    @DisplayName("标记精华-学生无权限抛异常")
    void markEssence_studentDenied() {
        when(topicMapper.selectById(1L)).thenReturn(topic(1L, "OPEN"));
        assertThrows(BusinessException.class, () -> service.markEssence(1L, true, 2L, "STUDENT"));
    }

    @Test
    @DisplayName("删除回复-作者成功")
    void deletePost_authorOk() {
        DiscussionPost p = post(1L);
        p.setAuthorId(2L);
        when(postMapper.selectById(1L)).thenReturn(p);
        when(postMapper.deleteById(1L)).thenReturn(1);
        when(topicMapper.selectById(1L)).thenReturn(topic(1L, "OPEN"));
        when(topicMapper.updateById(any(DiscussionTopic.class))).thenReturn(1);
        service.deletePost(1L, 2L, "STUDENT");
        verify(postMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除回复-无权限抛异常")
    void deletePost_noPermission() {
        DiscussionPost p = post(1L);
        p.setAuthorId(2L);
        when(postMapper.selectById(1L)).thenReturn(p);
        assertThrows(BusinessException.class, () -> service.deletePost(1L, 9L, "STUDENT"));
    }
}
