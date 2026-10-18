package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.Resource;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.DiscussionPostMapper;
import com.aicourse.mapper.ResourceMapper;
import com.aicourse.service.UserService;
import com.aicourse.vo.BannedAuthorVO;
import com.aicourse.vo.ModerationPostVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * ModerationServiceImpl 单元测试（角色矩阵 ③ 内容审核与违规处理）。
 *
 * <p>钉住四条口径：① HIDE/RESTORE 只改状态与留痕、不删内容；② DELETE 是**物理删**且先封禁后删；
 * ③ 封禁复用 {@code is_active=0} 且**不能封禁管理员**；④ 解除封禁只对"经审核封禁"（banned_at 非空）
 * 有效——不得把平台其它原因停用的账号顺手解开。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModerationServiceImplTest {

    @Mock
    private DiscussionPostMapper discussionPostMapper;
    @Mock
    private ResourceMapper resourceMapper;
    @Mock
    private UserService userService;

    private ModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ModerationServiceImpl(discussionPostMapper, resourceMapper, userService);
    }

    private DiscussionPost post(Long id, Long authorId) {
        DiscussionPost p = new DiscussionPost();
        p.setId(id);
        p.setTopicId(11L);
        p.setAuthorId(authorId);
        p.setContent("违规内容");
        p.setModerationStatus(DiscussionPost.MODERATION_NORMAL);
        return p;
    }

    private User user(Long id, String role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setUsername("u" + id);
        u.setFullName("用户" + id);
        u.setIsActive(true);
        return u;
    }

    private void stubPage(DiscussionPost... posts) {
        when(discussionPostMapper.selectPage(any(), any())).thenAnswer(inv -> {
            IPage<DiscussionPost> pager = inv.getArgument(0);
            pager.setRecords(List.of(posts));
            pager.setTotal(posts.length);
            return pager;
        });
    }

    // ===================== 列表 =====================

    @Test
    @DisplayName("listPosts：补作者姓名，且分页 size 越界收敛到 1–200")
    void listPosts_enrichesAuthorAndClampsSize() {
        stubPage(post(1L, 7L));
        when(userService.listByIds(any())).thenReturn(List.of(user(7L, "STUDENT")));

        var result = service.listPosts(0, 9999, " hidden ", null);

        assertEquals(1, result.getList().size());
        ModerationPostVO vo = result.getList().get(0);
        assertEquals("用户7", vo.getAuthorName());
        // 页码下限与 size 上限都必须被收敛：0 页 → 1 页；9999 → 200
        ArgumentCaptor<IPage<DiscussionPost>> captor = ArgumentCaptor.forClass(IPage.class);
        verify(discussionPostMapper).selectPage(captor.capture(), any());
        assertEquals(1L, captor.getValue().getCurrent());
        assertEquals(200L, captor.getValue().getSize());
    }

    @Test
    @DisplayName("listPosts：作者已被删除时姓名置空，但仍返回该行（不隐藏内容）")
    void listPosts_missingAuthor() {
        stubPage(post(1L, 404L));
        when(userService.listByIds(any())).thenReturn(List.of());

        var result = service.listPosts(1, 20, null, null);

        assertNull(result.getList().get(0).getAuthorName());
        assertNotNull(result.getList().get(0).getContent());
    }

    // ===================== 讨论回复处置 =====================

    @Test
    @DisplayName("HIDE：只置 HIDDEN + 原因 + 审核留痕，不删内容")
    void hide_post() {
        when(discussionPostMapper.selectById(1L)).thenReturn(post(1L, 7L));
        when(discussionPostMapper.updateById(any(DiscussionPost.class))).thenReturn(1);

        Map<String, Object> data = service.applyPostAction(1L, "HIDE", "  含广告  ", false, 99L);

        ArgumentCaptor<DiscussionPost> captor = ArgumentCaptor.forClass(DiscussionPost.class);
        verify(discussionPostMapper).updateById(captor.capture());
        assertEquals(DiscussionPost.MODERATION_HIDDEN, captor.getValue().getModerationStatus());
        assertEquals("含广告", captor.getValue().getModerationReason(), "原因应去空白");
        assertEquals(99L, captor.getValue().getModeratedBy());
        assertNotNull(captor.getValue().getModeratedAt());
        verify(discussionPostMapper, never()).deleteById(anyLong());
        assertEquals("HIDDEN", data.get("moderationStatus"));
        assertEquals(false, data.get("authorBanned"));
    }

    @Test
    @DisplayName("RESTORE：置回 NORMAL（可恢复），并忽略 banAuthor 字段")
    void restore_post_ignoresBanFlag() {
        DiscussionPost hidden = post(1L, 7L);
        hidden.setModerationStatus(DiscussionPost.MODERATION_HIDDEN);
        when(discussionPostMapper.selectById(1L)).thenReturn(hidden);
        when(discussionPostMapper.updateById(any(DiscussionPost.class))).thenReturn(1);

        service.applyPostAction(1L, "RESTORE", null, true, 99L);

        ArgumentCaptor<DiscussionPost> captor = ArgumentCaptor.forClass(DiscussionPost.class);
        verify(discussionPostMapper).updateById(captor.capture());
        assertEquals(DiscussionPost.MODERATION_NORMAL, captor.getValue().getModerationStatus());
        assertNull(captor.getValue().getModerationReason());
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("DELETE：物理删；勾选封禁作者时先封禁（事务内），并如实回传 authorBanned")
    void delete_post_withBanAuthor() {
        when(discussionPostMapper.selectById(1L)).thenReturn(post(1L, 7L));
        when(discussionPostMapper.deleteById(1L)).thenReturn(1);
        when(userService.getById(7L)).thenReturn(user(7L, "STUDENT"));
        when(userService.updateById(any(User.class))).thenReturn(true);

        Map<String, Object> data = service.applyPostAction(1L, "DELETE", "多次刷屏", true, 99L);

        verify(discussionPostMapper).deleteById(1L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).updateById(captor.capture());
        assertEquals(false, captor.getValue().getIsActive(), "封禁复用 is_active=0");
        assertEquals("多次刷屏", captor.getValue().getBanReason());
        assertEquals(99L, captor.getValue().getBannedBy());
        assertNotNull(captor.getValue().getBannedAt());
        assertEquals("DELETED", data.get("moderationStatus"));
        assertEquals(true, data.get("authorBanned"));
    }

    @Test
    @DisplayName("处置不存在的回复 → 404（不得静默成功）")
    void action_missingPost_404() {
        when(discussionPostMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyPostAction(1L, "HIDE", "x", false, 99L));
        assertEquals(ApiCode.NOT_FOUND, ex.getCode());
        verify(discussionPostMapper, never()).updateById(any(DiscussionPost.class));
    }

    // ===================== 资源处置 =====================

    @Test
    @DisplayName("资源 HIDE：置 HIDDEN 并留痕（与讨论回复同一口径）")
    void hide_resource() {
        Resource resource = new Resource();
        resource.setId(5L);
        resource.setUploaderId(8L);
        resource.setModerationStatus(Resource.MODERATION_NORMAL);
        when(resourceMapper.selectById(5L)).thenReturn(resource);
        when(resourceMapper.updateById(any(Resource.class))).thenReturn(1);

        Map<String, Object> data = service.applyResourceAction(5L, "HIDE", "涉密材料", false, 99L);

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceMapper).updateById(captor.capture());
        assertEquals(Resource.MODERATION_HIDDEN, captor.getValue().getModerationStatus());
        assertEquals("resources", data.get("target"));
    }

    // ===================== 封禁 / 解封 =====================

    @Test
    @DisplayName("封禁：不能封禁管理员（400），不得静默接受")
    void ban_admin_400() {
        when(userService.getById(3L)).thenReturn(user(3L, "ADMIN"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.banAuthor(3L, "x", 99L));
        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("封禁：账号不存在 → 404")
    void ban_missingUser_404() {
        when(userService.getById(404L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.banAuthor(404L, "x", 99L));
        assertEquals(ApiCode.NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("解封：非经审核封禁（banned_at 为空）→ false 且不改账号状态")
    void unban_nonModerationDisable_refused() {
        User disabled = user(7L, "STUDENT");
        disabled.setIsActive(false); // 其它原因停用（如演示账号加固），banned_at 仍为空
        when(userService.getById(7L)).thenReturn(disabled);

        assertFalse(service.unbanAuthor(7L));
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("解封：经审核封禁 → 恢复 is_active 并清除三列留痕")
    void unban_moderationBan_restores() {
        User banned = user(7L, "STUDENT");
        banned.setIsActive(false);
        banned.setBanReason("刷屏");
        banned.setBannedAt(java.time.LocalDateTime.now());
        banned.setBannedBy(99L);
        when(userService.getById(7L)).thenReturn(banned);
        when(userService.updateById(any(User.class))).thenReturn(true);

        assertTrue(service.unbanAuthor(7L));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).updateById(captor.capture());
        assertEquals(true, captor.getValue().getIsActive());
        assertNull(captor.getValue().getBanReason());
        assertNull(captor.getValue().getBannedAt());
        assertNull(captor.getValue().getBannedBy());
    }

    @Test
    @DisplayName("封禁名单：只列 banned_at 非空的账号（不与其它原因停用混在一起）")
    void listBannedAuthors() {
        User banned = user(7L, "STUDENT");
        banned.setBanReason("刷屏");
        banned.setBannedAt(java.time.LocalDateTime.now());
        // 显式标注 Wrapper：IService 同时有 list(Wrapper) 与 list(IPage) 两个重载，
        // 裸 any() 会让 javac 无法在两者间选择（与 DR-54 同一处坑，本轮再次踩到并就地修正）
        when(userService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(banned));

        List<BannedAuthorVO> list = service.listBannedAuthors();

        assertEquals(1, list.size());
        assertEquals(7L, list.get(0).getUserId());
        assertEquals("刷屏", list.get(0).getBanReason());
    }
}
