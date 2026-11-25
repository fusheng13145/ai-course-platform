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
 * 架构护栏：HTTP / 业务状态码只能来自 {@code ApiCode}，不得写魔法数字。
 *
 * <p><b>为什么要有这条</b>：状态码散落成 {@code 400}/{@code 403}/{@code 404} 字面量时，
 * 前后端对同一种失败会给出不同信封（阶段2 审计里 BusinessException 一族就混着
 * 「默认 400 + 手写 403/404」两种写法）。统一到 {@code ApiCode} 后，改一处即可全链路生效。</p>
 *
 * <p>判定覆盖四类写法：{@code new BusinessException(403, ...)}（含全限定类名）、
 * {@code ApiResponse.xxx(400, ...)}、{@code ResponseEntity.status(404)}、
 * {@code HttpStatusCode.valueOf(500)}、{@code .setCode(401)}。</p>
 */
class ApiCodeGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("new\\s+(?:com\\.aicourse\\.exception\\.)?BusinessException\\(\\s*\\d{3}\\s*[,)]"),
            Pattern.compile("ApiResponse\\s*\\.[a-zA-Z]+\\s*\\(\\s*\\d{3}\\s*[,)]"),
            Pattern.compile("ResponseEntity\\s*\\.\\s*status\\s*\\(\\s*\\d{3}\\s*\\)"),
            Pattern.compile("HttpStatusCode\\s*\\.\\s*valueOf\\s*\\(\\s*\\d{3}\\s*\\)"),
            Pattern.compile("\\.\\s*setCode\\s*\\(\\s*\\d{3}\\s*\\)")
    );

    @Test
    @DisplayName("main 源码里不得出现 HTTP/业务状态码字面量")
    void noMagicStatusCodesInMainSources() throws IOException {
        assertTrue(Files.isDirectory(SOURCE_ROOT), "源码根不存在: " + SOURCE_ROOT.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                for (Pattern pattern : FORBIDDEN) {
                    Matcher matcher = pattern.matcher(source);
                    while (matcher.find()) {
                        int line = source.substring(0, matcher.start()).split("\n", -1).length;
                        violations.add(relative + ":" + line + " → " + matcher.group().trim());
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "状态码请改用 ApiCode 常量（如 ApiCode.FORBIDDEN）：\n" + String.join("\n", violations));
    }
}
