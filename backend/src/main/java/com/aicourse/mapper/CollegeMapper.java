package com.aicourse.mapper;

import com.aicourse.entity.College;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * College 数据访问 Mapper 接口
 */
@Mapper
public interface CollegeMapper extends BaseMapper<College> {
}
