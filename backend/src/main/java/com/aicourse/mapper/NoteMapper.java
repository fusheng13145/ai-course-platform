package com.aicourse.mapper;

import com.aicourse.entity.Note;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 笔记 Mapper
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    /**
     * 自愈合建表：后端启动时确保 notes 表存在。
     * 使用 IF NOT EXISTS 保证幂等；仅在表不存在时创建，不破坏既有数据。
     * 与 index.sql 中的 CREATE TABLE notes 保持一致，确保全新部署与已运行实例均可正常工作。
     */
    @Update("CREATE TABLE IF NOT EXISTS notes (" +
            " id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            " user_id BIGINT NOT NULL," +
            " course_id BIGINT NULL," +
            " chapter_id BIGINT NULL," +
            " knowledge_point_id BIGINT NULL," +
            " title VARCHAR(255) NULL," +
            " content TEXT NOT NULL," +
            " is_favorite TINYINT(1) NOT NULL DEFAULT 0," +
            " created_at DATETIME NULL," +
            " updated_at DATETIME NULL," +
            " KEY idx_notes_user_id (user_id)," +
            " KEY idx_notes_knowledge_point_id (knowledge_point_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")
    void createTableIfNotExists();
}
