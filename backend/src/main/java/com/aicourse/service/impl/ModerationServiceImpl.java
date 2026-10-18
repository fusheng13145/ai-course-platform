package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.common.PagedResponse;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.Resource;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.DiscussionPostMapper;
import com.aicourse.mapper.ResourceMapper;
import com.aicourse.service.ModerationService;
import com.aicourse.service.UserService;
import com.aicourse.vo.BannedAuthorVO;
import com.aicourse.vo.ModerationPostVO;
import com.aicourse.vo.ModerationResourceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link ModerationService} 实现。
 *
 * <p>关键取舍：
 * ① 列表用 MyBatis-Plus 分页 + 批量取作者/上传者姓名（避免逐行查询）；
 * ② DELETE 走物理删，讨论回复按其父级外键会**级联删除楼中楼**——这一点在控制器文案与页面确认框里如实说明；
 * ③ 封禁与解封只动 {@code is_active} 与三个留痕列，不触碰其它账号状态。</p>
 */
@Service
public class ModerationServiceImpl implements ModerationService {

    /** 审核状态：正常（取值与实体常量 DiscussionPost/Resource.MODERATION_NORMAL 一致）。 */
    private static final String STATUS_NORMAL = DiscussionPost.MODERATION_NORMAL;
    /** 审核状态：已隐藏（读路径过滤，可恢复；取值与实体常量一致）。 */
    private static final String STATUS_HIDDEN = DiscussionPost.MODERATION_HIDDEN;

    /** 处置动作。 */
    private static final String ACTION_HIDE = "HIDE";
    private static final String ACTION_RESTORE = "RESTORE";
    private static final String ACTION_DELETE = "DELETE";

    private static final int MAX_PAGE_SIZE = 200;
    private static final int MIN_PAGE_SIZE = 1;

    private final DiscussionPostMapper discussionPostMapper;
    private final ResourceMapper resourceMapper;
    private final UserService userService;

    public ModerationServiceImpl(DiscussionPostMapper discussionPostMapper,
                                 ResourceMapper resourceMapper,
                                 UserService userService) {
        this.discussionPostMapper = discussionPostMapper;
        this.resourceMapper = resourceMapper;
        this.userService = userService;
    }

    // ===================== 列表 =====================

