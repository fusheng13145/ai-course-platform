package com.aicourse.mapper;

import com.aicourse.entity.ExamPaper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ExamPaper 数据访问 Mapper 接口。
 *
 * <p>R2 起只保留 MyBatis-Plus 基础 CRUD：开考不再对试卷行加悲观锁，
 * 并发正确性由 exam_sessions 的 (student_id, exam_paper_id, attempt_number) 唯一键承担。</p>
 */
@Mapper
public interface ExamPaperMapper extends BaseMapper<ExamPaper> {
}
