package com.aicourse.util;

import com.aicourse.entity.Question;
import com.aicourse.entity.User;
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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Excel 导入导出工具（POI XSSF 实现 .xlsx）。
 * <p>统一处理：下载响应头（中文文件名 URL 编码）、表头样式、题库导入/导出、
 * 用户批量导入、数据报表多 Sheet 导出。</p>
 */
public final class ExcelUtil {

    private ExcelUtil() {
    }

    /** 题库导出列头（与导入列严格一致） */
    private static final String[] QUESTION_HEADERS =
            {"题型", "题干", "选项(JSON)", "正确答案", "难度", "分值", "知识点", "解析"};

    /** 用户导入列头 */
    private static final String[] USER_HEADERS =
            {"用户名", "姓名", "邮箱", "角色", "初始密码", "手机号", "学号", "专业", "年级", "院系"};

    /** 默认初始密码（导入时未填写密码的兜底值，与种子数据一致） */
    private static final String DEFAULT_PASSWORD = "123456";

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
                q.setType(emptyToNull(cellStr(row.getCell(0))));
                q.setContent(content);
                q.setOptions(emptyToNull(cellStr(row.getCell(2))));
                q.setCorrectAnswers(emptyToNull(cellStr(row.getCell(3))));
                q.setDifficulty(emptyToNull(cellStr(row.getCell(4))));
                String score = cellStr(row.getCell(5));
                if (score != null && !score.isEmpty()) {
                    try {
                        q.setScore(new BigDecimal(score));
                    } catch (NumberFormatException e) {
                        q.setScore(null);
                    }
                }
                q.setKnowledgePoints(emptyToNull(cellStr(row.getCell(6))));
                q.setExplanation(emptyToNull(cellStr(row.getCell(7))));
                list.add(q);
            }
        }
        return list;
    }

    /**
     * 用户导入：从 xlsx 文件解析用户列表（跳过空行与表头行，密码缺省用默认值）。
     */
    public static List<User> readUsers(MultipartFile file) throws IOException {
        List<User> list = new ArrayList<>();
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
                u.setPassword(password == null || password.isEmpty() ? DEFAULT_PASSWORD : password);
                u.setPhone(emptyToNull(cellStr(row.getCell(5))));
                u.setStudentId(emptyToNull(cellStr(row.getCell(6))));
                u.setMajor(emptyToNull(cellStr(row.getCell(7))));
                u.setGrade(emptyToNull(cellStr(row.getCell(8))));
                u.setDepartment(emptyToNull(cellStr(row.getCell(9))));
                list.add(u);
            }
        }
        return list;
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
