package com.aicourse.util;

import com.aicourse.entity.Question;
import com.aicourse.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Excel 导入导出工具（POI XSSF 实现 .xlsx）。
 * <p>统一处理：下载响应头（中文文件名 URL 编码）、表头样式、题库导入/导出、
 * 用户批量导入、数据报表多 Sheet 导出。</p>
 */
public final class ExcelUtil {

    private static final Logger log = LoggerFactory.getLogger(ExcelUtil.class);

    private ExcelUtil() {
    }

    /** 题库导出列头（与导入列严格一致） */
    private static final String[] QUESTION_HEADERS =
            {"题型", "题干", "选项(JSON)", "正确答案", "难度", "分值", "知识点", "解析"};

    /** 用户导入列头 */
    private static final String[] USER_HEADERS =
            {"用户名", "姓名", "邮箱", "角色", "初始密码", "手机号", "学号", "专业", "年级", "院系"};

    /** 随机初始密码长度（导入时未填写密码的兜底，替代固定 123456 弱口令） */
    private static final int RANDOM_PASSWORD_LENGTH = 8;

    /** 随机密码字符集（去除易混淆字符 0/O/1/l/I） */
    private static final String RANDOM_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    /** 章节导入「是否发布」列可识别的否定写法（小写键）；其余写法一律沿用缺省的已发布语义 */
    private static final Set<String> NEGATIVE_WORDS = Set.of("否", "false", "0", "n", "no");

    /**
     * 生成随机初始密码（8 位，字母+数字，SecureRandom）。
     * <p>Excel 导入用户未填写密码时使用，替代固定 {@code 123456} 弱口令；
     * 临时密码由导入接口返回给管理员转告用户，首次登录后应尽快修改。</p>
     */
    public static String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(RANDOM_PASSWORD_LENGTH);
        for (int i = 0; i < RANDOM_PASSWORD_LENGTH; i++) {
            sb.append(RANDOM_PASSWORD_CHARS.charAt(random.nextInt(RANDOM_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 设置 Excel 下载响应头（xlsx Content-Type + UTF-8 编码的中文文件名）。
     */
    public static void setDownloadHeaders(HttpServletResponse response, String filename) throws IOException {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
    }

    /**
     * 校验文件名是否为 .xlsx 格式。
     */
    public static boolean isExcelFile(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".xlsx");
    }

    /**
     * 题库导出：将题目列表写入 xlsx 输出流（第 0 行为表头）。
     */
    public static void writeQuestions(OutputStream out, List<Question> questions) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("题库");
            writeHeaderRow(sheet, QUESTION_HEADERS);
            int rowIdx = 1;
            for (Question q : questions) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(nullToEmpty(q.getType()));
                row.createCell(1).setCellValue(nullToEmpty(q.getContent()));
                row.createCell(2).setCellValue(nullToEmpty(q.getOptions()));
                row.createCell(3).setCellValue(nullToEmpty(q.getCorrectAnswers()));
                row.createCell(4).setCellValue(nullToEmpty(q.getDifficulty()));
                row.createCell(5).setCellValue(q.getScore() == null ? "" : q.getScore().stripTrailingZeros().toPlainString());
                row.createCell(6).setCellValue(nullToEmpty(q.getKnowledgePoints()));
                row.createCell(7).setCellValue(nullToEmpty(q.getExplanation()));
            }
            wb.write(out);
        }
    }

