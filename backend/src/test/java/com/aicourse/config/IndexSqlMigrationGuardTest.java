package com.aicourse.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code index.sql} 与结构迁移的一致性护栏（2026-10-17 新增，手册 附录 D DR-61 处置①）。
 *
 * <p><b>背景（真实缺陷，不是假想）</b>：{@code index.sql} 是「开发环境全量初始化」脚本，
 * 也是 CI 的 {@code eval-nightly.yml} 建库唯一数据源（它把本脚本直接灌进 MySQL）。
 * 但 V8/V9/V10/V11 的结构变化只写进了 {@code db/migration}，本脚本停留在 V1 时点：
 * 缺 {@code semesters}、{@code ai_call_logs}、{@code ai_generation_restrictions} 三张表，
 * 还缺 V8/V11 给既有表加的 12 个列。走 index.sql 建出来的库因此
 * 既查不到新表、也 SELECT 不到新列——与 2026-10-13 那次
 * {@code Unknown column 'ban_reason'} 是同一类坑，只是换了个入口。</p>
 *
 * <p>本用例钉的是<b>集合包含关系</b>而不是具体数字：迁移随时可以再加表加列，
 * 只要 index.sql 没跟上就红。刻意不连数据库（纯资源解析），因此不需要 Docker。</p>
 */
