package com.aicourse.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架构护栏：角色/授权判定与 Cookie、目标 payload 都只能有**一处实现**（阶段 2 DUP-01/05/16/18/19/26）。
 *
 * <p><b>为什么要有这条</b>：权限判定的副本不像业务代码那样"错了能看出来"——
 * 两份 {@code isAdmin} 只要有一处放宽（漏了 null 判断、写成 {@code contains} 而不是精确匹配、
 * 或者用了错误的常量），就是一条静默越权路径，代码评审也未必抓得住。阶段 3 已把
 * 20 余处散落写法收敛到 {@link com.aicourse.security.AccessSupport}，
 * 本用例负责让它们不会悄悄回来。</p>
 *
 * <p>白名单是<b>按文件 + 处数双向断言</b>的：某文件多出一处会红，少了一处也会红
 * （少一处说明白名单腐化——真正删掉了代码却忘了收紧断言）。</p>
 */
class AccessAuthorityGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /** authorities 遍历的唯一合法位置。 */
    private static final Map<String, Integer> AUTHORITIES_ALLOWED = Map.of(
            // 唯一实现点：roleOf / hasRole / hasAuthority
            "com/aicourse/security/AccessSupport.java", 6,
            // 401 响应体里拼接角色名做日志，只做 toString 展示，不参与任何判定
            "com/aicourse/security/JwtAuthenticationEntryPoint.java", 1
    );

    /** {@code "ROLE_"} 字面量的唯一合法位置。 */
    private static final Map<String, Integer> ROLE_PREFIX_ALLOWED = Map.of(
            // 前缀常量 + 剥离前缀 + 拼出期望授权项
            "com/aicourse/security/AccessSupport.java", 3,
            // ROLE_AUTHORITY_PREFIX 常量（授权项前缀，非角色解析）
            "com/aicourse/security/AdminScopeGuard.java", 1,
            // 授权项生产者：new SimpleGrantedAuthority("ROLE_" + role)
            "com/aicourse/security/JwtRequestFilter.java", 1
    );

    /** Cookie 名称遍历的唯一合法位置。 */
    private static final Map<String, Integer> COOKIE_READ_ALLOWED = Map.of(
            "com/aicourse/common/CookieUtils.java", 1
    );

    /** {@code "teachingClassIds"} 字段名的唯一合法位置。 */
    private static final Map<String, Integer> TARGETS_PAYLOAD_ALLOWED = Map.of(
            "com/aicourse/common/ActivityTargetsPayload.java", 1
    );

    private static final Pattern AUTHORITIES = Pattern.compile("getAuthorities\\(\\)");
    private static final Pattern ROLE_PREFIX = Pattern.compile("\"ROLE_");
    private static final Pattern COOKIE_READ = Pattern.compile("cookie\\.getName\\(\\)");
    private static final Pattern TARGETS_FIELD = Pattern.compile("\"teachingClassIds\"");

    /** 不得再在 controller 里长出私有角色副本（收敛后应只剩调用 {@code AccessSupport}）。 */
    private static final Pattern CONTROLLER_ROLE_METHOD = Pattern.compile(
            "private\\s+(?:static\\s+)?[A-Za-z_<>\\[\\].]+\\s+"
                    + "(?:isAdmin|isStudent|isTeacher|roleOf|hasRole|principalId)\\s*\\(\\s*"
                    + "(?:final\\s+)?Authentication\\s+\\w+\\s*\\)");

    @Test
    @DisplayName("authorities 遍历只允许出现在 AccessSupport（与 401 日志拼接处）")
    void authoritiesTraversalIsCentralised() throws IOException {
        assertCounts("authorities 遍历（getAuthorities()）", AUTHORITIES, AUTHORITIES_ALLOWED,
                "请改用 AccessSupport.isAdmin / hasRole / hasAuthority / roleOf / requirePrincipalId");
    }

    @Test
    @DisplayName("角色前缀字面量只允许出现在 AccessSupport 与授权项生产者/常量")
    void rolePrefixLiteralIsCentralised() throws IOException {
        assertCounts("\"ROLE_\" 字面量", ROLE_PREFIX, ROLE_PREFIX_ALLOWED,
                "请改用 AccessSupport.hasRole(auth, RoleConstants.XXX)");
    }

    @Test
    @DisplayName("Cookie 名称遍历只有 CookieUtils 一处（DUP-01）")
    void cookieReadIsCentralised() throws IOException {
        assertCounts("Cookie 名称遍历", COOKIE_READ, COOKIE_READ_ALLOWED,
                "请改用 CookieUtils.readCookieValue(request, name)");
    }

    @Test
    @DisplayName("目标教学班 payload 字段名只有一处定义（DUP-05）")
    void targetsPayloadIsCentralised() throws IOException {
        assertCounts("teachingClassIds 字段", TARGETS_FIELD, TARGETS_PAYLOAD_ALLOWED,
                "请改用 ActivityTargetsPayload.of(visibility, teachingClassIds)");
    }

    @Test
    @DisplayName("controller 包内不得再出现私有角色判定副本（DUP-16/18/26）")
    void controllersDoNotDeclareRoleHelpers() throws IOException {
        assertTrue(Files.isDirectory(SOURCE_ROOT), "源码根不存在: " + SOURCE_ROOT.toAbsolutePath());
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                if (!relative.startsWith("com/aicourse/controller/")) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = CONTROLLER_ROLE_METHOD.matcher(source);
                while (matcher.find()) {
                    int line = source.substring(0, matcher.start()).split("\n", -1).length;
                    violations.add(relative + ":" + line + " → " + matcher.group());
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "控制器里不要再复制角色判定（统一调用 AccessSupport）：\n" + String.join("\n", violations));
    }

    // ==================== 工具 ====================

    private void assertCounts(String label, Pattern pattern, Map<String, Integer> allowed, String hint)
            throws IOException {
        assertTrue(Files.isDirectory(SOURCE_ROOT), "源码根不存在: " + SOURCE_ROOT.toAbsolutePath());
        Map<String, Integer> actual = new java.util.HashMap<>();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = pattern.matcher(source);
                int count = 0;
                while (matcher.find()) {
                    count++;
                    if (!allowed.containsKey(relative)) {
                        int line = source.substring(0, matcher.start()).split("\n", -1).length;
                        violations.add(relative + ":" + line + " → " + matcher.group());
                    }
                }
                if (count > 0) {
                    actual.put(relative, count);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                label + " 只允许出现在 " + allowed.keySet() + "：" + hint + "\n" + String.join("\n", violations));
        // 双向断言：白名单里的文件/处数必须与事实一致，防止白名单腐化
        assertEquals(allowed, actual, label + " 的白名单处数已与源码不一致（白名单需要收紧或补充理由）");
    }
}
