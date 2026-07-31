package com.aicourse.controller;

import jakarta.validation.Valid;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.DiscussionTopic;
import com.aicourse.service.DiscussionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.aicourse.common.RoleConstants;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 课程讨论接口（话题 / 回复 / 点赞 / 置顶 / 关闭）
 * 基础路径：/api/discussion
 */
@RestController
@RequestMapping("/discussion")
@Tag(name = "讨论区")
public class DiscussionController {

    private final DiscussionService discussionService;

    public DiscussionController(DiscussionService discussionService) {
        this.discussionService = discussionService;
    }

    /** 从认证信息中提取角色（ROLE_TEACHER -> TEACHER） */
    private String roleOf(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse(RoleConstants.STUDENT);
    }

    @PostMapping("/topic")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    public ApiResponse<DiscussionTopic> createTopic(@Valid @RequestBody DiscussionTopic topic, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("发布成功", discussionService.createTopic(topic, userId, roleOf(auth)));
    }

    @GetMapping("/topic/course/{courseId}")
    public ApiResponse<List<DiscussionTopic>> listTopics(@PathVariable Long courseId,
                                                         @RequestParam(required = false) Long chapterId,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) Boolean essence) {
        return ApiResponse.success(discussionService.listTopics(courseId, chapterId, keyword, essence));
    }

    @GetMapping("/topic/{id}")
    public ApiResponse<Map<String, Object>> getTopic(@PathVariable Long id) {
        return ApiResponse.success(discussionService.getTopicDetail(id));
    }

    @PostMapping("/topic/{id}/reply")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    public ApiResponse<DiscussionPost> reply(@PathVariable Long id,
                                             @Valid @RequestBody DiscussionPost post,
                                             Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("回复成功", discussionService.reply(id, post, userId, roleOf(auth)));
    }

    @PostMapping("/post/{postId}/like")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    public ApiResponse<Map<String, Object>> likePost(@PathVariable Long postId, Authentication auth) {
        Map<String, Object> result = discussionService.likePost(postId, (Long) auth.getPrincipal());
        return ApiResponse.success("操作成功", result);
    }

    @PostMapping("/topic/{id}/close")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<DiscussionTopic> closeTopic(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success("已关闭",
                discussionService.closeTopic(id, (Long) auth.getPrincipal(), roleOf(auth)));
    }

    @PostMapping("/topic/{id}/pin")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<DiscussionTopic> pinTopic(@PathVariable Long id,
                                                 @RequestParam boolean pinned,
                                                 Authentication auth) {
        return ApiResponse.success("已更新",
                discussionService.pinTopic(id, pinned, (Long) auth.getPrincipal(), roleOf(auth)));
    }

    @PostMapping("/topic/{id}/essence")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<DiscussionTopic> markEssence(@PathVariable Long id,
                                                    @RequestParam boolean essence,
                                                    Authentication auth) {
        return ApiResponse.success("已更新",
                discussionService.markEssence(id, essence, (Long) auth.getPrincipal(), roleOf(auth)));
    }

    @DeleteMapping("/post/{postId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    public ApiResponse<Void> deletePost(@PathVariable Long postId, Authentication auth) {
        discussionService.deletePost(postId, (Long) auth.getPrincipal(), roleOf(auth));
        return ApiResponse.success("已删除");
    }
}