@DisplayName("index.sql 与结构迁移一致性护栏（DR-61）")
class IndexSqlMigrationGuardTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path INDEX_SQL = RESOURCES.resolve("index.sql");
    private static final Path MIGRATION_DIR = RESOURCES.resolve("db/migration");

    /** CREATE TABLE 块：从表名后的左括号到行首的右括号（块内不存在以 ) 开头的嵌套行）。 */
    private static final Pattern CREATE_BLOCK = Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)`?\\s*\\((.*?)\\n\\)");

    /** 列定义行：首 token 为列名、次 token 为已知类型；据此排除 PRIMARY KEY / KEY / CONSTRAINT / INDEX 等行。 */
    private static final Pattern COLUMN_LINE = Pattern.compile(
            "(?i)^\\s*`?(\\w+)`?\\s+(?:bigint|int|smallint|tinyint|mediumint|varchar|char|text|mediumtext|"
                    + "longtext|tinytext|datetime|date|time|timestamp|decimal|numeric|double|float|json|blob|"
                    + "binary|enum|set)\\b");

    private static final Pattern ALTER_STATEMENT = Pattern.compile(
            "(?is)ALTER\\s+TABLE\\s+`?(\\w+)`?\\s+(.*?);");

    /** ADD 后面允许出现的非列关键字：{@code ADD INDEX xxx (col)} 里要收的是索引子句，不是列。 */
    private static final Set<String> NON_COLUMN_ADD = Set.of(
            "index", "key", "constraint", "primary", "unique", "fulltext", "spatial", "check", "foreign");

    private final Map<String, Set<String>> indexColumns = new LinkedHashMap<>();
    private final Set<String> indexDrops = new LinkedHashSet<>();

    IndexSqlMigrationGuardTest() throws IOException {
        assertThat(INDEX_SQL)
                .as("%s 必须能从 backend/ 工作目录读到（surefire 的 basedir 就是模块根）", INDEX_SQL)
                .exists();
        String raw = stripComments(Files.readString(INDEX_SQL, StandardCharsets.UTF_8));
        Matcher block = CREATE_BLOCK.matcher(raw);
        while (block.find()) {
            indexColumns.put(block.group(1).toLowerCase(Locale.ROOT), columnNames(block.group(2)));
        }
        Matcher drop = Pattern.compile("(?i)DROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?`?(\\w+)`?")
                .matcher(raw);
        while (drop.find()) {
            indexDrops.add(drop.group(1).toLowerCase(Locale.ROOT));
        }
    }

    @Test
    @DisplayName("解析器自检：CREATE 块与列都要能解析出来，否则护栏形同虚设")
    void parserExtractsSomething() {
        assertThat(indexColumns)
                .as("index.sql 一个 CREATE TABLE 都没解析出来——正则已失效，本用例不能算通过")
                .isNotEmpty();
        assertThat(indexColumns.keySet())
                .as("index.sql 里存在解析出 0 列的表——列定义行的类型名单需要补充")
                .noneMatch(table -> indexColumns.get(table).isEmpty());
    }

    @Test
    @DisplayName("index.sql 的表集合必须 ⊇ db/migration 建出的全部表")
    void indexSqlCoversEveryMigratedTable() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Set<String> created : migratedTables().values()) {
            for (String table : created) {
                if (!indexColumns.containsKey(table)) {
                    missing.add(table);
                }
            }
        }
        assertThat(missing)
                .as("以下表只存在于 db/migration，index.sql 没有：建库走 index.sql 的环境（开发全量初始化、"
                        + "CI eval-nightly）会直接缺表。请把 CREATE TABLE 同步进 index.sql。%n%s",
                        String.join(System.lineSeparator(), missing))
                .isEmpty();
    }

    @Test
    @DisplayName("迁移里每个 ALTER TABLE … ADD COLUMN 都必须已落进 index.sql 的建表定义")
    void indexSqlCarriesEveryAddedColumn() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path script : migrationScripts()) {
            String raw = stripComments(Files.readString(script, StandardCharsets.UTF_8));
            Matcher alter = ALTER_STATEMENT.matcher(raw);
            while (alter.find()) {
                String table = alter.group(1).toLowerCase(Locale.ROOT);
                Set<String> columns = indexColumns.get(table);
                if (columns == null) {
                    continue; // 表本身的缺失由上一条用例负责，避免同一根因报两遍
                }
                Matcher add = Pattern.compile("(?i)ADD\\s+(?:COLUMN\\s+)?`?(\\w+)`?")
                        .matcher(alter.group(2));
                while (add.find()) {
                    String column = add.group(1).toLowerCase(Locale.ROOT);
                    if (!NON_COLUMN_ADD.contains(column) && !columns.contains(column)) {
                        missing.add(script.getFileName() + " 给 " + table + " 加的列 " + column);
                    }
                }
            }
        }
        assertThat(missing)
                .as("index.sql 的建表定义落后于迁移加列（走 index.sql 建库会报 Unknown column）：%n%s",
                        String.join(System.lineSeparator(), missing))
                .isEmpty();
    }

    @Test
    @DisplayName("index.sql 自身闭合：每张自建表都要有对应的 DROP TABLE IF EXISTS")
    void indexSqlDropsEveryTableItCreates() {
        List<String> undroppable = indexColumns.keySet().stream()
                .filter(table -> !indexDrops.contains(table))
                .sorted()
                .toList();
        assertThat(undroppable)
                .as("index.sql 建了但没有 DROP 的表（重复执行本脚本会在无 IF NOT EXISTS 的建表处失败）：%n%s",
                        String.join(System.lineSeparator(), undroppable))
                .isEmpty();
    }

    /** 各迁移脚本新建的表名（小写），key 为脚本文件名；V1 之外的脚本才是「index.sql 可能落后」的来源。 */
    private Map<String, Set<String>> migratedTables() throws IOException {
        Map<String, Set<String>> byScript = new LinkedHashMap<>();
        for (Path script : migrationScripts()) {
            String raw = stripComments(Files.readString(script, StandardCharsets.UTF_8));
            Set<String> created = new LinkedHashSet<>();
            Matcher createdMatcher = Pattern
                    .compile("(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)`?")
                    .matcher(raw);
            while (createdMatcher.find()) {
                created.add(createdMatcher.group(1).toLowerCase(Locale.ROOT));
            }
            byScript.put(script.getFileName().toString(), created);
        }
        assertThat(byScript)
                .as("db/migration 下没读到任何迁移脚本：目录位置变了，护栏需要跟着改")
                .isNotEmpty();
        return byScript;
    }

    private List<Path> migrationScripts() throws IOException {
        try (var stream = Files.list(MIGRATION_DIR)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
    }

    private Set<String> columnNames(String blockBody) {
        Set<String> columns = new LinkedHashSet<>();
        for (String line : blockBody.split("\n")) {
            Matcher matcher = COLUMN_LINE.matcher(line);
            if (matcher.find()) {
                columns.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    /** 只去行注释：本仓库的 DDL 没有块注释，且列注释里会出现「CREATE TABLE」等字样，留着必然误报。 */
    private String stripComments(String sql) {
        return sql.replaceAll("(?m)--.*$", "");
    }
}
