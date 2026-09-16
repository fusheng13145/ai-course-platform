package com.aicourse.mapper;

import com.aicourse.entity.CourseEnrollment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * CourseEnrollment 数据访问 Mapper 接口
 * 继承自 MyBatis-Plus 的 {@code BaseMapper}，提供该实体的基础 CRUD 操作。
 */
@Mapper
public interface CourseEnrollmentMapper extends BaseMapper<CourseEnrollment> {

    /**
     * 当前读（加行锁）统计某课程有效选课数，用于并发选课容量校验。
     *
     * <p>修复 Q-1 并发超卖：{@code enroll} 在 {@code @Transactional} 内已对课程行 {@code SELECT ... FOR UPDATE}
     * 串行化，但 {@code assertCapacity} 原用 MyBatis-Plus 的 {@code this.count(...)}（普通 SELECT）在
     * MySQL REPEATABLE READ 下读取事务快照，并发事务彼此看不到对方已提交的新增选课，
     * 导致「先 count 后 insert」全部放行形成超卖。此处用 {@code FOR UPDATE} 使计数成为当前读，
     * 与课程行锁共同保证「计数→插入」临界区的串行一致，杜绝超卖。</p>
     */
    @Select("SELECT COUNT(*) FROM course_enrollments WHERE course_id = #{courseId} AND is_active = 1 FOR UPDATE")
    long countActiveForUpdate(@Param("courseId") Long courseId);
}
