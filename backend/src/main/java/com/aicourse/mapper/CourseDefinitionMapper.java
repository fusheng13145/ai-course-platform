package com.aicourse.mapper;

import com.aicourse.entity.CourseDefinition;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程库定义数据访问。
 */
@Mapper
public interface CourseDefinitionMapper extends BaseMapper<CourseDefinition> {
}
