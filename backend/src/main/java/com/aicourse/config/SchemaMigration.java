package com.aicourse.config;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 启动时自愈式 schema 迁移。
 * 仅对"新增的可空列"做幂等补齐（先查 information_schema，缺失才 ALTER），
 * 不影响既有数据，也不破坏已存在的列。与 task_completions 的 @PostConstruct 自检思路一致。
 */
@Component
@Slf4j
public class SchemaMigration {


    private final JdbcTemplate jdbcTemplate;

    public SchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        try {
            ensureColumn("tasks", "late_penalty_per_day",
                    "DECIMAL(5,2) NULL DEFAULT 10.00 COMMENT '逾期每天扣分比例(%)'");
            ensureColumn("tasks", "max_submissions",
                    "INT NULL DEFAULT 0 COMMENT '最大提交次数，0表示不限'");
            ensureColumn("wrong_questions", "ai_analysis",
                    "TEXT NULL COMMENT 'AI 错因分析'");
            // 讨论区进阶（#70）：精华 / 楼层 / @提及
            ensureColumn("discussion_topics", "is_essence",
                    "TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否精华：0-否，1-是'");
            ensureColumn("discussion_posts", "floor",
                    "INT NULL COMMENT '楼层号，按话题内回复顺序递增'");
            ensureColumn("discussion_posts", "mentions",
                    "VARCHAR(500) NULL COMMENT '被@提及的用户ID列表，逗号分隔'");
        } catch (Exception e) {
            // 自检失败不应阻断启动；列可能已存在（information_schema 查询异常等）
            log.warn("SchemaMigration 自检异常（可忽略，若列已存在）: {}", e.getMessage());
        }
    }

    private void ensureColumn(String table, String column, String definition) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        if (cnt != null && cnt == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("SchemaMigration: 已为 {} 补齐列 {}", table, column);
        }
    }
}
