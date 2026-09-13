package com.aicourse.service.component.sys;

import com.aicourse.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import com.aicourse.util.SysFileUtil;

/**
 * SysLogComponent 单元测试（依赖真实目录，临时文件于测试内创建并清理）。
 * <p>覆盖日志文件列表与读取（关键字过滤/行数截取/路径防穿越）。</p>
 */
class SysLogComponentTest {

    private final SysLogComponent component = new SysLogComponent();

    private static final String LOG_FILE = "logtest_tmp.log";

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(Path.of("logs"));
        Files.writeString(Path.of("logs", LOG_FILE),
                "line1 keyword apple\nline2 banana\nline3 keyword orange\n");
    }

    @Test
    @DisplayName("getLogFiles 返回文件信息")
    void getLogFiles() {
        List<Map<String, Object>> r = component.getLogFiles();
        assertTrue(r.stream().anyMatch(m -> LOG_FILE.equals(m.get("fileName"))));
    }

    @Test
    @DisplayName("readLogFile 含分隔符抛异常")
    void readLogFile_separator() {
        assertThrows(BusinessException.class, () -> component.readLogFile("a/b.log", 10, null));
        assertThrows(BusinessException.class, () -> component.readLogFile("..escape", 10, null));
    }

    @Test
    @DisplayName("readLogFile 文件不存在抛异常")
    void readLogFile_notExists() {
        assertThrows(BusinessException.class, () -> component.readLogFile("nope.log", 10, null));
    }

    @Test
    @DisplayName("readLogFile 成功并按关键字过滤、行数截取")
    void readLogFile_ok() {
        String r = component.readLogFile(LOG_FILE, 10, "keyword");
        assertTrue(r.contains("line1"));
        assertTrue(r.contains("line3"));
        assertFalse(r.contains("line2"));

        // 末尾行数截取
        String twoLines = component.readLogFile(LOG_FILE, 2, null);
        assertTrue(twoLines.contains("line2") && twoLines.contains("line3"));
    }

    @Test
    @DisplayName("目录不存在时 getLogFiles 返回空")
    void getLogFiles_missingDir() {
        List<Map<String, Object>> r = SysFileUtil.getFileInfoList("no-such-dir-xyz", ".log");
        assertTrue(r.isEmpty());
    }
}
