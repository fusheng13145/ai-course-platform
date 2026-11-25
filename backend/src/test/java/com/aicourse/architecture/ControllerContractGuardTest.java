package com.aicourse.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架构护栏：HTTP 入参必须是 DTO，实体不得出现在 {@code @RequestBody} 上。
 *
 * <p><b>为什么要有这条</b>：实体直绑不是「不方便」而是「不安全」——实体的
 * {@code id}／{@code creatorId}／{@code status}／{@code isActive}／{@code signCode} 等列，
 * 原本依赖服务层「记得覆盖」来兜底。3B-2／3B-3／3E-01 三个批次一共清掉 16 处，
 * 但每次审计都靠脚本现扫，说明这件事没有结构性保障。本用例把它变成编译期之后的硬约束：
 * 控件层一旦再绑实体，测试直接变红。</p>
 *
 * <p><b>判定口径</b>：遍历 {@code controller/} 下所有 {@code @RequestBody}，取到类型简单名后：</p>
 * <ol>
 *   <li>类型是 {@code entity/} 下的类 → <b>违规</b>（无论服务层是否覆盖）；</li>
 *   <li>类型既不是包装类型（{@code Map}/{@code List}/{@code String}…）、也不是 {@code dto/} 下的类、
 *       也不是本文件内声明的嵌套类 → <b>违规</b>（防止随手绑定一个随手写的 POJO 绕过第 1 条）。</li>
 * </ol>
 *
 * <p>第二组用例把「DTO 里不该有哪些字段」钉死：日后如果有人图省事把 {@code creatorId}
 * 加回 {@code DiscussionTopicSaveDTO}，护栏会直接报出字段名。</p>
 */
class ControllerContractGuardTest {

    /** 源码根（测试工作目录是 backend/）。 */
    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    private static final Path CONTROLLER_DIR = SOURCE_ROOT.resolve("com/aicourse/controller");
    private static final Path ENTITY_DIR = SOURCE_ROOT.resolve("com/aicourse/entity");
    private static final Path DTO_DIR = SOURCE_ROOT.resolve("com/aicourse/dto");

    /** {@code @RequestBody} 后面的类型（跳过 @Valid 等注解）。 */
    private static final Pattern REQUEST_BODY = Pattern.compile(
            "@RequestBody(?:\\s+@[A-Za-z0-9_.]+(?:\\([^)]*\\))?)*\\s+([\\w.]+)");

    /** 允许直接接收的容器／标量类型（不承载业务字段，无从注入）。 */
    private static final Set<String> WRAPPER_TYPES = Set.of(
            "Map", "List", "Set", "Collection", "String", "Object", "byte", "JsonNode", "MultipartFile", "Long", "Integer");

    /**
     * DTO 中禁止出现的服务端字段：DTO 类名 → 不允许存在的字段名。
     *
     * <p>这些字段只能由服务端（登录身份／流转端点／定时任务）写入。字段一旦出现在入参 DTO 上，
     * 就等于把「谁能写」交回给了客户端。</p>
     */
    private static final Map<String, Set<String>> FORBIDDEN_DTO_FIELDS = new LinkedHashMap<>();

    static {
        FORBIDDEN_DTO_FIELDS.put("DiscussionTopicSaveDTO", Set.of(
                "id", "creatorId", "creatorRole", "status", "isPinned", "isEssence", "replyCount",
                "lastReplyAt", "createdAt", "updatedAt"));
        FORBIDDEN_DTO_FIELDS.put("ExamPaperSaveDTO", Set.of(
                "id", "creatorId", "status", "isActive", "courseName", "createdAt", "updatedAt"));
        FORBIDDEN_DTO_FIELDS.put("GradePolicySaveDTO", Set.of("id", "creatorId", "createdAt", "updatedAt"));
        FORBIDDEN_DTO_FIELDS.put("GradeItemSaveDTO", Set.of("id", "graderId", "teachingClassId", "createdAt", "updatedAt"));
        FORBIDDEN_DTO_FIELDS.put("SignInSaveDTO", Set.of(
                "id", "creatorId", "status", "signCode", "startedAt", "closedAt", "createdAt", "updatedAt"));
        FORBIDDEN_DTO_FIELDS.put("VoteSaveDTO", Set.of(
                "id", "creatorId", "status", "startedAt", "closedAt", "createdAt", "updatedAt"));
        FORBIDDEN_DTO_FIELDS.put("VoteCastDTO", Set.of(
                "id", "voteId", "studentId", "ip", "userAgent", "createdAt", "updatedAt"));
        FORBIDDEN_DTO_FIELDS.put("RolePermissionSaveDTO", Set.of("id"));
        FORBIDDEN_DTO_FIELDS.put("WrongQuestionUpdateDTO", Set.of(
                "id", "studentId", "courseId", "wrongAnswer", "correctAnswer", "mastered",
                "sourceType", "practiceCount", "lastPracticedAt"));
        // 3B-2 已有 DTO：审批链字段同样不得入参（自批自审）
        FORBIDDEN_DTO_FIELDS.put("CourseSaveDTO", Set.of(
                "approvalStatus", "approvedBy", "approvalComment", "approvalTime", "completedAt"));
    }

