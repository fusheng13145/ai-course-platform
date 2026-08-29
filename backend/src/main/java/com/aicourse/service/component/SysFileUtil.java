package com.aicourse.service.component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;

/**
 * 系统文件工具：读取目录下指定后缀文件信息（名称、大小、修改时间）。
 * <p>供备份组件与日志组件共享，保持无状态、可独立测试。</p>
 */
public final class SysFileUtil {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private SysFileUtil() {
    }

    /**
     * 读取目录下指定后缀文件信息（名称、大小、修改时间）
     *
     * @param dirPath 目录路径
     * @param suffix  文件后缀
     * @return 文件信息集合
     */
    public static List<Map<String, Object>> getFileInfoList(String dirPath, String suffix) {
        List<Map<String, Object>> fileList = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return fileList;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(suffix));
        if (files == null || files.length == 0) {
            return fileList;
        }

        for (File file : files) {
            Map<String, Object> fileMap = new HashMap<>(4);
            fileMap.put("fileName", file.getName());
            fileMap.put("size", file.length());
            LocalDateTime modifyTime = Instant.ofEpochMilli(file.lastModified())
                    .atZone(SYSTEM_ZONE)
                    .toLocalDateTime();
            fileMap.put("lastModified", modifyTime);
            fileList.add(fileMap);
        }
        return fileList;
    }
}
