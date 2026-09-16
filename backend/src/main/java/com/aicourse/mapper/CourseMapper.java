package com.aicourse.mapper;

import com.aicourse.entity.Course;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Course 数据访问 Mapper 接口
 * 继承自 MyBatis-Plus 的 {@code BaseMapper}，提供该实体的基础 CRUD 操作。
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    /**
     * 悲观行锁读取：在事务内对指定课程行加 {@code FOR UPDATE}，
     * 使并发选课/容量校验串行化，杜绝「先 count 后 insert」的选课超卖竞争（修复 Q-1）。
     * 必须在 {@code @Transactional} 内调用，锁随事务提交释放。
     */
    @Select("SELECT * FROM courses WHERE id = #{id} FOR UPDATE")
    Course selectByIdForUpdate(@Param("id") Long id);
}
