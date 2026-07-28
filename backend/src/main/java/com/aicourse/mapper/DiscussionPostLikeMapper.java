package com.aicourse.mapper;

import com.aicourse.entity.DiscussionPostLike;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 讨论回复点赞 Mapper（修复 P1-5：点赞去重）
 */
@Mapper
public interface DiscussionPostLikeMapper extends BaseMapper<DiscussionPostLike> {
}
