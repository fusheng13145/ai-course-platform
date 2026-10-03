package com.aicourse.util;

import com.aicourse.entity.Question;
import com.aicourse.entity.User;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * ExcelUtil 单元测试（纯 POI，无外部依赖）。
 * 覆盖：setDownloadHeaders / isExcelFile / writeQuestions / readQuestions（含各类单元格类型与
 * 分值解析失败分支）/ readUsers（默认密码）/ writeReport（含嵌套 Map 与 List 触发 flatten 各分支）/
 * safeSheetName（非法字符与超长）。
 */
@ExtendWith(MockitoExtension.class)
class ExcelUtilTest {

    @Mock
    private HttpServletResponse response;

    @Test
    void setDownloadHeaders_setsContentTypeAndDisposition() throws Exception {
        ExcelUtil.setDownloadHeaders(response, "题库.xlsx");
        verify(response).setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        verify(response).setHeader(org.mockito.ArgumentMatchers.eq("Content-Disposition"),
                org.mockito.ArgumentMatchers.contains("filename*=UTF-8''"));
    }

    @Test
    void isExcelFile() {
        assertTrue(ExcelUtil.isExcelFile("a.xlsx"));
        assertTrue(ExcelUtil.isExcelFile("A.XLSX"));
        assertFalse(ExcelUtil.isExcelFile("a.txt"));
        assertFalse(ExcelUtil.isExcelFile(null));
    }

    @Test
    void writeAndReadQuestions_roundTrip() throws Exception {
        Question q = new Question();
        q.setType("single");
        q.setContent("1+1=?");
        q.setOptions("[\"2\"]");
        q.setCorrectAnswers("2");
        q.setDifficulty("easy");
        q.setScore(java.math.BigDecimal.valueOf(5));
        q.setKnowledgePoints("math");
        q.setExplanation("ans");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelUtil.writeQuestions(out, List.of(q));

        MultipartFile file = new MockMultipartFile("f", "q.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));
        List<Question> read = ExcelUtil.readQuestions(file);

        assertEquals(1, read.size());
        Question r = read.get(0);
        // 2026-09 修复分支将题型导入归一化为全大写字典值（与 Vote/ExamSessionGradingService 等主流程一致）
        assertEquals("SINGLE_CHOICE", r.getType());
        assertEquals("1+1=?", r.getContent());
        assertEquals("[\"2\"]", r.getOptions());
        assertEquals("2", r.getCorrectAnswers());
        assertEquals("EASY", r.getDifficulty());
        assertEquals(java.math.BigDecimal.valueOf(5), r.getScore());
    }

    @Test
    void readQuestions_coversCellTypesAndScoreParseError() throws Exception {
        // 手动构造包含多种单元格类型与非法分值的行，触发 cellStr 各分支与日志分支
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("题库");
            Row header = sheet.createRow(0);
            for (int i = 0; i < 8; i++) header.createCell(i).setCellValue("h" + i);
            // 行1：STRING / NUMERIC / BOOLEAN / 非法分值（"abc"）
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("single");
            row1.createCell(1).setCellValue("content text");
            row1.createCell(2).setCellValue("[\"A\"]");
            row1.createCell(3).setCellValue("A");
            row1.createCell(4).setCellValue("easy");
            row1.createCell(5).setCellValue("abc"); // 分值解析失败 -> setScore(null)
            row1.createCell(6).setCellValue("math");
            row1.createCell(7).setCellValue("exp");
            // 行2：空内容行（content 为空 -> 跳过）
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("single");
            // 第1列(content)留空

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);

            MultipartFile file = new MockMultipartFile("f", out.toByteArray());
            List<Question> read = ExcelUtil.readQuestions(file);
            assertEquals(1, read.size());
            assertNull(read.get(0).getScore()); // 非法分值 -> null
        }
    }

    @Test
    void readUsers_randomPasswordAndSkip() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("users");
            Row header = sheet.createRow(0);
            for (int i = 0; i < 10; i++) header.createCell(i).setCellValue("c" + i);
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("zhangsan");   // username
            row1.createCell(1).setCellValue("张三");         // fullName
            row1.createCell(2).setCellValue("z@x.com");     // email
            row1.createCell(3).setCellValue("STUDENT");     // role
            // cell(4) password 留空 -> 随机生成 8 位（不再固定 123456）
            row1.createCell(5).setCellValue("13800000000"); // phone
            Row row2 = sheet.createRow(2); // 无 username -> 跳过

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            MultipartFile file = new MockMultipartFile("f", out.toByteArray());
            List<User> read = ExcelUtil.readUsers(file);
            assertEquals(1, read.size());
            assertEquals("zhangsan", read.get(0).getUsername());
            assertEquals("张三", read.get(0).getFullName());
            assertEquals("STUDENT", read.get(0).getRole());
            String pwd = read.get(0).getPassword();
            assertNotNull(pwd);
            assertEquals(8, pwd.length()); // 随机 8 位
            assertNotEquals("123456", pwd); // 不再使用固定弱口令
        }
    }

    @Test
    void readUsers_explicitPasswordKept() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("users");
            Row header = sheet.createRow(0);
            for (int i = 0; i < 10; i++) header.createCell(i).setCellValue("c" + i);
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("lisi");
            row1.createCell(4).setCellValue("MyP@ss-2026"); // 显式填写密码 -> 原样保留

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            MultipartFile file = new MockMultipartFile("f", out.toByteArray());
            List<User> read = ExcelUtil.readUsers(file);
            assertEquals(1, read.size());
            assertEquals("MyP@ss-2026", read.get(0).getPassword());
        }
    }

    @Test
    void readUsersWithGenerated_returnsTempPasswordMap() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("users");
            Row header = sheet.createRow(0);
            for (int i = 0; i < 10; i++) header.createCell(i).setCellValue("c" + i);
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("wangwu"); // 密码留空 -> 随机
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("zhaoliu");
            row2.createCell(4).setCellValue("FixedPass1"); // 显式密码 -> 不进映射

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            MultipartFile file = new MockMultipartFile("f", out.toByteArray());
            ExcelUtil.UserImportResult result = ExcelUtil.readUsersWithGenerated(file);
            assertEquals(2, result.users().size());
            assertEquals(1, result.generatedPasswords().size());
            assertTrue(result.generatedPasswords().containsKey("wangwu"));
            assertEquals(8, result.generatedPasswords().get("wangwu").length());
            assertFalse(result.generatedPasswords().containsKey("zhaoliu"));
        }
    }

    @Test
    void writeReport_flattensNestedMapAndList() throws Exception {
        Map<String, Object> report = Map.of(
                "summary", Map.of("total", 10, "pass", 8),
                "list", List.of(Map.of("name", "a"), Map.of("name", "b")),
                "emptyList", List.of(),
                "scalar", "v");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelUtil.writeReport(out, report);
        assertTrue(out.size() > 0);
    }

    @Test
    void writeReport_safeSheetNameWithIllegalCharsAndLong() throws Exception {
        String longName = "a".repeat(40) + "/\\:*?[]";
        Map<String, Object> report = Map.of(longName, Map.of("k", "v"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelUtil.writeReport(out, report);
        assertTrue(out.size() > 0);
    }
}