    @Override
    public PagedResponse<ModerationPostVO> listPosts(int page, int size, String status, String keyword) {
        Page<DiscussionPost> pager = new Page<>(Math.max(page, 1), clampSize(size));
        LambdaQueryWrapper<DiscussionPost> qw = new LambdaQueryWrapper<>();
        if (STATUS_HIDDEN.equalsIgnoreCase(safe(status))) {
            qw.eq(DiscussionPost::getModerationStatus, STATUS_HIDDEN);
        } else if (STATUS_NORMAL.equalsIgnoreCase(safe(status))) {
            qw.eq(DiscussionPost::getModerationStatus, STATUS_NORMAL);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(DiscussionPost::getContent, keyword.trim());
        }
        qw.orderByDesc(DiscussionPost::getCreatedAt);

        IPage<DiscussionPost> result = discussionPostMapper.selectPage(pager, qw);
        Map<Long, User> users = loadUsers(result.getRecords().stream()
                .map(DiscussionPost::getAuthorId).toList());
        List<ModerationPostVO> list = new ArrayList<>(result.getRecords().size());
        for (DiscussionPost post : result.getRecords()) {
            ModerationPostVO vo = new ModerationPostVO();
            vo.setId(post.getId());
            vo.setTopicId(post.getTopicId());
            vo.setAuthorId(post.getAuthorId());
            User author = users.get(post.getAuthorId());
            vo.setAuthorName(author != null ? author.getFullName() : null);
            vo.setContent(post.getContent());
            vo.setModerationStatus(post.getModerationStatus());
            vo.setModerationReason(post.getModerationReason());
            vo.setModeratedAt(post.getModeratedAt());
            vo.setCreatedAt(post.getCreatedAt());
            list.add(vo);
        }
        return PagedResponse.of(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public PagedResponse<ModerationResourceVO> listResources(int page, int size, String status, String keyword) {
        Page<Resource> pager = new Page<>(Math.max(page, 1), clampSize(size));
        LambdaQueryWrapper<Resource> qw = new LambdaQueryWrapper<>();
        if (STATUS_HIDDEN.equalsIgnoreCase(safe(status))) {
            qw.eq(Resource::getModerationStatus, STATUS_HIDDEN);
        } else if (STATUS_NORMAL.equalsIgnoreCase(safe(status))) {
            qw.eq(Resource::getModerationStatus, STATUS_NORMAL);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Resource::getFileName, keyword.trim());
        }
        qw.orderByDesc(Resource::getUploadTime);

        IPage<Resource> result = resourceMapper.selectPage(pager, qw);
        Map<Long, User> users = loadUsers(result.getRecords().stream()
                .map(Resource::getUploaderId).toList());
        List<ModerationResourceVO> list = new ArrayList<>(result.getRecords().size());
        for (Resource resource : result.getRecords()) {
            ModerationResourceVO vo = new ModerationResourceVO();
            vo.setId(resource.getId());
            vo.setFileName(resource.getFileName());
            vo.setFileType(resource.getFileType());
            vo.setCourseId(resource.getCourseId());
            vo.setUploaderId(resource.getUploaderId());
            User uploader = users.get(resource.getUploaderId());
            vo.setUploaderName(uploader != null ? uploader.getFullName() : null);
            vo.setModerationStatus(resource.getModerationStatus());
            vo.setModerationReason(resource.getModerationReason());
            vo.setModeratedAt(resource.getModeratedAt());
            vo.setUploadTime(resource.getUploadTime());
            list.add(vo);
        }
        return PagedResponse.of(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    // ===================== 处置 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyPostAction(Long postId, String action, String reason,
                                               boolean banAuthor, Long adminId) {
        DiscussionPost post = discussionPostMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "讨论回复不存在：" + postId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("target", "discussion-posts");
        data.put("id", postId);
        data.put("action", action);

        if (ACTION_DELETE.equals(action)) {
            // 先封禁再删：删除后作者ID仍在本方法内存里，但顺序固定便于出错时整事务回滚
            boolean banned = Boolean.TRUE.equals(banAuthor)
                    && banAuthorInternal(post.getAuthorId(), reason, adminId);
            discussionPostMapper.deleteById(postId);
            data.put("moderationStatus", "DELETED");
            data.put("authorBanned", banned);
            return data;
        }

        boolean hide = ACTION_HIDE.equals(action);
        post.setModerationStatus(hide ? STATUS_HIDDEN : STATUS_NORMAL);
        post.setModerationReason(normalize(reason));
        post.setModeratedAt(LocalDateTime.now());
        post.setModeratedBy(adminId);
        discussionPostMapper.updateById(post);

        boolean banned = hide && Boolean.TRUE.equals(banAuthor)
                && banAuthorInternal(post.getAuthorId(), reason, adminId);
        data.put("moderationStatus", post.getModerationStatus());
        data.put("authorBanned", banned);
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyResourceAction(Long resourceId, String action, String reason,
                                                   boolean banAuthor, Long adminId) {
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "资源不存在：" + resourceId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("target", "resources");
        data.put("id", resourceId);
        data.put("action", action);

        if (ACTION_DELETE.equals(action)) {
            boolean banned = Boolean.TRUE.equals(banAuthor)
                    && banAuthorInternal(resource.getUploaderId(), reason, adminId);
            resourceMapper.deleteById(resourceId);
            data.put("moderationStatus", "DELETED");
            data.put("authorBanned", banned);
            return data;
        }

        boolean hide = ACTION_HIDE.equals(action);
        resource.setModerationStatus(hide ? STATUS_HIDDEN : STATUS_NORMAL);
        resource.setModerationReason(normalize(reason));
        resource.setModeratedAt(LocalDateTime.now());
        resource.setModeratedBy(adminId);
        resourceMapper.updateById(resource);

        boolean banned = hide && Boolean.TRUE.equals(banAuthor)
                && banAuthorInternal(resource.getUploaderId(), reason, adminId);
        data.put("moderationStatus", resource.getModerationStatus());
        data.put("authorBanned", banned);
        return data;
    }

    // ===================== 封禁 / 解封 =====================

    @Override
    public List<BannedAuthorVO> listBannedAuthors() {
        List<User> banned = userService.list(new LambdaQueryWrapper<User>()
                .isNotNull(User::getBannedAt)
                .orderByDesc(User::getBannedAt));
        List<BannedAuthorVO> list = new ArrayList<>(banned.size());
        for (User user : banned) {
            BannedAuthorVO vo = new BannedAuthorVO();
            vo.setUserId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setFullName(user.getFullName());
            vo.setRole(user.getRole());
            vo.setBanReason(user.getBanReason());
            vo.setBannedAt(user.getBannedAt());
            vo.setBannedBy(user.getBannedBy());
            list.add(vo);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> banAuthor(Long userId, String reason, Long adminId) {
        if (!banAuthorInternal(userId, reason, adminId)) {
            throw new BusinessException(ApiCode.NOT_FOUND, "账号不存在：" + userId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("banned", true);
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unbanAuthor(Long userId) {
        User user = userService.getById(userId);
        if (user == null || user.getBannedAt() == null) {
            // 未封禁 / 非经审核封禁（如平台其它原因停用）一律不认，避免把别的停用状态"顺手解开"
            return false;
        }
        user.setIsActive(true);
        user.setBanReason(null);
        user.setBannedAt(null);
        user.setBannedBy(null);
        userService.updateById(user);
        return true;
    }

    /**
     * 封禁内核：复用 {@code is_active=0} 并写留痕三列。
     *
     * @return true=处置成功；false=账号不存在（由调用方决定是否抛 404）
     * @throws BusinessException 目标为 ADMIN 时（封禁治理者无意义，明确拒绝而非静默接受）
     */
    private boolean banAuthorInternal(Long userId, String reason, Long adminId) {
        if (userId == null) {
            return false;
        }
        User user = userService.getById(userId);
        if (user == null) {
            return false;
        }
        if (RoleConstants.ADMIN.equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "不能封禁管理员账号");
        }
        user.setIsActive(false);
        user.setBanReason(normalize(reason));
        user.setBannedAt(LocalDateTime.now());
        user.setBannedBy(adminId);
        userService.updateById(user);
        return true;
    }

    // ===================== 内部工具 =====================

    private Map<Long, User> loadUsers(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return new HashMap<>();
        }
        return userService.listByIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
