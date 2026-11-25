package com.aicourse.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架构护栏：下游重试策略只能有一处实现（阶段2 DUP-10~13）。
 *
 * <p>三个 HTTP 客户端（RAG / 知识图谱 / DeepGraph）曾各抄一份
 * {@code isExplicitFailure / sleep / isReadTimeout / isServiceUnavailable / isConnectFailure}。
 * 这类方法的副作用不是"多几行代码"，而是**同一场故障在不同端点上重试次数不同**：
 * 漏改的那一路会把读超时当成可重试，端到端多等 1+2 秒，直接把 {@code <5s} 指标打穿，
 * 而线上表现只是"某个接口今天慢一点"。</p>
 */
class DownstreamRetryGuardTest {

    private static final Path INTEGRATION_ROOT = Path.of("src", "main", "java", "com", "aicourse", "integration");
    private static final String SUPPORT_FILE = "com/aicourse/integration/support/DownstreamRetrySupport.java";

    /** 只允许在共用实现点出现的方法声明。 */
    private static final Pattern FORBIDDEN_DECL = Pattern.compile(
            "(?m)^[ \\t]*(?:private|public)(?:[ \\t]+static)?[ \\t]+(?:boolean|void)[ \\t]+"
                    + "(?:isExplicitFailure|isReadTimeout|isServiceUnavailable|isConnectFailure|sleep)"
                    + "[ \\t]*\\(");

    @Test
    @DisplayName("重试判定不得在客户端里再复制一份，只能调用 DownstreamRetrySupport")
    void retryPredicatesLiveInOnePlace() throws IOException {
        assertTrue(Files.isDirectory(INTEGRATION_ROOT), "源码根不存在: " + INTEGRATION_ROOT.toAbsolutePath());
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(INTEGRATION_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = "com/aicourse/integration/"
                        + INTEGRATION_ROOT.relativize(file).toString().replace('\\', '/');
                if (SUPPORT_FILE.equals(relative)) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = FORBIDDEN_DECL.matcher(source);
                while (matcher.find()) {
                    int line = source.substring(0, matcher.start()).split("\n", -1).length;
                    violations.add(relative + ":" + line + " → " + matcher.group().trim());
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "重试判定请调用 DownstreamRetrySupport（读超时/连接拒绝/503 不重试的策略只允许一处定义）：\n"
                        + String.join("\n", violations));
    }

    @Test
    @DisplayName("三个客户端都必须真的用上共用判定（防止删了副本却忘了改调用）")
    void clientsActuallyUseSharedSupport() throws IOException {
        List<String> clients = List.of(
                "com/aicourse/integration/rag/HttpRagProvider.java",
                "com/aicourse/integration/kg/HttpKnowledgeGraphProvider.java",
                "com/aicourse/integration/deepgraph/HttpDeepGraphRagProvider.java");
        List<String> missing = new ArrayList<>();
        for (String client : clients) {
            Path path = INTEGRATION_ROOT.resolve(client.substring("com/aicourse/integration/".length()));
            String source = Files.readString(path, StandardCharsets.UTF_8);
            if (!source.contains("DownstreamRetrySupport.")) {
                missing.add(client);
            }
        }
        assertTrue(missing.isEmpty(), "以下客户端没有走共用重试判定: " + missing);
    }
}