    /**
     * 题库导入：从 xlsx 文件解析题目列表（跳过空行与表头行）。
     */
    public static List<Question> readQuestions(MultipartFile file) throws IOException {
        List<Question> list = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String content = cellStr(row.getCell(1));
                if (content == null || content.isEmpty()) {
                    continue;
                }
                Question q = new Question();
                // 题型/难度归一化（2026-09-23）：非法值不再原样入库。
                // 背景：P2-11（角色）与 P2-12（难度/类型）暴露的同类问题——批量导入路径
                // 不经过 DTO 的 @Pattern 校验，非法枚举会直接写库，前端映射表取不到值
                // 便渲染为空/默认（如难度渲染为「-」）。
                q.setType(normalizeQuestionType(cellStr(row.getCell(0))));
                q.setContent(content);
                // 修复（2026-09-23，P2-1）：C/D/G 三列在 MySQL 中是 JSON 类型，写入值本身必须是合法 JSON。
                // 原实现用 emptyToNull 原样透传——用户按直觉写「知识点 = 数据结构基础」「填空答案 = def」
                // 会导致整批导入失败，且报错为「数据完整性校验失败，请检查关联数据是否存在」，
                // 完全不提示 JSON 问题，极难自查。现统一规范化：已是合法 JSON 则原样保留，
                // 否则按列语义包装/拆分（见 normalizeJsonArray / normalizeJsonAny）。
                q.setOptions(normalizeJsonArray(cellStr(row.getCell(2))));
                q.setCorrectAnswers(normalizeJsonAny(cellStr(row.getCell(3))));
                q.setDifficulty(normalizeDifficulty(cellStr(row.getCell(4))));
                String score = cellStr(row.getCell(5));
                if (score != null && !score.isEmpty()) {
                    try {
                        q.setScore(new BigDecimal(score));
                    } catch (NumberFormatException e) {
                        log.error("解析题目分值失败: {}", e.getMessage(), e);
                        q.setScore(null);
                    }
                }
                q.setKnowledgePoints(normalizeJsonArray(cellStr(row.getCell(6))));
                q.setExplanation(emptyToNull(cellStr(row.getCell(7))));
                list.add(q);
            }
        }
        return list;
    }

    /** 供 JSON 规范化复用的轻量 mapper（仅做语法校验与序列化，不涉及业务类型） */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * 规范化「选项」「知识点」列：目标形态必须是 <b>JSON 数组</b>。
     *
     * <p>修复（2026-09-23，P2-1）：这两列对应 MySQL JSON 列，写入值本身必须合法。
     * 用户按直觉填 {@code 数据结构基础} / {@code 栈,队列,二叉树}（裸文本）会令<b>整批导入失败</b>，
     * 且报错「数据完整性校验失败，请检查关联数据是否存在」完全不提 JSON，极难自查。</p>
     *
     * <p>规则：空白 → null；已是合法 JSON 数组 → 原样保留；是合法 JSON 但非数组 → 包装为单元素数组；
     * 非法 JSON → 按 {@code , ， ; ；} 或换行拆分后组装为数组。</p>
     */
    private static String normalizeJsonArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            JsonNode node = JSON_MAPPER.readTree(trimmed);
            if (node.isArray()) {
                return trimmed;
            }
            return JSON_MAPPER.writeValueAsString(List.of(node.asText()));
        } catch (Exception notJson) {
            List<String> parts = Arrays.stream(trimmed.split("[,，;；\\r\\n]+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (parts.isEmpty()) {
                parts = List.of(trimmed);
            }
            try {
                return JSON_MAPPER.writeValueAsString(parts);
            } catch (Exception e) {
                log.warn("规范化 JSON 数组失败，该值将置空：{}", e.getMessage());
                return null;
            }
        }
    }

    /**
     * 规范化「正确答案」列：允许<b>任意</b>合法 JSON（字符串 / 数组 / 布尔 / 数字）。
     *
     * <p>单选索引 {@code "1"}、多选 {@code ["0","1"]}、判断 {@code true}、填空 {@code ["def"]}
     * 都是合法 JSON，原样保留；裸文本（如填空答案直接写 {@code def}）会包装为 {@code ["def"]}。</p>
     */
    private static String normalizeJsonAny(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            JSON_MAPPER.readTree(trimmed);
            return trimmed;
        } catch (Exception notJson) {
            try {
                return JSON_MAPPER.writeValueAsString(List.of(trimmed));
            } catch (Exception e) {
                log.warn("规范化正确答案失败，该值将置空：{}", e.getMessage());
                return null;
            }
        }
    }

    /**
     * 用户导入解析结果：用户列表 + 未填密码（随机生成）用户的临时密码映射（key 为用户名）。
     */
    public record UserImportResult(List<User> users, Map<String, String> generatedPasswords) {
    }

    /**
     * 用户导入：从 xlsx 文件解析用户列表（跳过空行与表头行，密码缺省生成随机初始密码）。
     */
    public static List<User> readUsers(MultipartFile file) throws IOException {
        return parseUsers(file).users();
    }

    /**
     * 用户导入（含临时密码）：除用户列表外，返回未填密码用户的随机初始密码映射，
     * 供导入接口回传管理员转告用户（明文仅本次响应出现一次）。
     */
    public static UserImportResult readUsersWithGenerated(MultipartFile file) throws IOException {
        return parseUsers(file);
    }

    private static UserImportResult parseUsers(MultipartFile file) throws IOException {
        List<User> list = new ArrayList<>();
        Map<String, String> generatedPasswords = new java.util.LinkedHashMap<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String username = cellStr(row.getCell(0));
                if (username == null || username.isEmpty()) {
                    continue;
                }
                User u = new User();
                u.setUsername(username);
                u.setFullName(emptyToNull(cellStr(row.getCell(1))));
                u.setEmail(emptyToNull(cellStr(row.getCell(2))));
                u.setRole(emptyToNull(cellStr(row.getCell(3))));
                String password = cellStr(row.getCell(4));
                if (password == null || password.isEmpty()) {
                    String randomPwd = generateRandomPassword();
                    u.setPassword(randomPwd);
                    generatedPasswords.put(username, randomPwd);
                } else {
                    u.setPassword(password);
                }
                u.setPhone(emptyToNull(cellStr(row.getCell(5))));
                u.setStudentId(emptyToNull(cellStr(row.getCell(6))));
                u.setMajor(emptyToNull(cellStr(row.getCell(7))));
                u.setGrade(emptyToNull(cellStr(row.getCell(8))));
                u.setDepartment(emptyToNull(cellStr(row.getCell(9))));
                list.add(u);
            }
        }
        return new UserImportResult(list, generatedPasswords);
    }

    // ==================== 章节 / 知识点批量导入（2026-09-23 新增，P2-4） ====================

    /**
     * 章节导入行。
     *
     * <p>{@code parentTitle} 以「父章节标题」引用而非 ID —— 使用者拿到的是课程大纲文本，
     * 不可能知道数据库 ID；写入时再解析为 {@code parentId}（支持二级章节）。</p>
     */
    public record ChapterRow(String title, String parentTitle, Integer orderIndex,
                             Integer duration, boolean isPublished, String content) {
    }

    /**
     * 解析章节导入文件。
     *
     * <p>表头（第 1 行，6 列）：章节标题* / 父章节标题 / 排序序号 / 时长(分钟) / 是否发布 / 章节内容。
     * 仅「章节标题」必填，为空的行整行跳过；「是否发布」仅显式写 否/false/0/n/no 才判为未发布。</p>
     */
    public static List<ChapterRow> readChapters(MultipartFile file) throws IOException {
        List<ChapterRow> list = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String title = cellStr(row.getCell(0));
                if (title == null || title.isEmpty()) {
                    continue;
                }
                list.add(new ChapterRow(
                        title,
                        emptyToNull(cellStr(row.getCell(1))),
                        toIntOrNull(cellStr(row.getCell(2))),
                        toIntOrNull(cellStr(row.getCell(3))),
                        toPublishedFlag(cellStr(row.getCell(4))),
                        emptyToNull(cellStr(row.getCell(5)))));
            }
        }
        return list;
    }

    /**
     * 知识点导入行。{@code parentName} 同样以「父知识点名称」引用，写入时解析为 {@code parentId}。
     */
    public record KnowledgeRow(String name, String parentName, String difficultyLevel,
                               Integer importance, Integer estimatedTime, String pointType,
                               String description) {
    }

    /**
     * 解析知识点导入文件。
     *
     * <p>表头（第 1 行，7 列）：知识点名称* / 父知识点名称 / 难度 / 重要程度(1-10) /
     * 预计学习时长(分钟) / 类型 / 描述。仅「知识点名称」必填，为空的行整行跳过。</p>
     */
    public static List<KnowledgeRow> readKnowledgePoints(MultipartFile file) throws IOException {
        List<KnowledgeRow> list = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String name = cellStr(row.getCell(0));
                if (name == null || name.isEmpty()) {
                    continue;
                }
                list.add(new KnowledgeRow(
                        name,
                        emptyToNull(cellStr(row.getCell(1))),
                        // 难度/类型归一化到权威枚举（难度 EASY/MEDIUM/HARD、类型 concept/skill/principle）
                        normalizeDifficulty(cellStr(row.getCell(2))),
                        toIntOrNull(cellStr(row.getCell(3))),
                        toIntOrNull(cellStr(row.getCell(4))),
                        normalizePointType(cellStr(row.getCell(5))),
                        emptyToNull(cellStr(row.getCell(6)))));
            }
        }
        return list;
    }

    /** 文本转整数，无法解析返回 null（缺省值由调用方决定）。 */
    private static Integer toIntOrNull(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return BigDecimal.valueOf(Double.parseDouble(s)).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 文本转布尔发布标记：仅显式否定写法（否/false/0/n/no）判为未发布。
     *
     * <p>留空或无法识别沿用历史缺省「已发布」，因此这里用原始 {@code boolean} 表达二态，
     * 不再返回可空 {@code Boolean}——调用方本就只区分「未发布 / 其余一律发布」。</p>
     */
    private static boolean toPublishedFlag(String s) {
        return s == null || !NEGATIVE_WORDS.contains(s.trim().toLowerCase());
    }

    // ==================== 枚举归一化（2026-09-23 新增） ====================

    /**
     * 归一化题型到权威枚举。
     *
     * <p>背景：批量导入路径不经过 DTO 校验（同 P2-11/P2-12 的根因），非法枚举会直接入库；
     * 而前端按题型分支渲染答题控件（单选/多选/判断/填空/简答），取值不匹配将导致
     * 题目在答题页无法作答。此处兼容中文与大小写写法，无法识别返回 null（由调用方按缺省处理）。</p>
     */
    private static String normalizeQuestionType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().toUpperCase();
        switch (v) {
            case "SINGLE_CHOICE":
            case "SINGLE":
            case "单选":
            case "单选题":
                return "SINGLE_CHOICE";
            case "MULTIPLE_CHOICE":
            case "MULTIPLE":
            case "多选":
            case "多选题":
                return "MULTIPLE_CHOICE";
            case "TRUE_FALSE":
            case "JUDGE":
            case "判断":
            case "判断题":
                return "TRUE_FALSE";
            case "FILL_BLANK":
            case "FILL":
            case "填空":
            case "填空题":
                return "FILL_BLANK";
            case "SHORT_ANSWER":
            case "ESSAY":
            case "简答":
            case "简答题":
                return "SHORT_ANSWER";
            default:
                return null;
        }
    }

    /**
     * 归一化难度到权威枚举 {@code EASY / MEDIUM / HARD}。
     *
     * <p>背景（P2-12）：前端映射表只有这三个键，库中出现过的 {@code BASIC} 会渲染为空标签。
     * 此处兼容历史写法（BASIC/ADVANCED）与中文，无法识别返回 null。</p>
     */
    private static String normalizeDifficulty(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        switch (raw.trim().toUpperCase()) {
            case "EASY":
            case "BASIC":
            case "简单":
            case "容易":
                return "EASY";
            case "MEDIUM":
            case "NORMAL":
            case "INTERMEDIATE":
            case "中等":
                return "MEDIUM";
            case "HARD":
            case "ADVANCED":
            case "DIFFICULT":
            case "困难":
                return "HARD";
            default:
                return null;
        }
    }

    /**
     * 归一化知识点类型到建表注释的权威取值（<b>小写</b>）：{@code concept / skill / principle}。
     *
     * <p>背景（P2-12）：库中同时存在 {@code CONCEPT} 与 {@code concept} 两种写法。
     * 兼容中文；无法识别返回 null。</p>
     */
    private static String normalizePointType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        switch (raw.trim().toUpperCase()) {
            case "CONCEPT":
            case "概念":
                return "concept";
            case "SKILL":
            case "技能":
                return "skill";
            case "PRINCIPLE":
            case "原理":
                return "principle";
            default:
                return null;
        }
    }

    /**
     * 数据报表导出：将报表 Map 按顶层分组写入多个 Sheet（每 Sheet 两列：指标 / 数值，嵌套结构以点号路径拍平）。
     */
    public static void writeReport(OutputStream out, Map<String, Object> report) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            int idx = 0;
            for (Map.Entry<String, Object> entry : report.entrySet()) {
                Sheet sheet = wb.createSheet(safeSheetName(entry.getKey(), idx));
                writeHeaderRow(sheet, new String[]{"指标", "数值"});
                List<Map.Entry<String, Object>> flat = flatten(entry.getValue(), entry.getKey());
                int rowIdx = 1;
                for (Map.Entry<String, Object> e : flat) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(e.getKey());
                    Object v = e.getValue();
                    row.createCell(1).setCellValue(v == null ? "" : String.valueOf(v));
                }
                idx++;
            }
            wb.write(out);
        }
    }

    /**
     * 递归拍平嵌套 Map/List 为 (路径, 标量) 列表，供报表导出使用。
     */
    private static List<Map.Entry<String, Object>> flatten(Object value, String prefix) {
        List<Map.Entry<String, Object>> result = new ArrayList<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = e.getKey() == null ? "null" : String.valueOf(e.getKey());
                String childPrefix = prefix == null || prefix.isEmpty() ? key : prefix + "." + key;
                result.addAll(flatten(e.getValue(), childPrefix));
            }
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                result.add(new AbstractMap.SimpleEntry<>(prefix == null ? "" : prefix, "[]"));
            } else {
                for (int i = 0; i < list.size(); i++) {
                    result.addAll(flatten(list.get(i), prefix + "[" + i + "]"));
                }
            }
        } else {
            result.add(new AbstractMap.SimpleEntry<>(prefix == null ? "" : prefix, value));
        }
        return result;
    }

    private static void writeHeaderRow(Sheet sheet, String[] headers) {
        Row row = sheet.createRow(0);
        CellStyle style = headerStyle(sheet.getWorkbook());
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
            int width = headers[i].getBytes().length * 256 + 600;
            sheet.setColumnWidth(i, Math.min(45 * 256, Math.max(10 * 256, width)));
        }
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    /**
     * 读取单元格文本值（数字/布尔转字符串，空单元格返回 null）。
     */
    private static String cellStr(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /**
     * Sheet 名安全化：去除非法字符（：\\/?*[] 等）并截断到 31 字符。
     */
    private static String safeSheetName(String raw, int fallbackIdx) {
        String name = raw.replaceAll("[\\\\/:*?\\[\\]]", "").trim();
        if (name.isEmpty()) {
            name = "Sheet" + fallbackIdx;
        }
        return name.length() > 31 ? name.substring(0, 31) : name;
    }
}
