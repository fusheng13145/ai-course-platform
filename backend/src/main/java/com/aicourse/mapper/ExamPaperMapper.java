package com.aicourse.mapper;

import com.aicourse.entity.ExamPaper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * ExamPaper 数据访问 Mapper 接口
 * 继承自 MyBatis-Plus 的 {@code BaseMapper}，提供该实体的基础 CRUD 操作与悲观锁读取。
 */
@Mapper
public interface ExamPaperMapper extends BaseMapper<ExamPaper> {

    /**
     * 悲观行锁读取：在事务内对指定试卷行加 {@code FOR UPDATE}，
     * 使同一试卷的并发开考串行化，杜绝「先 count 后 insert」的尝试次数/活跃会话双开竞争。
     * 必须在 {@code @Transactional} 内调用，锁随事务提交释放（范式同 CourseMapper.selectByIdForUpdate）。
     */
    @Select("SELECT * FROM exam_papers WHERE id = #{id} FOR UPDATE")
    ExamPaper selectByIdForUpdate(@Param("id") Long id);
}
