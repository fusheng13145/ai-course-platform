package com.aicourse.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
