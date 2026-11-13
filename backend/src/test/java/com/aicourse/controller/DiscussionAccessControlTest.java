package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.DiscussionTopic;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.DiscussionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 讨论区对象级授权切片测试（2026-11-06 验收批，B-05）。
 * <p>
 * 改前实测（真栈 8080）：{@code student}（选课集合 [1,2,3,7026088,7026065]）对未选课程
 * 4060/4027/4028 调 {@code GET /api/discussion/topic/course/{id}} 一律 <b>HTTP 200</b>，
 * 响应体含他人课程话题的标题、正文与 creatorId；阴性对照 {@code GET /api/users/{id}} 同机返回 403。
 * 本组用例把「读/发帖/回复必须先过课程成员关系」钉成机械断言，
 * 成员关系判定复用 {@link CourseAccessControl#assertCanReadCourse}，此处只验接线与状态码。
 */
@WebMvcTest(DiscussionController.class)
class DiscussionAccessControlTest extends ControllerTestBase {

    @MockitoBean
    private DiscussionService discussionService;

    @MockitoBean
    private CourseAccessControl courseAccessControl;

    private static BusinessException notMember() {
        return new BusinessException(ApiCode.FORBIDDEN, "尚未加入该课程，无权查看课程内容");
    }

    @Test
    @DisplayName("未登录访问课程话题列表返回 401")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/discussion/topic/course/4060"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未选课学生读他人课程话题列表：403，且不落到服务层")
    void nonMemberStudentCannotListTopics() throws Exception {
        doThrow(notMember()).when(courseAccessControl).assertCanReadCourse(anyLong(), any());

        mockMvc.perform(withUser(get("/discussion/topic/course/4060"), 5001L, "STUDENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(courseAccessControl).assertCanReadCourse(eq(4060L), any());
        verify(discussionService, org.mockito.Mockito.never())
                .listTopics(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("已选课学生读本课程内容：放行并落到服务层")
    void enrolledStudentCanListOwnCourseTopics() throws Exception {
        doNothing().when(courseAccessControl).assertCanReadCourse(anyLong(), any());
        doReturn(List.of()).when(discussionService).listTopics(anyLong(), any(), any(), any());

        mockMvc.perform(withUser(get("/discussion/topic/course/4007"), 5001L, "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(courseAccessControl).assertCanReadCourse(eq(4007L), any());
    }

    @Test
    @DisplayName("读他人课程话题详情：先按话题归属课程判成员关系，非成员 403")
    void topicDetailIsScopedToItsCourse() throws Exception {
        DiscussionTopic foreign = new DiscussionTopic();
        foreign.setId(77L);
        foreign.setCourseId(4060L);
        doReturn(foreign).when(discussionService).getById(77L);
        doThrow(notMember()).when(courseAccessControl).assertCanReadCourse(anyLong(), any());

        mockMvc.perform(withUser(get("/discussion/topic/77"), 5001L, "STUDENT"))
                .andExpect(status().isForbidden());

        verify(courseAccessControl).assertCanReadCourse(eq(4060L), any());
        verify(discussionService, org.mockito.Mockito.never()).getTopicDetail(anyLong());
    }

    @Test
    @DisplayName("在未加入的课程下回帖：403（写面同样受成员关系约束）")
    void nonMemberCannotReply() throws Exception {
        DiscussionTopic foreign = new DiscussionTopic();
        foreign.setId(77L);
        foreign.setCourseId(4060L);
        doReturn(foreign).when(discussionService).getById(77L);
        doThrow(notMember()).when(courseAccessControl).assertCanReadCourse(anyLong(), any());

        mockMvc.perform(withUser(post("/discussion/topic/77/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"占位回复\"}"), 5001L, "STUDENT"))
                .andExpect(status().isForbidden());

        verify(discussionService, org.mockito.Mockito.never())
                .reply(anyLong(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("在未加入的课程下发帖：403")
    void nonMemberCannotCreateTopic() throws Exception {
        doThrow(notMember()).when(courseAccessControl).assertCanReadCourse(anyLong(), any());

        mockMvc.perform(withUser(post("/discussion/topic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":4060,\"title\":\"t\",\"content\":\"c\"}"),
                5001L, "STUDENT"))
                .andExpect(status().isForbidden());

        verify(discussionService, org.mockito.Mockito.never())
                .createTopic(any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("给他人课程的回复点赞：403，且不落点赞计数")
    void nonMemberCannotLikePostInForeignCourse() throws Exception {
        doThrow(notMember()).when(courseAccessControl).assertCanReadCourse(eq(4060L), any());
        doReturn(4060L).when(discussionService).courseIdOfPost(88L);

        mockMvc.perform(withUser(post("/discussion/post/88/like"), 5001L, "STUDENT"))
                .andExpect(status().isForbidden());

        verify(discussionService, never()).likePost(anyLong(), anyLong());
    }

    @Test
    @DisplayName("给本人课程内的回复点赞：放行并落到服务层")
    void memberCanLikePostInOwnCourse() throws Exception {
        doReturn(4007L).when(discussionService).courseIdOfPost(88L);
        doReturn(null).when(discussionService).likePost(eq(88L), eq(5001L));

        mockMvc.perform(withUser(post("/discussion/post/88/like"), 5001L, "STUDENT"))
                .andExpect(status().isOk());

        verify(courseAccessControl).assertCanReadCourse(eq(4007L), any());
    }

    @Test
    @DisplayName("话题不带 courseId：400，不放行到服务层")
    void missingCourseIdIsRejected() throws Exception {
        mockMvc.perform(withUser(post("/discussion/topic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"), 5001L, "STUDENT"))
                .andExpect(status().isBadRequest());

        verify(discussionService, org.mockito.Mockito.never())
                .createTopic(any(), anyLong(), anyString());
    }
}
