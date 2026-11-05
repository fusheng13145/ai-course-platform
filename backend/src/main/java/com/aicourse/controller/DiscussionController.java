package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.DiscussionTopic;
import com.aicourse.service.DiscussionService;
import com.aicourse.vo.LikePostVO;
import com.aicourse.vo.TopicDetailVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    private static final Logger log = LoggerFactory.getLogger(DiscussionController.class);

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

    /** 当前登录用户发布一个新的讨论话题 */
    @PostMapping("/topic")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    public ApiResponse<DiscussionTopic> createTopic(@Valid @RequestBody DiscussionTopic topic, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("发布成功", discussionService.createTopic(topic, userId, roleOf(auth)));
    }

    /** 查询某课程下的讨论话题列表，支持按章节、关键词、精华筛选 */
    @GetMapping("/topic/course/{courseId}")
    public ApiResponse<List<DiscussionTopic>> listTopics(@PathVariable Long courseId,
                                                         @RequestParam(required = false) Long chapterId,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) Boolean essence) {
        return ApiResponse.success(discussionService.listTopics(courseId, chapterId, keyword, essence));
    }

    /** 获取单个话题的完整详情（含回复与统计） */
    @GetMapping("/topic/{id}")
    public ApiResponse<TopicDetailVO> getTopic(@PathVariable Long id) {
        return ApiResponse.success(discussionService.getTopicDetail(id));
    }

    /** 在指定话题下发表一条回复 */
    @PostMapping("/topic/{id}/reply")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    public ApiResponse<DiscussionPost> reply(@PathVariable Long id,
                                             @Valid @RequestBody DiscussionPost post,
                                             Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("回复成功", discussionService.reply(id, post, userId, roleOf(auth)));
    }

    /** 对指定回复点赞或取消点赞（切换式） */
    @PostMapping("/post/{postId}/like")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    public ApiResponse<LikePostVO> likePost(@PathVariable Long postId, Authentication auth) {
        LikePostVO result = discussionService.likePost(postId, (Long) auth.getPrincipal());
        return ApiResponse.success("操作成功", result);
    }

    /** 关闭或重新开启某个话题（教师/管理员） */
    @PostMapping("/topic/{id}/close")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<DiscussionTopic> closeTopic(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success("已关闭",
                discussionService.closeTopic(id, (Long) auth.getPrincipal(), roleOf(auth)));
    }

    /** 置顶或取消置顶某个话题（教师/管理员） */
    @PostMapping("/topic/{id}/pin")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<DiscussionTopic> pinTopic(@PathVariable Long id,
                                                 @RequestParam boolean pinned,
                                                 Authentication auth) {
        return ApiResponse.success("已更新",
                discussionService.pinTopic(id, pinned, (Long) auth.getPrincipal(), roleOf(auth)));
    }

    /** 标记或取消标记某个话题为精华（教师/管理员） */
    @PostMapping("/topic/{id}/essence")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<DiscussionTopic> markEssence(@PathVariable Long id,
                                                    @RequestParam boolean essence,
                                                    Authentication auth) {
        return ApiResponse.success("已更新",
                discussionService.markEssence(id, essence, (Long) auth.getPrincipal(), roleOf(auth)));
    }

    /** 删除某条回复，仅作者、教师或管理员可操作 */
    @DeleteMapping("/post/{postId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    public ApiResponse<Void> deletePost(@PathVariable Long postId, Authentication auth) {
        discussionService.deletePost(postId, (Long) auth.getPrincipal(), roleOf(auth));
        return ApiResponse.success("已删除");
    }

    /** 删除整个话题，仅作者或管理员可操作，级联清理回复与点赞 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    public ApiResponse<Void> deleteTopic(@PathVariable Long id, Authentication auth) {
        discussionService.deleteTopic(id, (Long) auth.getPrincipal(), roleOf(auth));
        return ApiResponse.success("已删除");
    }
}
