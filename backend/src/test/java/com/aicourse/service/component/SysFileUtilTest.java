package com.aicourse.service.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SysFileUtil 单元测试：目录文件遍历（后缀过滤/多文件/忽略无关文件）。
 */
class SysFileUtilTest {

    @Test
    @DisplayName("getFileInfoList 覆盖文件遍历分支")
    void getFileInfoList() throws IOException {
        Path tmp = Files.createTempDirectory("sys-file-util");
        try {
            Files.writeString(tmp.resolve("a.sql"), "select 1;");
            Files.writeString(tmp.resolve("b.sql"), "select 2;");
            Files.writeString(tmp.resolve("ignore.txt"), "x");
            List<Map<String, Object>> list = SysFileUtil.getFileInfoList(tmp.toString(), ".sql");
            assertEquals(2, list.size());
            assertTrue(list.stream().allMatch(m -> m.get("fileName") != null && m.get("size") != null));
        } finally {
            Files.list(tmp).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("目录不存在返回空列表")
    void getFileInfoList_missingDir() {
        assertTrue(SysFileUtil.getFileInfoList("no-such-dir-abc", ".sql").isEmpty());
    }
}
