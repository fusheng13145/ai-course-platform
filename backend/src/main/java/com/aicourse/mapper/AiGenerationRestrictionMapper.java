package com.aicourse.mapper;

import com.aicourse.entity.AiGenerationRestriction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 教师 AI 出题限制名单 Mapper
 */
@Mapper
public interface AiGenerationRestrictionMapper extends BaseMapper<AiGenerationRestriction> {
}
