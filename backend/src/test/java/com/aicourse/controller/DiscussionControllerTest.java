package com.aicourse.controller;
import com.aicourse.controller.DiscussionController;
import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.DiscussionTopic;
import com.aicourse.service.DiscussionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DiscussionController 接口测试（升级版：真实业务断言）。
 * 对每个 endpoint 打桩 service 并断言实体 / Map 字段透传，verify 确认路由委托。
 * closeTopic/pinTopic/markEssence 限定教师/管理员，已新增越权 403 测试。
 */
@WebMvcTest(DiscussionController.class)
class DiscussionControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.DiscussionService discussionService;

    @Test
    @DisplayName("POST /discussion/topic - 发布话题返回200，ID透传")
    void createTopic_authorized_passes_security() throws Exception {
        DiscussionTopic topic = new DiscussionTopic();
        topic.setId(1L);
        when(discussionService.createTopic(any(), eq(1L), any())).thenReturn(topic);
        mockMvc.perform(post("/discussion/topic").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(discussionService).createTopic(any(), eq(1L), any());
    }

    @Test
    @DisplayName("POST /discussion/topic - 未认证返回401")
    void createTopic_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/discussion/topic").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /discussion/topic/course/1 - 查询课程话题返回200，数组透传")
    void listTopics_authorized_passes_security() throws Exception {
        DiscussionTopic topic = new DiscussionTopic();
        topic.setId(1L);
        when(discussionService.listTopics(eq(1L), any(), any(), any())).thenReturn(List.of(topic));
        mockMvc.perform(get("/discussion/topic/course/1?chapterId=1&keyword=test&essence=true")
                        .with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(discussionService).listTopics(eq(1L), any(), any(), any());
    }

    @Test
    @DisplayName("GET /discussion/topic/course/1?chapterId=1&keyword=test&essence=test - 未认证返回401")
    void listTopics_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/discussion/topic/course/1?chapterId=1&keyword=test&essence=test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /discussion/topic/1 - 查询话题详情返回200，字段透传")
    void getTopic_authorized_passes_security() throws Exception {
        com.aicourse.vo.TopicDetailVO stub = new com.aicourse.vo.TopicDetailVO();
        DiscussionTopic stubTopic = new DiscussionTopic();
        stubTopic.setTitle("话题");
        stub.setTopic(stubTopic);
        stub.setPosts(List.of());
        stub.setReplyCount(0);
        when(discussionService.getTopicDetail(eq(1L))).thenReturn(stub);
        mockMvc.perform(get("/discussion/topic/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.topic.title").value("话题"));
        verify(discussionService).getTopicDetail(eq(1L));
    }

    @Test
    @DisplayName("GET /discussion/topic/1 - 未认证返回401")
    void getTopic_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/discussion/topic/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /discussion/topic/1/reply - 回复话题返回200，ID透传")
    void reply_authorized_passes_security() throws Exception {
        DiscussionPost post = new DiscussionPost();
        post.setId(1L);
        when(discussionService.reply(eq(1L), any(), eq(1L), any())).thenReturn(post);
        mockMvc.perform(post("/discussion/topic/1/reply").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(discussionService).reply(eq(1L), any(), eq(1L), any());
    }

    @Test
    @DisplayName("POST /discussion/topic/1/reply - 未认证返回401")
    void reply_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/discussion/topic/1/reply").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /discussion/post/1/like - 点赞返回200，字段透传")
    void likePost_authorized_passes_security() throws Exception {
        when(discussionService.likePost(eq(1L), eq(1L))).thenReturn(new com.aicourse.vo.LikePostVO(1, true));
        mockMvc.perform(post("/discussion/post/1/like").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.liked").value(true));
        verify(discussionService).likePost(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("POST /discussion/post/1/like - 未认证返回401")
    void likePost_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/discussion/post/1/like"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /discussion/topic/1/close - 教师关闭话题返回200，ID透传")
    void closeTopic_authorized_passes_security() throws Exception {
        DiscussionTopic topic = new DiscussionTopic();
        topic.setId(1L);
        when(discussionService.closeTopic(eq(1L), eq(1L), any())).thenReturn(topic);
        mockMvc.perform(post("/discussion/topic/1/close").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(discussionService).closeTopic(eq(1L), eq(1L), any());
    }

    @Test
    @DisplayName("POST /discussion/topic/1/close - 未认证返回401")
    void closeTopic_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/discussion/topic/1/close"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /discussion/topic/1/close - 学生越权返回403")
    void closeTopic_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/discussion/topic/1/close").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /discussion/topic/1/pin - 教师置顶话题返回200，ID透传")
    void pinTopic_authorized_passes_security() throws Exception {
        DiscussionTopic topic = new DiscussionTopic();
        topic.setId(1L);
        when(discussionService.pinTopic(eq(1L), eq(true), eq(1L), any())).thenReturn(topic);
        mockMvc.perform(post("/discussion/topic/1/pin?pinned=true").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(discussionService).pinTopic(eq(1L), eq(true), eq(1L), any());
    }

    @Test
    @DisplayName("POST /discussion/topic/1/pin - 未认证返回401")
    void pinTopic_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/discussion/topic/1/pin?pinned=true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /discussion/topic/1/pin - 学生越权返回403")
    void pinTopic_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/discussion/topic/1/pin?pinned=true").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /discussion/topic/1/essence - 教师加精返回200，ID透传")
    void markEssence_authorized_passes_security() throws Exception {
        DiscussionTopic topic = new DiscussionTopic();
        topic.setId(1L);
        when(discussionService.markEssence(eq(1L), eq(true), eq(1L), any())).thenReturn(topic);
        mockMvc.perform(post("/discussion/topic/1/essence?essence=true").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(discussionService).markEssence(eq(1L), eq(true), eq(1L), any());
    }

    @Test
    @DisplayName("POST /discussion/topic/1/essence - 未认证返回401")
    void markEssence_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/discussion/topic/1/essence?essence=true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /discussion/topic/1/essence - 学生越权返回403")
    void markEssence_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/discussion/topic/1/essence?essence=true").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /discussion/post/1 - 删除帖子返回200")
    void deletePost_authorized_passes_security() throws Exception {
        mockMvc.perform(delete("/discussion/post/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(discussionService).deletePost(eq(1L), eq(1L), any());
    }

    @Test
    @DisplayName("DELETE /discussion/post/1 - 未认证返回401")
    void deletePost_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/discussion/post/1"))
                .andExpect(status().isUnauthorized());
    }
}
