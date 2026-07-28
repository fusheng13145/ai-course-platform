package com.aicourse.mapper;

import com.aicourse.entity.DiscussionPost;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 讨论回复 Mapper
 */
@Mapper
public interface DiscussionPostMapper extends BaseMapper<DiscussionPost> {
}
