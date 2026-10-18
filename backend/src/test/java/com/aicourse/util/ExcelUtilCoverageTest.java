package com.aicourse.util;

import com.aicourse.entity.Question;
import com.aicourse.entity.User;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * ExcelUtil 覆盖率补测（*CoverageTest 命名惯例，专打既有 {@link ExcelUtilTest} 未触及的分支）。
 *
 * <p>逐条对应源码分支：</p>
 * <ul>
 *   <li>normalizeQuestionType / normalizeDifficulty / normalizePointType：全部合法别名、非法值、空白、null；</li>
 *   <li>normalizeJsonArray / normalizeJsonAny：合法数组原样、合法非数组包装、非法 JSON 按分隔符拆分、
 *       纯分隔符回退整串、空白/null 返回 null；</li>
 *   <li>cellStr：STRING / NUMERIC / BOOLEAN / BLANK(默认分支) / null 单元格五种形态；</li>
 *   <li>readQuestions / parseUsers / readChapters / readKnowledgePoints：null 行跳过、必填列 null 与空串
 *       两种跳过形态、分值 NumberFormatException、toIntOrNull 各分支；</li>
 *   <li>writeQuestions：字段全 null（nullToEmpty 真侧）与 score null（三元空串侧），POI 读回断言；</li>
 *   <li>writeReport / flatten：空串顶层 key（prefix.isEmpty 真侧 + safeSheetName 空名回退 SheetN）、
 *       内层 null key、null 标量值、嵌套 List、空 List；</li>
 *   <li>generateRandomPassword 字符集、setDownloadHeaders 空格 %20 编码、isExcelFile 无扩展名形态。</li>
 * </ul>
 *
 * <p>刻意跳过（详见交付报告）：toBoolOrNull 的 default 侧（HEAD 已重构为二态 toPublishedFlag，断言会随合并
 * 漂移，故只测两种实现语义一致的显式写法）；normalizeJson* 的内层 writeValueAsString catch（字符串列表
 * 序列化不可能抛异常，不可达）；flatten 的 prefix==null 真侧（公开入口恒传非 null key，不可达）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExcelUtilCoverageTest {

    @Mock
    private HttpServletResponse response;

    /** 哨兵对象：在该列创建 BLANK 单元格（走 cellStr 的 default 分支）。 */
    private static final Object BLANK = new Object();

    /**
     * 按行造 xlsx：List 元素为 Object[]（列值），null 元素表示整行缺失（空洞行，getRow 返回 null）。
     * 列值：null=不建单元格；BLANK=建空单元格；Boolean/Number=原生类型单元格；其余=文本。
     */
    private static MultipartFile workbook(String sheetName, List<Object[]> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            Row header = sheet.createRow(0);
            for (int i = 0; i < 10; i++) {
                header.createCell(i).setCellValue("h" + i);
            }
            int rowIdx = 1;
            for (Object[] r : rows) {
                if (r == null) {
                    rowIdx++; // 空洞行
                    continue;
                }
                Row row = sheet.createRow(rowIdx++);
                for (int c = 0; c < r.length; c++) {
                    Object v = r[c];
                    if (v == null) {
                        continue;
                    }
                    Cell cell = row.createCell(c);
                    if (v == BLANK) {
                        continue; // 只建不赋值 -> BLANK 类型
                    }
                    if (v instanceof Boolean b) {
                        cell.setCellValue(b);
                    } else if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else {
                        cell.setCellValue(String.valueOf(v));
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("f", out.toByteArray());
        }
    }

    // ==================== 归一化三件套：逐 case 命中 ====================

    @Test
    @DisplayName("readQuestions：题型列 20 种写法逐一命中 normalizeQuestionType 各 case 与 default/blank/null 侧")
    void readQuestions_coversEveryQuestionTypeCase() throws Exception {
        String[] variants = {
                "SINGLE_CHOICE", "单选", "单选题",
                "MULTIPLE_CHOICE", "MULTIPLE", "多选", "多选题",
                "TRUE_FALSE", "JUDGE", "判断", "判断题",
                "FILL_BLANK", "FILL", "填空", "填空题",
                "SHORT_ANSWER", "ESSAY", "简答", "简答题",
                "选择题", "   ", null
        };
        String[] expected = {
                "SINGLE_CHOICE", "SINGLE_CHOICE", "SINGLE_CHOICE",
                "MULTIPLE_CHOICE", "MULTIPLE_CHOICE", "MULTIPLE_CHOICE", "MULTIPLE_CHOICE",
                "TRUE_FALSE", "TRUE_FALSE", "TRUE_FALSE", "TRUE_FALSE",
                "FILL_BLANK", "FILL_BLANK", "FILL_BLANK", "FILL_BLANK",
                "SHORT_ANSWER", "SHORT_ANSWER", "SHORT_ANSWER", "SHORT_ANSWER",
                null, null, null
        };
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < variants.length; i++) {
            rows.add(new Object[]{variants[i], "题干" + i});
        }
        List<Question> qs = ExcelUtil.readQuestions(workbook("题库", rows));
        assertEquals(variants.length, qs.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], qs.get(i).getType(), "row " + i + " 输入=" + variants[i]);
            assertEquals("题干" + i, qs.get(i).getContent());
        }
    }

    @Test
    @DisplayName("readQuestions：难度列 12 种写法逐一命中 normalizeDifficulty 各 case 与 default/blank 侧")
    void readQuestions_coversEveryDifficultyCase() throws Exception {
        String[] variants = {"EASY", "BASIC", "简单", "容易",
                "MEDIUM", "NORMAL", "INTERMEDIATE", "中等",
                "HARD", "ADVANCED", "DIFFICULT", "困难",
                "地狱难度", "  "};
        String[] expected = {"EASY", "EASY", "EASY", "EASY",
                "MEDIUM", "MEDIUM", "MEDIUM", "MEDIUM",
                "HARD", "HARD", "HARD", "HARD",
                null, null};
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < variants.length; i++) {
            rows.add(new Object[]{"single", "t" + i, null, null, variants[i]});
        }
        List<Question> qs = ExcelUtil.readQuestions(workbook("题库", rows));
        assertEquals(variants.length, qs.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], qs.get(i).getDifficulty(), "row " + i + " 输入=" + variants[i]);
        }
    }

    @Test
    @DisplayName("readKnowledgePoints：类型列逐一命中 normalizePointType 各 case、default、空白与 null 侧")
    void readKnowledgePoints_coversEveryPointTypeCase() throws Exception {
        List<Object[]> rows = Arrays.asList(
                new Object[]{"n1", null, null, null, null, "CONCEPT"},
                new Object[]{"n2", null, null, null, null, "概念"},
                new Object[]{"n3", null, null, null, null, "SKILL"},
                new Object[]{"n4", null, null, null, null, "技能"},
                new Object[]{"n5", null, null, null, null, "PRINCIPLE"},
                new Object[]{"n6", null, null, null, null, "原理"},
                new Object[]{"n7", null, null, null, null, "乱写的"},
                new Object[]{"n8", null, null, null, null, " "},
                new Object[]{"n9"} // 类型列缺单元格 -> cellStr null -> normalizePointType null 侧
        );
        List<ExcelUtil.KnowledgeRow> list = ExcelUtil.readKnowledgePoints(workbook("知识点", rows));
        assertEquals(9, list.size());
        assertEquals(List.of("concept", "concept", "skill", "skill", "principle", "principle"),
                list.subList(0, 6).stream().map(ExcelUtil.KnowledgeRow::pointType).toList());
        assertNull(list.get(6).pointType());
        assertNull(list.get(7).pointType());
        assertNull(list.get(8).pointType());
    }

    // ==================== JSON 规范化分支 ====================

    @Test
    @DisplayName("readQuestions：选项/正确答案列的合法数组、合法非数组、非法拆分、纯分隔符、空白五形态")
    void readQuestions_coversJsonNormalizationBranches() throws Exception {
        List<Object[]> rows = Arrays.asList(
                // 合法 JSON 数组原样保留 + 正确答案合法数组原样 + 数字分值单元格
                new Object[]{"single", "c1", "[\"a\",\"b\"]", "[\"0\"]", "EASY", 4.5},
                // 合法 JSON 非数组（数字）-> 包装为单元素数组；正确答案布尔原样
                new Object[]{"single", "c2", "42", "true"},
                // 非法 JSON -> 按 , ; 换行 拆分为数组；非法答案 -> 包装单元素
                new Object[]{"single", "c3", "数据结构基础, 队列;二叉树\r\n第三项", "def"},
                // 非法且拆分后全为空 -> parts.isEmpty 真侧回退整串
                new Object[]{"single", "c4", ",，;"},
                // 空白列 -> null；分值列空串 -> 跳过 setScore（score!=null && !isEmpty 假侧）
                new Object[]{"single", "c5", "   ", null, null, ""}
        );
        List<Question> qs = ExcelUtil.readQuestions(workbook("题库", rows));
        assertEquals(5, qs.size());

        assertEquals("[\"a\",\"b\"]", qs.get(0).getOptions());
        assertEquals("[\"0\"]", qs.get(0).getCorrectAnswers());
        assertEquals(new java.math.BigDecimal("4.5"), qs.get(0).getScore());

        assertEquals("[\"42\"]", qs.get(1).getOptions());
        assertEquals("true", qs.get(1).getCorrectAnswers());

        assertEquals("[\"数据结构基础\",\"队列\",\"二叉树\",\"第三项\"]", qs.get(2).getOptions());
        assertEquals("[\"def\"]", qs.get(2).getCorrectAnswers());

        assertEquals("[\",，;\"]", qs.get(3).getOptions());

        assertNull(qs.get(4).getOptions());
        assertNull(qs.get(4).getCorrectAnswers());
        assertNull(qs.get(4).getScore());
    }

    @Test
    @DisplayName("readQuestions：分值非法字符串走 NumberFormatException 分支置 null")
    void readQuestions_scoreParseFailureYieldsNull() throws Exception {
        List<Question> qs = ExcelUtil.readQuestions(workbook("题库",
                List.<Object[]>of(new Object[]{"single", "c", null, null, null, "abc"})));
        assertEquals(1, qs.size());
        assertNull(qs.get(0).getScore());
    }

    // ==================== cellStr 各类型 + 行/列跳过形态 ====================

    @Test
    @DisplayName("readQuestions：数字/布尔/BLANK/缺失单元格与空洞行、空题干行的跳过分支")
    void readQuestions_coversCellTypesAndRowSkips() throws Exception {
        List<Object[]> rows = Arrays.asList(
                // 题干=数字单元格（NUMERIC 转文本可解析）；题型=布尔单元格 -> normalizeQuestionType default
                new Object[]{true, 123.0},
                // 题干空串 -> isEmpty 真侧跳过
                new Object[]{"single", ""},
                // 空洞行（getRow == null）跳过
                null,
                // 正常行：知识点/解析列建 BLANK 单元格 -> cellStr default 分支 -> null
                new Object[]{"single", "gap-check", BLANK, BLANK, BLANK, BLANK, BLANK, BLANK}
        );
        List<Question> qs = ExcelUtil.readQuestions(workbook("题库", rows));
        assertEquals(2, qs.size());
        assertEquals("123", qs.get(0).getContent());
        // 题型列是布尔单元格，cellStr 得 "true"，normalizeQuestionType 不识别 -> null
        assertNull(qs.get(0).getType());
        assertEquals("gap-check", qs.get(1).getContent());
        assertNull(qs.get(1).getKnowledgePoints());
        assertNull(qs.get(1).getExplanation());
    }

    @Test
    @DisplayName("readChapters：标题 null/空串跳过、空洞行、数字列取整、toBool 显式写法各分支")
    void readChapters_coversAllParsingBranches() throws Exception {
        List<Object[]> rows = Arrays.asList(
                new Object[]{"第1章", "父A", 3.0, 20.5, "是", "内容1"},
                new Object[]{"第2章", "", "abc", null, "否", ""},
                new Object[]{"", "x"},                    // 标题空串 -> isEmpty 跳过
                new Object[]{null},                       // 标题缺失 -> null 跳过
                null,                                     // 空洞行
                new Object[]{"第3章", null, null, null, 1.0},   // 数字 1 -> "1" -> TRUE
                new Object[]{"第4章", null, null, null, "true"},
                new Object[]{"第5章", null, null, null, "y"},
                new Object[]{"第6章", null, null, null, "YES"},
                new Object[]{"第7章", null, null, null, "false"},
                new Object[]{"第8章", null, null, null, "N"},
                new Object[]{"第9章", null, null, null, "no"},
                new Object[]{"第10章", null, null, null, "0"}
        );
        List<ExcelUtil.ChapterRow> list = ExcelUtil.readChapters(workbook("章节", rows));
        assertEquals(10, list.size());

        ExcelUtil.ChapterRow r1 = list.get(0);
        assertEquals("第1章", r1.title());
        assertEquals("父A", r1.parentTitle());
        assertEquals(3, r1.orderIndex());           // 3.0 -> 3
        assertEquals(20, r1.duration());            // 20.5 -> 截断 20
        assertTrue(Boolean.TRUE.equals(r1.isPublished()), "「是」应判已发布");
        assertEquals("内容1", r1.content());

        ExcelUtil.ChapterRow r2 = list.get(1);
        assertNull(r2.parentTitle());               // 空串 -> emptyToNull
        assertNull(r2.orderIndex());                // "abc" -> NumberFormatException -> null
        assertNull(r2.duration());                  // 缺单元格 -> null
        assertTrue(Boolean.FALSE.equals(r2.isPublished()), "「否」应判未发布");
        assertNull(r2.content());

        assertTrue(Boolean.TRUE.equals(list.get(2).isPublished()), "数字 1 应判已发布");
        // NEGATIVE_WORDS = 否/false/0/n/no：true、y、YES 均落「其余一律已发布」侧
        for (int i = 3; i <= 5; i++) {
            assertTrue(Boolean.TRUE.equals(list.get(i).isPublished()), "row " + i);
        }
        // index 6 起为 false / N / no / 0，命中否定词表
        for (int i = 6; i <= 9; i++) {
            assertTrue(Boolean.FALSE.equals(list.get(i).isPublished()), "row " + i);
        }
    }

    @Test
    @DisplayName("readKnowledgePoints：名称跳过形态、空洞行与 toIntOrNull 数值/非法/空白分支")
    void readKnowledgePoints_coversRowSkipsAndIntParsing() throws Exception {
        List<Object[]> rows = Arrays.asList(
                new Object[]{"k1", "父K", "EASY", "8", 15.0, "concept", "描述"},
                new Object[]{"k2", null, "乱写", "abc", "   ", null, ""},
                new Object[]{""},   // 名称空串 -> 跳过
                null,               // 空洞行 -> 跳过
                new Object[]{null}   // 名称缺失 -> 跳过
        );
        List<ExcelUtil.KnowledgeRow> list = ExcelUtil.readKnowledgePoints(workbook("知识点", rows));
        assertEquals(2, list.size());
        assertEquals("k1", list.get(0).name());
        assertEquals("父K", list.get(0).parentName());
        assertEquals("EASY", list.get(0).difficultyLevel());
        assertEquals(8, list.get(0).importance());
        assertEquals(15, list.get(0).estimatedTime());
        assertEquals("concept", list.get(0).pointType());
        assertEquals("描述", list.get(0).description());

        assertNull(list.get(1).difficultyLevel());
        assertNull(list.get(1).importance());
        assertNull(list.get(1).estimatedTime());
        assertNull(list.get(1).pointType());
        assertNull(list.get(1).description());
    }

    @Test
    @DisplayName("readUsers：空串用户名跳过、空串密码随机生成、数字单元格读取")
    void readUsers_coversEmptyStringAndNumericCellBranches() throws Exception {
        List<Object[]> rows = Arrays.asList(
                new Object[]{"", "无用户名"},          // 用户名空串 -> isEmpty 真侧跳过
                new Object[]{null, "无用户名"},         // 用户名缺失 -> null 侧跳过
                null,                                  // 空洞行
                new Object[]{"stu01", null, null, "STUDENT", "", 13800000000.0, 20240001.0}
        );
        List<User> users = ExcelUtil.readUsers(workbook("用户", rows));
        assertEquals(1, users.size());
        User u = users.get(0);
        assertEquals("stu01", u.getUsername());
        assertEquals(8, u.getPassword().length());     // 空串密码 -> 随机 8 位
        assertEquals("13800000000", u.getPhone());     // NUMERIC -> 去尾零纯文本
        assertEquals("20240001", u.getStudentId());
        assertNull(u.getFullName());
    }

    // ==================== 导出侧：nullToEmpty / score 三元 / flatten 分支 ====================

    @Test
    @DisplayName("writeQuestions：全 null 字段与 score null 导出的空串单元格，POI 读回断言")
    void writeQuestions_nullFieldsExportAsEmptyStrings() throws Exception {
        Question full = new Question();
        full.setType("SINGLE_CHOICE");
        full.setContent("1+1");
        full.setScore(new java.math.BigDecimal("5.50"));

        Question empty = new Question(); // 全部字段 null

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelUtil.writeQuestions(out, List.of(full, empty));

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertEquals("题型", header.getCell(0).getStringCellValue());
            assertEquals("解析", header.getCell(7).getStringCellValue());
            Row r1 = sheet.getRow(1);
            assertEquals("SINGLE_CHOICE", r1.getCell(0).getStringCellValue());
            // 5.50 stripTrailingZeros -> 5.5
            assertEquals("5.5", r1.getCell(5).getStringCellValue());
            Row r2 = sheet.getRow(2);
            for (int c = 0; c < 8; c++) {
                assertEquals("", r2.getCell(c).getStringCellValue(), "列 " + c + " 应为空串");
            }
        }
    }

    @Test
    @DisplayName("writeReport：空串 sheet 名回退 SheetN、内层 null key、null 值、嵌套/空 List，POI 读回断言")
    void writeReport_coversFlattenAndSheetNameFallbacks() throws Exception {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put(null, 5);       // 内层 null key -> 路径 "null"
        inner.put("v", null);     // null 标量 -> 空串单元格
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("", inner);                   // 空串顶层 key -> Sheet0 + flatten prefix.isEmpty 真侧
        report.put("s1", List.of(List.of("a"))); // 嵌套 List -> 路径 s1[0][1]
        report.put("s2", List.of());             // 空 List -> "[]"

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelUtil.writeReport(out, report);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
            assertEquals(3, wb.getNumberOfSheets());
            Sheet s0 = wb.getSheet("Sheet0");
            assertNotNull(s0, "空串 key 应回退为 Sheet0");
            assertEquals("指标", s0.getRow(0).getCell(0).getStringCellValue());
            assertEquals("null", s0.getRow(1).getCell(0).getStringCellValue());
            assertEquals("5", s0.getRow(1).getCell(1).getStringCellValue());
            assertEquals("v", s0.getRow(2).getCell(0).getStringCellValue());
            assertEquals("", s0.getRow(2).getCell(1).getStringCellValue());
            assertEquals("s1[0][0]", wb.getSheet("s1").getRow(1).getCell(0).getStringCellValue());
            assertEquals("a", wb.getSheet("s1").getRow(1).getCell(1).getStringCellValue());
            assertEquals("[]", wb.getSheet("s2").getRow(1).getCell(1).getStringCellValue());
        }
    }

    @Test
    @DisplayName("writeReport：仅含非法字符的 sheet 名清洗后为空，同样回退 SheetN")
    void writeReport_illegalOnlySheetNameFallsBack() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelUtil.writeReport(out, Map.of("/:*?[]\\", "x"));
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
            assertNotNull(wb.getSheet("Sheet0"));
            assertEquals("x", wb.getSheet("Sheet0").getRow(1).getCell(1).getStringCellValue());
        }
    }

    // ==================== 杂项入口 ====================

    @Test
    @DisplayName("generateRandomPassword：恒 8 位且只含去混淆字符集")
    void generateRandomPassword_lengthAndCharset() {
        Set<Character> allowed = new HashSet<>();
        for (char c : "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789".toCharArray()) {
            allowed.add(c);
        }
        for (int i = 0; i < 20; i++) {
            String pwd = ExcelUtil.generateRandomPassword();
            assertEquals(8, pwd.length());
            for (char c : pwd.toCharArray()) {
                assertTrue(allowed.contains(c), "非法字符: " + c);
            }
        }
    }

    @Test
    @DisplayName("setDownloadHeaders：空格经 URL 编码为 %20 而非加号")
    void setDownloadHeaders_encodesSpaceAsPercent20() throws Exception {
        ExcelUtil.setDownloadHeaders(response, "题库 导出.xlsx");
        verify(response).setHeader(org.mockito.ArgumentMatchers.eq("Content-Disposition"),
                org.mockito.ArgumentMatchers.contains("filename*=UTF-8''%E9%A2%98%E5%BA%93%20%E5%AF%BC%E5%87%BA.xlsx"));
    }

    @Test
    @DisplayName("isExcelFile：无扩展名/空串形态为 false，大写后缀为 true")
    void isExcelFile_rejectsNoExtensionForms() {
        assertFalse(ExcelUtil.isExcelFile("xlsx"));
        assertFalse(ExcelUtil.isExcelFile(""));
        assertTrue(ExcelUtil.isExcelFile("报表.XLSX"));
    }
}
