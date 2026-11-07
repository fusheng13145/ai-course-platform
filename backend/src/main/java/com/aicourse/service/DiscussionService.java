package com.aicourse.service;

import com.aicourse.entity.DiscussionPost;
import com.aicourse.entity.DiscussionTopic;
import com.aicourse.vo.LikePostVO;
import com.aicourse.vo.TopicDetailVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 课程讨论服务
 */
public interface DiscussionService extends IService<DiscussionTopic> {

    /**
     * 发起讨论话题
     */
    DiscussionTopic createTopic(DiscussionTopic topic, Long userId, String role);

    /**
     * 按课程/章节/关键词列出话题（置顶优先、精华优先、按最后回复时间倒序）
     * @param essence 仅筛选精华帖（null 表示不筛选）
     */
    List<DiscussionTopic> listTopics(Long courseId, Long chapterId, String keyword, Boolean essence);

    /**
     * 话题详情：包含主题与全部回复
     */
    TopicDetailVO getTopicDetail(Long id);

    /**
     * 回复话题（同步维护话题回复数与最后回复时间）
     */
    DiscussionPost reply(Long topicId, DiscussionPost post, Long userId, String role);

    /**
     * 点赞/取消点赞回复（修复 P1-5：按用户去重，同一用户对同一回复仅计一次，再次点击取消）
     * @return 含 likeCount（最新点赞数）与 liked（当前用户是否已赞）的 Map
     */
    LikePostVO likePost(Long postId, Long userId);

    /**
     * 取某条回复所属课程的ID（用于直链之外的对象级 ACL 判定，2026-11-06 验收批）。
     * <p>点赞、编辑等以 postId 为入口的操作原先只能判「已登录」，于是任意用户可给
     * 他人课程里的回复计数。本方法只做关系查询，不重复实现成员关系规则——
     * 判定仍由 {@code CourseAccessControl} 负责。</p>
     *
     * @return 回复不存在或话题缺失时返回 null（调用方据此放行到服务层，保持原有「不存在」语义）
     */
    Long courseIdOfPost(Long postId);

    /**
     * 关闭话题（仅发起人或管理员）
     */
    DiscussionTopic closeTopic(Long id, Long userId, String role);

    /**
     * 置顶/取消置顶（仅发起人或管理员）
     */
    DiscussionTopic pinTopic(Long id, boolean pinned, Long userId, String role);

    /**
     * 标记/取消精华（仅教师或管理员，对标超星精华帖）
     */
    DiscussionTopic markEssence(Long id, boolean essence, Long userId, String role);

    /**
     * 删除回复（作者本人或管理员），同步减少话题回复数
     */
    void deletePost(Long postId, Long userId, String role);

    /**
     * 删除话题（作者本人或管理员），级联清理该话题下全部回复与点赞
     */
    void deleteTopic(Long id, Long userId, String role);
}