    @Test
    @DisplayName("控制器不得用 @RequestBody 直接接收实体")
    void noEntityInRequestBody() throws IOException {
        Set<String> entityNames = simpleNames(ENTITY_DIR);
        assertFalse(entityNames.isEmpty(), "未扫到实体类，扫描口径可能失效: " + ENTITY_DIR.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        int scanned = 0;
        for (Path file : controllerFiles()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = REQUEST_BODY.matcher(source);
            while (matcher.find()) {
                scanned++;
                String type = lastSegment(matcher.group(1));
                if (entityNames.contains(type)) {
                    violations.add(file.getFileName() + ":" + lineOf(source, matcher.start()) + " 绑定了实体 " + type);
                }
            }
        }

        assertTrue(scanned > 100, "扫到的 @RequestBody 数量异常偏少（" + scanned + "），口径可能失效");
        assertTrue(violations.isEmpty(),
                "控制器仍在入参上直接绑定实体：\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("控制器入参类型必须是 DTO、包装类型或本文件内声明的嵌套类")
    void requestBodyTypesAreDtoOrWrapper() throws IOException {
        Set<String> dtoNames = simpleNames(DTO_DIR);
        List<String> violations = new ArrayList<>();
        for (Path file : controllerFiles()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = REQUEST_BODY.matcher(source);
            while (matcher.find()) {
                String type = lastSegment(matcher.group(1));
                if (WRAPPER_TYPES.contains(type) || dtoNames.contains(type) || declaresNested(source, type)) {
                    continue;
                }
                violations.add(file.getFileName() + ":" + lineOf(source, matcher.start())
                        + " 的入参类型 " + type + " 既不是 dto 也不是包装类型");
            }
        }
        assertTrue(violations.isEmpty(),
                "存在来源不明的入参类型（可能是随手写的 POJO 绕过实体检查）：\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("入参 DTO 不得携带服务端字段")
    void dtoMustNotCarryServerSideFields() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : FORBIDDEN_DTO_FIELDS.entrySet()) {
            Path src = DTO_DIR.resolve(entry.getKey() + ".java");
            assertTrue(Files.exists(src), "DTO 已不存在（护栏条目过期）: " + src);
            Class<?> clazz = Class.forName("com.aicourse.dto." + entry.getKey());
            Set<String> present = new LinkedHashSet<>();
            for (Field field : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    present.add(field.getName());
                }
            }
            for (String forbidden : entry.getValue()) {
                if (present.contains(forbidden)) {
                    violations.add(entry.getKey() + " 携带了服务端字段 " + forbidden);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "入参 DTO 里出现了只应由服务端写入的字段：\n" + String.join("\n", violations));
    }

    // ---------------------------------------------------------------- helpers

    private static List<Path> controllerFiles() throws IOException {
        assertTrue(Files.isDirectory(CONTROLLER_DIR), "控制器目录不存在: " + CONTROLLER_DIR.toAbsolutePath());
        try (Stream<Path> files = Files.list(CONTROLLER_DIR)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /** 目录下 {@code *.java} 的简单类名。 */
    private static Set<String> simpleNames(Path dir) throws IOException {
        assertTrue(Files.isDirectory(dir), "目录不存在: " + dir.toAbsolutePath());
        Set<String> names = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                names.add(name.substring(0, name.length() - ".java".length()));
            }
        }
        return names;
    }

    /** 本文件内是否声明了同名嵌套类／record／enum。 */
    private static boolean declaresNested(String source, String type) {
        return Pattern.compile("(?:class|record|enum|interface)\\s+" + Pattern.quote(type) + "\\b")
                .matcher(source).find();
    }

    private static String lastSegment(String type) {
        int dot = type.lastIndexOf('.');
        return dot < 0 ? type : type.substring(dot + 1);
    }

    private static int lineOf(String source, int offset) {
        return source.substring(0, offset).split("\n", -1).length;
    }
}
