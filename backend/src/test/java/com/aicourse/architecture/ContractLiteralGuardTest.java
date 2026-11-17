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
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 契约字面量守卫：控制器层不得直接写 {@code mode} 取值字符串。
 *
 * <p><b>为什么需要</b>（N-5，2026-11-17）：{@code ContractPaths} 已定义
 * {@code MODE_ENGINE}/{@code MODE_LOCAL}/{@code MODE_UNAVAILABLE}，但
 * {@code KnowledgeController.parseKgJson} 曾用字符串字面量 {@code "LOCAL"}/{@code "UNAVAILABLE"}
 * 直接拼返回体。于是 {@code mode} 有了两个来源——常量类与控制器各写一份。
 * 这类分叉不会报错：改常量的人以为改全了，控制器仍发旧值，前端按 {@code mode}
 * 分支的降级判断会静默错判（把"引擎不可用"显示成"引擎正常"）。
 * 这正是 {@code AGENTS.md} §4.3「引第二套看起来一样的实现」禁止的情形。</p>
 *
 * <p><b>检测口径</b>：只匹配**带双引号的字符串字面量**（如 {@code "LOCAL"}）。
 * 注释与 Javadoc 里的 {@code {@code mode=LOCAL}}、{@code ApiCode.SERVICE_UNAVAILABLE}
 * 枚举名都不带引号，因此不会误报——这是刻意的：口径收窄到"真正的字面量"，
 * 守卫才不会因为一句注释就红，也不会被人用"加个注释就绕过"的方式绕开。</p>
 *
 * <p><b>阴性对照</b>（{@code AGENTS.md} §10.4「负向断言必须配阴性对照」）：
 * {@link #detectorCatchesSyntheticViolation()} 用一段人造违规样本证明检测器确实能抓到问题，
 * 避免"扫描窗口为空/正则写错"导致守卫恒绿。</p>
 */
@DisplayName("契约字面量守卫：控制器不得直写 mode 字面量")
class ContractLiteralGuardTest {

    /** 禁止在控制器层出现的 mode 字面量（与 ContractPaths.MODE_* 一一对应）。 */
    private static final List<String> FORBIDDEN_MODE_LITERALS =
            List.of("\"ENGINE\"", "\"LOCAL\"", "\"UNAVAILABLE\"");

    /** 相对模块根（Maven surefire 的工作目录是 backend/）。 */
    private static final Path CONTROLLER_SOURCE_DIR =
            Paths.get("src", "main", "java", "com", "aicourse", "controller");

    @Test
    @DisplayName("控制器层不得出现 mode 字面量（应统一走 ContractPaths.MODE_*）")
    void controllersMustNotHardcodeModeLiterals() throws IOException {
        List<Path> sources = controllerSources();

        // 阳性对照：扫描窗口必须真的包含已知文件，否则下面的断言会"空集恒绿"。
        assertThat(sources)
                .as("扫描窗口必须包含 KnowledgeController.java（否则守卫形同虚设）")
                .anyMatch(p -> p.getFileName().toString().equals("KnowledgeController.java"));

        Map<Path, List<String>> hits = new LinkedHashMap<>();
        for (Path src : sources) {
            String text = Files.readString(src, StandardCharsets.UTF_8);
            List<String> found = new ArrayList<>();
            for (String literal : FORBIDDEN_MODE_LITERALS) {
                if (text.contains(literal)) {
                    found.add(literal);
                }
            }
            if (!found.isEmpty()) {
                hits.put(src, found);
            }
        }

        assertThat(hits)
                .as("以下控制器直接写了 mode 字面量，请改用 ContractPaths.MODE_ENGINE/MODE_LOCAL/MODE_UNAVAILABLE：%s", hits)
                .isEmpty();
    }

    @Test
    @DisplayName("阴性对照：检测器能抓到人造违规样本（防止守卫恒绿）")
    void detectorCatchesSyntheticViolation() {
        String synthetic = "Map<String,Object> m = new LinkedHashMap<>();\n"
                + "m.put(\"mode\", \"LOCAL\");\n";
        assertThat(containsForbiddenLiteral(synthetic))
                .as("检测器必须能识别 {\"mode\":\"LOCAL\"} 这类写法，否则守卫是假的")
                .isTrue();

        String clean = "m.put(ContractPaths.R_MODE, ContractPaths.MODE_LOCAL); // mode=LOCAL 见注释\n";
        assertThat(containsForbiddenLiteral(clean))
                .as("走常量、且只在注释里提到 LOCAL 的写法不得被判违规")
                .isFalse();
    }

    // ===================== 内部工具 =====================

    private static boolean containsForbiddenLiteral(String text) {
        for (String literal : FORBIDDEN_MODE_LITERALS) {
            if (text.contains(literal)) {
                return true;
            }
        }
        return false;
    }

    /** 枚举控制器源码文件；目录不存在时**显式失败**，不返回空集（空集会让他断言恒绿）。 */
    private List<Path> controllerSources() throws IOException {
        Path dir = CONTROLLER_SOURCE_DIR;
        if (!Files.isDirectory(dir)) {
            // 少数执行入口（如 IDE 直接跑测试）工作目录可能不是模块根，向上回退一级再找一次。
            Path fallback = Paths.get("backend").resolve(CONTROLLER_SOURCE_DIR);
            if (Files.isDirectory(fallback)) {
                dir = fallback;
            }
        }
        assertThat(Files.isDirectory(dir))
                .as("找不到控制器源码目录：%s（工作目录=%s）；守卫不得在找不到源码时静默通过",
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
