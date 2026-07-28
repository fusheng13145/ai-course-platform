package com.aicourse.mapper;

import com.aicourse.entity.TaskCompletion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 任务点完成记录 Mapper
 */
@Mapper
public interface TaskCompletionMapper extends BaseMapper<TaskCompletion> {

    /**
     * 自愈合建表：后端启动时确保 task_completions 存在。
     * 使用 IF NOT EXISTS 保证幂等；仅在表不存在时创建，不破坏既有数据。
     */
    @Update("CREATE TABLE IF NOT EXISTS task_completions (" +
            " id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            " student_id BIGINT NOT NULL," +
            " task_id BIGINT NOT NULL," +
            " course_id BIGINT NULL," +
            " chapter_id BIGINT NULL," +
            " task_type VARCHAR(32) NULL," +
            " completion_type VARCHAR(32) NULL," +
            " watched_duration INT NULL," +
            " last_position INT NULL," +
            " total_duration INT NULL," +
            " score DECIMAL(10,2) NULL," +
            " completed TINYINT(1) NOT NULL DEFAULT 0," +
            " completed_at DATETIME NULL," +
            " created_at DATETIME NULL," +
            " updated_at DATETIME NULL," +
            " UNIQUE KEY uk_task_completion (student_id, task_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4")
    void createTableIfNotExists();
}
