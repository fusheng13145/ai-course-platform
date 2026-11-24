package com.aicourse.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 基址默认值守卫：{@code @Value} 里的 base-url 兜底值不得写死引擎端口。
 *
 * <p><b>为什么需要</b>（2026-11-24 阶段4 复核发现）：{@code application.yaml} 已把
 * {@code knowledge-graph/rag/deepgraph-rag/fine-tune} 四个 base-url 默认改成 Engine Hub 单口
 * {@code :8600}，但 5 个类在 {@code @Value} 注解里又各写了一份
 * {@code "${xxx.base-url:http://localhost:8001}"} 这类兜底字面量（共 8 处）。
 * 配置缺失时注解兜底即生效，于是同一个基址有了两个来源：改 yaml 的人以为改全了，
 * 缺配置的那条路径仍打旧端口 —— 分离布局下端口不在听，表现为"开关是 true、地址打空"，
 * 与端口统一前完全同型的静默降级。这就是 {@code AGENTS.md} §4.3「引第二套看起来一样的实现」。</p>
 *
 * <p><b>修法口径</b>：兜底值统一引用 {@code ContractPaths.*_DEFAULT_BASE_URL}（编译期常量，
 * 注解允许常量拼接），让"默认地址"只有一个事实源；分离布局由启动器注入环境变量覆盖，
 * 不回改常量。</p>
 *
 * <p><b>检测口径</b>：只扫 {@code @Value} 注解内、键名含 {@code base-url} 或 {@code .url} 的片段，
 * 因此注释里提到的 {@code localhost:8501}、以及 {@code server.port} 之类的非基址配置都不会误报。
 * 端口只匹配 {@code 8xxx}（引擎与后端段），避免把数据库 3306、Ollama 11434 判成违规。</p>
 *
 * <p><b>阴性对照</b>（{@code AGENTS.md} §10.4）：{@link #detectorCatchesSyntheticViolation()}
 * 用一段人造违规注解证明检测器能抓到问题；同时用"走常量的写法"与"只在注释里写端口"证明不误报，
 * 避免守卫因为正则写错而恒绿或恒红。</p>
 */
@DisplayName("基址默认值守卫：@Value 的 base-url 兜底不得写死端口")
class BaseUrlDefaultGuardTest {

    /** 相对模块根（Maven surefire 的工作目录是 backend/）。 */
    private static final Path MAIN_SOURCE_DIR = Paths.get("src", "main", "java");

    /** 注解兜底里被禁止的硬编码基址：只抓 8xxx 段的 localhost / 127.0.0.1。 */
    private static final Pattern HARDCODED_BASE_URL =
            Pattern.compile("https?://(?:localhost|127\\.0\\.0\\.1):8\\d{3}");

    @Test
    @DisplayName("@Value 注解的 base-url 兜底必须走 ContractPaths 常量，不得写死端口")
    void valueFallbacksMustNotHardcodeEnginePorts() throws IOException {
        List<Path> sources = sources();

        // 阳性对照：扫描窗口必须真的包含已知文件，否则下面的断言会"空集恒绿"。
        assertThat(sources)
                .as("扫描窗口必须包含 PythonServicesHealthIndicator.java（否则守卫形同虚设）")
                .anyMatch(p -> p.getFileName().toString().equals("PythonServicesHealthIndicator.java"));
        assertThat(sources.size())
                .as("main 源码数量异常偏少，扫描窗口可能失效")
                .isGreaterThan(50);

        Map<String, List<String>> hits = new LinkedHashMap<>();
        for (Path src : sources) {
            String text = Files.readString(src, StandardCharsets.UTF_8);
            for (String snippet : valueAnnotationSnippets(text)) {
                List<String> found = hardcodedBaseUrls(snippet);
                if (!found.isEmpty()) {
                    hits.put(src.getFileName().toString(), found);
                }
            }
        }

        assertThat(hits)
                .as("以下 @Value 的 base-url 兜底写死了端口，请改用 "
                        + "ContractPaths.KG/RAG/FT/DEEPGRAPH_DEFAULT_BASE_URL：%s", hits)
                .isEmpty();
    }

    @Test
    @DisplayName("阴性对照：检测器能抓到人造违规，且不误报常量写法与注释")
    void detectorCatchesSyntheticViolation() {
        String buggy = "    @Value(\"${rag.base-url:http://localhost:8501}\") String baseUrl,";
        assertThat(hardcodedBaseUrls(buggy))
                .as("检测器必须能识别注解兜底里写死的 localhost:8501，否则守卫是假的")
                .isNotEmpty();

        String fixed = "    @Value(\"${rag.base-url:\" + ContractPaths.RAG_DEFAULT_BASE_URL + \"}\") "
                + "String baseUrl,";
        assertThat(hardcodedBaseUrls(fixed))
                .as("走常量的写法不得被判违规")
                .isEmpty();

        String commentOnly = "    // 分离布局下默认 http://localhost:8501，由启动器注入覆盖\n"
                + "    @Value(\"${rag.enabled:true}\") boolean enabled,";
        assertThat(hardcodedBaseUrls(commentOnly))
                .as("只在注释里提到旧端口、注解本身不写端口的写法不得被判违规")
                .isEmpty();
    }

    // ===================== 内部工具 =====================

    /** 抽取一个 {@code @Value} 注解的完整片段（含跨行拼接，最多 4 行）。 */
    private static List<String> valueAnnotationSnippets(String text) {
        List<String> lines = text.lines().toList();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).contains("@Value(")) {
                continue;
            }
            StringBuilder sb = new StringBuilder(lines.get(i));
            int j = i;
            while (j + 1 < lines.size() && j - i < 4 && sb.indexOf("\")\"") < 0) {
                j++;
                sb.append('\n').append(lines.get(j));
            }
            out.add(sb.toString());
        }
        return out;
    }

    /** 只对"键名含 base-url / .url"的注解片段判定硬编码端口。 */
    private static List<String> hardcodedBaseUrls(String snippet) {
        List<String> found = new ArrayList<>();
        if (!snippet.contains("@Value(")) {
            return found;
        }
        if (!snippet.contains("base-url") && !snippet.contains(".url")) {
            return found;
        }
        Matcher m = HARDCODED_BASE_URL.matcher(snippet);
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    /** 枚举 main 源码；目录不存在时**显式失败**，不返回空集（空集会让他断言恒绿）。 */
    private List<Path> sources() throws IOException {
        Path dir = MAIN_SOURCE_DIR;
        if (!Files.isDirectory(dir)) {
            Path fallback = Paths.get("backend").resolve(MAIN_SOURCE_DIR);
            if (Files.isDirectory(fallback)) {
                dir = fallback;
            }
        }
        assertThat(Files.isDirectory(dir))
                .as("找不到 main 源码目录：%s（工作目录=%s）；守卫不得在找不到源码时静默通过",
                        dir, Paths.get("").toAbsolutePath())
                .isTrue();

        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        }
    }
}
