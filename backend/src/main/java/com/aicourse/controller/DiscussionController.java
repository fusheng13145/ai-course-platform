package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.DiscussionTopic;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
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
    private final CourseAccessControl courseAccessControl;

    public DiscussionController(DiscussionService discussionService,
                                CourseAccessControl courseAccessControl) {
        this.discussionService = discussionService;
        this.courseAccessControl = courseAccessControl;
    }

    /**
     * 课程成员关系断言（2026-11-06 验收批，B-05 对象级 ACL）。
     * <p>
     * 讨论区的读、发帖、回复原先只有 URL 层「已登录」兜底：实测未选任何课程的学生
     * 可以读到别人课程的话题与正文。这里复用
     * {@link CourseAccessControl#assertCanReadCourse}（学生须有 APPROVED 选课、
     * 教师须是本课程、管理员放行），不再另写一份成员关系判定。
     */
    private void assertCourseMember(Long courseId, Authentication auth) {
        if (courseId == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "缺少课程ID");
        }
        courseAccessControl.assertCanReadCourse(courseId, auth);
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
        assertCourseMember(topic.getCourseId(), auth);
        return ApiResponse.success("发布成功", discussionService.createTopic(topic, userId, roleOf(auth)));
    }

    /** 查询某课程下的讨论话题列表，支持按章节、关键词、精华筛选 */
    @GetMapping("/topic/course/{courseId}")
    public ApiResponse<List<DiscussionTopic>> listTopics(@PathVariable Long courseId,
                                                         @RequestParam(required = false) Long chapterId,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) Boolean essence,
                                                         Authentication auth) {
        assertCourseMember(courseId, auth);
        return ApiResponse.success(discussionService.listTopics(courseId, chapterId, keyword, essence));
    }

    /** 获取单个话题的完整详情（含回复与统计） */
    @GetMapping("/topic/{id}")
    public ApiResponse<TopicDetailVO> getTopic(@PathVariable Long id, Authentication auth) {
        assertCourseMembershipOfTopic(id, auth);
        return ApiResponse.success(discussionService.getTopicDetail(id));
    }

    /** 在指定话题下发表一条回复 */
    @PostMapping("/topic/{id}/reply")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    public ApiResponse<DiscussionPost> reply(@PathVariable Long id,
                                             @Valid @RequestBody DiscussionPost post,
                                             Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        assertCourseMembershipOfTopic(id, auth);
        return ApiResponse.success("回复成功", discussionService.reply(id, post, userId, roleOf(auth)));
    }

    /**
     * 按话题归属课程判成员关系。话题不存在时**不在此处判**——留给服务层原有的
     * 「话题不存在」语义，避免把 404 类结果改写成 400。
     */
    private void assertCourseMembershipOfTopic(Long topicId, Authentication auth) {
        DiscussionTopic topic = discussionService.getById(topicId);
        if (topic != null) {
            assertCourseMember(topic.getCourseId(), auth);
        }
    }

    /**
     * 以 postId 为入口的操作（点赞）同口径：先取回复所属课程，再判成员关系。
     * 关系链缺失（回复或话题不存在）时不在此处判，交由服务层保持原有「不存在」语义。
     */
    private void assertCourseMembershipOfPost(Long postId, Authentication auth) {
        Long courseId = discussionService.courseIdOfPost(postId);
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
        }
    }

    /** 对指定回复点赞或取消点赞（切换式） */
    @PostMapping("/post/{postId}/like")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    public ApiResponse<LikePostVO> likePost(@PathVariable Long postId, Authentication auth) {
        assertCourseMembershipOfPost(postId, auth);
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
