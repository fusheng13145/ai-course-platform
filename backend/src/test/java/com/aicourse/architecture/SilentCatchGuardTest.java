package com.aicourse.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架构护栏：{@code catch} 块不得静默吞异常（DEF-05）。
 *
 * <p><b>为什么要有这条</b>：异常被吞掉后返回默认值时，故障现场只剩「结果不对但没有任何日志」，
 * 阶段2 审计一次就扫出 30+ 处；阶段3 用<strong>字符偏移做括号配对</strong>逐块判定，
 * 分两批补了 30 处日志、并把「确实不该记日志」的 7 处固定为白名单。</p>
 *
 * <p><b>判定口径</b>：catch 块体内既没有 logger 调用、也没有 {@code throw}，即视为静默。
 * 白名单按「文件 → 允许的处数」登记，任何新增静默 catch 都会让本用例变红——
 * 要么补日志，要么在白名单里写明理由。</p>
 */
class SilentCatchGuardTest {

    /** 源码根（测试工作目录是 backend/）。 */
    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /** logger 调用：{@code log.warn(...)} 或全限定 {@code LoggerFactory.getLogger(...).warn(...)}。 */
    private static final Pattern LOGGER_CALL =
            Pattern.compile("(\\blog|logger|LOGGER)\\s*\\.\\s*[a-zA-Z]+|LoggerFactory\\s*\\.\\s*getLogger");

    private static final Pattern CATCH_START = Pattern.compile("catch\\s*\\(");

    /**
     * 已判定「不补日志」的清单：文件 → 允许处数 + 理由。
     * 详见 {@code 项目文档/阶段3_改动清单与验证记录_批次3A_2026-11-24.md} §5.4。
     */
    private static final Map<String, Integer> WHITELIST = new LinkedHashMap<>();

    static {
        // 四种格式试错解析的固定控制流，最后一次失败会 throw IllegalArgumentException
        WHITELIST.put("com/aicourse/util/LenientDateTimeParser.java", 4);
        // 答案解析回退（JSON→去引号→分隔符拆分），与 DDL 兼容口径一致，同方法其它分支已打日志
        WHITELIST.put("com/aicourse/service/impl/exam/ExamSessionGradingService.java", 2);
        // 同方法内已有 warn，重复记录无收益
        WHITELIST.put("com/aicourse/exception/GlobalExceptionHandler.java", 1);
    }

    @Test
    @DisplayName("main 源码中每个 catch 块都必须打日志或重新抛出（白名单除外）")
    void noSilentCatchInMainSources() throws IOException {
        assertTrue(Files.isDirectory(SOURCE_ROOT), "源码根不存在: " + SOURCE_ROOT.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        Map<String, Integer> actual = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                String source = Files.readString(file, StandardCharsets.UTF_8);
                List<Integer> silentLines = silentCatchLines(source);
                if (!silentLines.isEmpty()) {
                    int allowed = WHITELIST.getOrDefault(relative, 0);
                    actual.put(relative, silentLines.size());
                    if (silentLines.size() > allowed) {
                        violations.add(relative + " 静默 catch " + silentLines.size()
                                + " 处（白名单 " + allowed + "）：行 " + silentLines);
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "存在静默吞异常（catch 块既不打日志也不抛出）：\n" + String.join("\n", violations));
        // 白名单条目必须仍然存在，否则说明是过期条目，应及时删除
        for (Map.Entry<String, Integer> entry : WHITELIST.entrySet()) {
            assertEquals(entry.getValue(), actual.get(entry.getKey()),
                    "白名单条目与实际不符（可能已补日志，请从这里删除）: " + entry.getKey());
        }
    }

    /** 逐 catch 用括号配对取出块体，返回「既无 logger 调用也无 throw」的行号。 */
    private static List<Integer> silentCatchLines(String source) {
        List<Integer> lines = new ArrayList<>();
        Matcher matcher = CATCH_START.matcher(source);
        while (matcher.find()) {
            int cursor = matcher.end() - 1;
            cursor = skipBalanced(source, cursor, '(', ')');
            if (cursor < 0) {
                continue;
            }
            cursor = skipWhitespace(source, cursor + 1);
            if (cursor >= source.length() || source.charAt(cursor) != '{') {
                continue;
            }
            int end = skipBalanced(source, cursor, '{', '}');
            if (end < 0) {
                continue;
            }
            String body = source.substring(cursor + 1, end);
            boolean logged = LOGGER_CALL.matcher(body).find() || body.contains("throw ");
            if (!logged) {
                lines.add(source.substring(0, matcher.start()).split("\n", -1).length);
            }
        }
        return lines;
    }

    /** 从 {@code start}（指向开括号）跳到配对闭括号的索引；不配对返回 -1。 */
    private static int skipBalanced(String source, int start, char open, char close) {
        int depth = 0;
        for (int i = start; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int skipWhitespace(String source, int from) {
        int i = from;
        while (i < source.length() && Character.isWhitespace(source.charAt(i))) {
            i++;
        }
        return i;
    }
}
