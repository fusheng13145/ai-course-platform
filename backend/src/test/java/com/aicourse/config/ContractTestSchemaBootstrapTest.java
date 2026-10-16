package com.aicourse.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 契约测试库的结构自愈清单守卫（2026-10-13 新增）。
 *
 * <p><b>背景（真实缺陷，不是假想）</b>：契约测试 profile 刻意关闭 Flyway，改由
 * {@code spring.sql.init.schema-locations} 重建测试库。该清单此前**只列 V1**，
 * 于是 V2 起的全部结构迁移在契约测试库里从未生效——缺口自 V8 起就潜伏，
 * 直到 V11 给 {@code users} 加列才爆发：`User` 实体会 SELECT 新列，
 * 而测试库没有这些列，{@code ApiContractTest} 11 个用例在**上下文加载阶段**
 * 全部报 {@code Unknown column 'ban_reason' in 'field list'}。</p>
 *
 * <p>本用例把"清单必须覆盖 db/migration 下每一个结构迁移、且不得打乱版本顺序"机械钉住：
 * 今后新增结构迁移若忘记同步该清单，构建期即红，而不是等到某个实体恰好用到那个列时才炸。</p>
 */
@DisplayName("契约测试库结构自愈清单守卫")
class ContractTestSchemaBootstrapTest {

    private static final String CONFIG = "application-contract-test.yml";
    private static final String MIGRATION_DIR = "classpath:db/migration/";

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    @Test
    @DisplayName("schema-locations 必须覆盖 db/migration 下全部结构迁移（漏列即红）")
    void schemaLocationsCoverEveryStructuralMigration() throws IOException {
        String raw = readConfig();
        Set<String> structural = filenames(MIGRATION_DIR + "*.sql");
        assertThat(structural).contains("V1__init_schema.sql").hasSizeGreaterThan(1);

        for (String file : structural) {
            assertThat(raw)
                    .as("契约测试库的 schema-locations 漏列了结构迁移：%s（新增迁移必须同步该清单）", file)
                    .contains("db/migration/" + file);
        }
    }

    @Test
    @DisplayName("版本顺序不得打乱：V1 必须排在其余结构迁移之前（先建基线再演进）")
    void v1ComesFirst() throws IOException {
        String raw = readConfig();
        int v1 = raw.indexOf("db/migration/V1__init_schema.sql");
        assertThat(v1).as("V1 必须在清单里出现").isGreaterThanOrEqualTo(0);

        for (String file : filenames(MIGRATION_DIR + "*.sql")) {
            if (file.startsWith("V1__")) {
                continue;
            }
            int idx = raw.indexOf("db/migration/" + file);
            assertThat(idx)
                    .as("结构迁移 %s 必须排在 V1 之后（脚本按序执行，V1 负责建基线）", file)
                    .isGreaterThan(v1);
        }
    }

    /**
     * 覆盖 2026-10-16 实测缺陷：V1 的 DROP 清单冻结在其编写时点，不含后加迁移新建的表，
     * 测试库**第二次**重建会在 V8 的无 IF NOT EXISTS 的 CREATE TABLE 处报
     * {@code Table 'ai_call_logs' already exists}，4 个 @SpringBootTest 集成类上下文加载全败。
     * 本用例把"后加表必须可从测试库清掉（V1 清单或前置清理脚本二选一覆盖）"钉死。
     */
    @Test
    @DisplayName("后加表必须可清理：V2+ 的每个 CREATE TABLE 都要有对应的幂等 DROP")
    void postV1TablesAreDroppableBeforeRecreate() throws IOException {
        Set<String> droppable = new LinkedHashSet<>();
        droppable.addAll(droppedTables(readResource("db/migration/V1__init_schema.sql")));
        droppable.addAll(droppedTables(readResource(DROP_ADDENDUM)));

        assertThat(droppable)
                .as("V1 与前置清理脚本都没解析出任何 DROP——解析逻辑可能已失效，护栏形同虚设")
                .isNotEmpty();

        List<String> missing = new java.util.ArrayList<>();
        for (Resource resource : resolver.getResources(MIGRATION_DIR + "*.sql")) {
            String file = resource.getFilename();
            if (file.startsWith("V1__")) {
                continue;
            }
            for (String table : createdTables(readResource("db/migration/" + file))) {
                if (!droppable.contains(table)) {
                    missing.add(file + " 建的 " + table + " 不在可清理清单里");
                }
            }
        }

        assertThat(missing)
                .as("以下表在测试库重建时会撞名（already exists）：请把 DROP TABLE IF EXISTS 加进 %s。%n%s",
                        DROP_ADDENDUM, String.join(System.lineSeparator(), missing))
                .isEmpty();
    }

    /** V2+ 建表必须可清理；前置清理脚本路径（测试专属资源，刻意不放进 db/migration 以免 Flyway 误扫）。 */
    private static final String DROP_ADDENDUM = "contract-test/00-drop-post-v1-tables.sql";

    private String readResource(String classpathLocation) throws IOException {
        try (var in = new ClassPathResource(classpathLocation).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 收集 SQL 中的 CREATE TABLE 目标表名（忽略行注释；小写归一）。 */
    private Set<String> createdTables(String sql) {
        return matchTableNames(sql, "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)");
    }

    /** 收集 SQL 中的 DROP TABLE 目标表名（忽略行注释；小写归一）。 */
    private Set<String> droppedTables(String sql) {
        return matchTableNames(sql, "DROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?(\\w+)");
    }

    private Set<String> matchTableNames(String sql, String regex) {
        Set<String> names = new LinkedHashSet<>();
        String noComments = sql.replaceAll("(?m)--.*$", "");
        var matcher = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(noComments);
        while (matcher.find()) {
            names.add(matcher.group(1).toLowerCase());
        }
        return names;
    }

    private String readConfig() throws IOException {
        try (var in = new ClassPathResource(CONFIG).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Set<String> filenames(String pattern) throws IOException {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (Resource resource : resolver.getResources(pattern)) {
            names.add(resource.getFilename());
        }
        return names;
    }
}
