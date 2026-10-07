package com.aicourse.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 演示种子与生产迁移的隔离契约（手册 §24.5 / 附录 D DR-18 路径①）。
 *
 * <p>迁移脚本按目录分两层：{@code db/migration} 只有结构迁移（建表与状态规范化），
 * {@code db/seed-demo} 是演示语料（V5 富种子、V6 示范课 4007、V7 示范课文案修正，
 * 含 162 个号段演示账号）。
 * 生产 profile 的 {@code spring.flyway.locations} 只保留前者，因此全新生产库首启
 * 不会把演示数据写进库——这是「演示数据不进生产库」这条定案的机械保证。
 *
 * <p>本用例刻意不连数据库：它钉的是类路径布局与配置值。任一被改动都会在运行期
 * 造成「生产库混入演示数据」或「开发环境丢演示语料」，因此必须在构建期就红。
 *
 * <p>读的是类路径资源（{@code target/classes} 下 Maven 过滤后的副本），与 Flyway 运行期
 * 看到的视图一致。若因增量构建残留旧目录而失败，用 {@code mvn clean} 重跑。
 */
@DisplayName("演示种子迁移隔离（DR-18①）")
class DemoSeedMigrationIsolationTest {

    /** Flyway 版本化脚本命名：{@code V<版本>__<描述>.sql}。 */
    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+)__.+\\.sql$");

    /** 结构迁移允许的最高版本号：超过即说明演示种子被放回了 db/migration。 */
    private static final int MAX_STRUCTURAL_VERSION = 4;

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    @Test
    @DisplayName("db/migration 只含结构迁移，版本号不得超过 4")
    void structuralLocationHoldsNoDemoSeed() throws IOException {
        Set<String> files = filenames("classpath:db/migration/*.sql");
        assertThat(files).contains("V1__init_schema.sql");

        List<Integer> versions = files.stream()
                .map(DemoSeedMigrationIsolationTest::versionOf)
                .sorted()
                .toList();
        assertThat(versions).isNotEmpty().allMatch(v -> v <= MAX_STRUCTURAL_VERSION);
    }

    @Test
    @DisplayName("演示种子脚本落在 db/seed-demo，文件名逐字保持 V5/V6/V7")
    void demoSeedLocationHoldsExactlyTheSeedScripts() throws IOException {
        assertThat(filenames("classpath:db/seed-demo/*.sql"))
                .containsExactlyInAnyOrder(
                        "V5__rich_seed_data.sql",
                        "V6__computer_network_showcase.sql",
                        // V7：DR-19 的文案前向修正。V6 已应用、校验和不可改，
                        // 故库内示范课简介的规模数字只能由新迁移改写。
                        "V7__fix_showcase_seed_self_description.sql");
    }

    @Test
    @DisplayName("开发默认双 location：结构迁移 + 演示种子")
    void baseConfigStillLoadsDemoSeedForDev() {
        assertThat(locations("application.yaml"))
                .containsExactly("classpath:db/migration", "classpath:db/seed-demo");
    }

    @Test
    @DisplayName("prod 单 location：演示种子不进生产库")
    void prodConfigExcludesDemoSeedLocation() {
        assertThat(locations("application-prod.yml"))
                .containsExactly("classpath:db/migration");
    }

    @Test
    @DisplayName("prod 同时关闭演示账号开关：兜住 V1 内置的 8 个种子账号")
    void prodConfigHardensV1BuiltinAccounts() {
        // V1 已 baseline、脚本校验和不可改，其内置演示账号只能靠运行期加固收口。
        assertThat(load("application-prod.yml").getProperty("app.demo-accounts.enabled"))
                .isEqualTo("false");
    }

    private static List<String> locations(String yamlFile) {
        String raw = load(yamlFile).getProperty("spring.flyway.locations");
        assertThat(raw).as("%s 必须显式声明 spring.flyway.locations", yamlFile).isNotBlank();
        return List.of(raw.split("\\s*,\\s*"));
    }

    private static Properties load(String yamlFile) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(yamlFile));
        factory.afterPropertiesSet();
        Properties properties = factory.getObject();
        assertThat(properties).as("%s 解析失败", yamlFile).isNotNull();
        return properties;
    }

    private Set<String> filenames(String pattern) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        for (Resource resource : resolver.getResources(pattern)) {
            names.add(resource.getFilename());
        }
        return names;
    }

    private static int versionOf(String filename) {
        Matcher matcher = VERSIONED.matcher(filename);
        if (!matcher.matches()) {
            throw new AssertionError("不符合 Flyway 版本化命名：" + filename);
        }
        return Integer.parseInt(matcher.group(1));
    }
}
