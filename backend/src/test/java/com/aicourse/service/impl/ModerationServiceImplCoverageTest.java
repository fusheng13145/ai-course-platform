package com.aicourse.service.impl;

import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.Resource;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.DiscussionPostMapper;
import com.aicourse.mapper.ResourceMapper;
import com.aicourse.service.FileStorageService;
import com.aicourse.service.OperationLogService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 内容审核剩余分支：状态筛选、关键词、空作者、资源删除/恢复、封禁边界与摘要截断。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModerationServiceImplCoverageTest {

    @Mock private DiscussionPostMapper discussionPostMapper;
    @Mock private ResourceMapper resourceMapper;
    @Mock private UserService userService;
    @Mock private OperationLogService operationLogService;
    @Mock private FileStorageService fileStorageService;

    private ModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ModerationServiceImpl(discussionPostMapper, resourceMapper, userService,
                operationLogService, fileStorageService);
    }

    private DiscussionPost post(Long id, Long authorId, String content) {
        DiscussionPost p = new DiscussionPost();
        p.setId(id); p.setTopicId(11L); p.setAuthorId(authorId); p.setContent(content);
        p.setModerationStatus(DiscussionPost.MODERATION_NORMAL);
        return p;
    }

    private Resource resource(Long id, Long uploaderId, String url) {
        Resource r = new Resource();
        r.setId(id); r.setUploaderId(uploaderId); r.setFileName("f.pdf"); r.setUrl(url);
        r.setCourseId(1L); r.setModerationStatus(Resource.MODERATION_NORMAL);
        return r;
    }

    private User user(Long id, String role) {
        User u = new User();
        u.setId(id); u.setRole(role); u.setUsername("u" + id); u.setFullName("用户" + id); u.setIsActive(true);
        return u;
    }

    private void stubPostPage(DiscussionPost... posts) {
        doAnswer(inv -> {
            IPage<DiscussionPost> pager = inv.getArgument(0);
            pager.setRecords(List.of(posts)); pager.setTotal(posts.length);
            return pager;
        }).when(discussionPostMapper).selectPage(any(), any());
    }

    private void stubResourcePage(Resource... resources) {
        doAnswer(inv -> {
            IPage<Resource> pager = inv.getArgument(0);
            pager.setRecords(List.of(resources)); pager.setTotal(resources.length);
            return pager;
        }).when(resourceMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("listPosts：NORMAL 筛选、关键词、空页与空作者")
    void listPostsBranches() {
        DiscussionPost withAuthor = post(1L, 7L, "广告内容");
        DiscussionPost noAuthor = post(2L, 404L, "另一条");
        stubPostPage(withAuthor, noAuthor);
        when(userService.listByIds(any())).thenReturn(List.of(user(7L, "STUDENT")));

        var result = service.listPosts(1, 0, "normal", " 广告 ");
        assertEquals(2, result.getList().size());
        assertEquals("用户7", result.getList().get(0).getAuthorName());
        assertNull(result.getList().get(1).getAuthorName());

        stubPostPage();
        var empty = service.listPosts(1, 10, null, null);
        assertTrue(empty.getList().isEmpty());
    }

    @Test
    @DisplayName("listResources：HIDDEN/NORMAL 筛选、关键词、空上传者")
    void listResourcesBranches() {
        stubResourcePage(resource(5L, 8L, "/uploads/a.pdf"), resource(6L, 404L, "/uploads/b.pdf"));
        when(userService.listByIds(any())).thenReturn(List.of(user(8L, "TEACHER")));

        var hidden = service.listResources(1, 20, "hidden", "a");
        var normal = service.listResources(1, 20, "normal", null);
        assertEquals("用户8", hidden.getList().get(0).getUploaderName());
        assertNull(normal.getList().get(1).getUploaderName());
    }

    @Test
    @DisplayName("讨论 HIDE 勾选封禁：先封禁作者再隐藏")
    void hidePostWithBan() {
        when(discussionPostMapper.selectById(1L)).thenReturn(post(1L, 7L, "广告"));
        when(discussionPostMapper.updateById(any(DiscussionPost.class))).thenReturn(1);
        when(userService.getById(7L)).thenReturn(user(7L, "STUDENT"));
        when(userService.updateById(any(User.class))).thenReturn(true);

        Map<String, Object> data = service.applyPostAction(1L, "HIDE", "  ", true, 99L);

        assertEquals(true, data.get("authorBanned"));
        assertEquals(DiscussionPost.MODERATION_HIDDEN, ((DiscussionPost) verifyAndCapturePost()).getModerationStatus());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).updateById(userCaptor.capture());
        assertEquals(false, userCaptor.getValue().getIsActive());
        assertNull(userCaptor.getValue().getBanReason());
    }

    private Object verifyAndCapturePost() {
        ArgumentCaptor<DiscussionPost> captor = ArgumentCaptor.forClass(DiscussionPost.class);
        verify(discussionPostMapper).updateById(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("资源：不存在 404；DELETE 勾选封禁并删盘；RESTORE 忽略封禁")
    void resourceActionBranches() {
        when(resourceMapper.selectById(5L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.applyResourceAction(5L, "DELETE", "x", false, 99L));

        Resource target = resource(5L, 8L, "/uploads/a.pdf");
        when(resourceMapper.selectById(5L)).thenReturn(target);
        when(resourceMapper.deleteById(5L)).thenReturn(1);
        when(userService.getById(8L)).thenReturn(user(8L, "TEACHER"));
        when(userService.updateById(any(User.class))).thenReturn(true);
        when(fileStorageService.deleteByUrl("/uploads/a.pdf")).thenReturn(true);

        Map<String, Object> deleted = service.applyResourceAction(5L, "DELETE", "违规", true, 99L);
        assertEquals("DELETED", deleted.get("moderationStatus"));
        assertEquals(true, deleted.get("fileDeleted"));
        assertEquals(true, deleted.get("authorBanned"));
        verify(fileStorageService).deleteByUrl("/uploads/a.pdf");

        Resource restored = resource(6L, 8L, "/uploads/b.pdf");
        when(resourceMapper.selectById(6L)).thenReturn(restored);
        when(resourceMapper.updateById(any(Resource.class))).thenReturn(1);
        Map<String, Object> restoredData = service.applyResourceAction(6L, "RESTORE", "  ", true, 99L);
        assertEquals("NORMAL", restoredData.get("moderationStatus"));
        assertEquals(false, restoredData.get("authorBanned"));
    }

    @Test
    @DisplayName("资源 HIDE 勾选封禁：状态 HIDDEN 且封禁作者")
    void hideResourceWithBan() {
        Resource target = resource(5L, 8L, "/uploads/a.pdf");
        when(resourceMapper.selectById(5L)).thenReturn(target);
        when(resourceMapper.updateById(any(Resource.class))).thenReturn(1);
        when(userService.getById(8L)).thenReturn(user(8L, "TEACHER"));
        when(userService.updateById(any(User.class))).thenReturn(true);

        Map<String, Object> data = service.applyResourceAction(5L, "HIDE", "涉密", true, 99L);

        assertEquals("HIDDEN", data.get("moderationStatus"));
        assertEquals(true, data.get("authorBanned"));
    }

    @Test
    @DisplayName("封禁/解封： userId 为空、账号不存在、解封不存在")
    void banUnbanBranches() {
        assertThrows(BusinessException.class, () -> service.banAuthor(null, "x", 99L));
        assertThrows(BusinessException.class, () -> service.banAuthor(6L, "x", 99L));
        when(userService.getById(6L)).thenReturn(null);
        assertFalse(service.unbanAuthor(6L));
    }

    @Test
    @DisplayName("删除留痕：内容为 null 与超 200 字分别归空/截断")
    void excerptBranches() {
        when(discussionPostMapper.selectById(1L)).thenReturn(post(1L, 7L, null));
        when(discussionPostMapper.deleteById(1L)).thenReturn(1);
        service.applyPostAction(1L, "DELETE", "x", false, 99L);
        ArgumentCaptor<com.aicourse.entity.OperationLog> first =
                ArgumentCaptor.forClass(com.aicourse.entity.OperationLog.class);
        verify(operationLogService).save(first.capture());
        assertTrue(first.getValue().getParams().contains("\"contentExcerpt\":\"\""));

        String longText = "x".repeat(300);
        when(discussionPostMapper.selectById(2L)).thenReturn(post(2L, 7L, longText));
        when(discussionPostMapper.deleteById(2L)).thenReturn(1);
        service.applyPostAction(2L, "DELETE", "x", false, 99L);
        ArgumentCaptor<com.aicourse.entity.OperationLog> second =
                ArgumentCaptor.forClass(com.aicourse.entity.OperationLog.class);
        verify(operationLogService, times(2)).save(second.capture());
        assertTrue(second.getAllValues().get(1).getParams().contains("…"));
    }

    @Test
    @DisplayName("封禁名单：空列表")
    void emptyBannedList() {
        when(userService.list(any(Wrapper.class))).thenReturn(List.of());
        assertTrue(service.listBannedAuthors().isEmpty());
    }
}
