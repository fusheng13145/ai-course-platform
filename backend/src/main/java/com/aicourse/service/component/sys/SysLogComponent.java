package com.aicourse.service.component.sys;

import com.aicourse.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.aicourse.util.SysFileUtil;

/**
 * 系统日志协作者：日志文件列表与内容读取（含关键字过滤与路径防穿越）。
 * <p>由 {@code SystemServiceImpl} 门面委托调用。</p>
 */
@Component
public class SysLogComponent {

    private static final Logger log = LoggerFactory.getLogger(SysLogComponent.class);

    private static final String LOG_DIR = "logs";
    private static final String LOG_SUFFIX = ".log";

    /** 日志文件列表 */
    public List<Map<String, Object>> getLogFiles() {
        return SysFileUtil.getFileInfoList(LOG_DIR, LOG_SUFFIX);
    }

    /** 读取日志文件末尾指定行数（支持关键字过滤，路径防穿越） */
    public String readLogFile(String fileName, int lines, String keyword) {
        // 路径防穿越
        if (fileName.contains(File.separator) || fileName.contains("..")) {
            throw new BusinessException("非法文件名");
        }
        Path logPath = Paths.get(LOG_DIR, fileName);
        if (!Files.exists(logPath)) {
            throw new BusinessException("日志文件不存在");
        }

        try (Stream<String> lineStream = Files.lines(logPath)) {
            List<String> allLines = lineStream.collect(Collectors.toList());

            // 关键字过滤
            if (keyword != null && !keyword.isBlank()) {
                allLines = allLines.stream()
                        .filter(line -> line.contains(keyword))
                        .collect(Collectors.toList());
            }

            // 取末尾指定行数
            int startIndex = Math.max(0, allLines.size() - lines);
            List<String> resultLines = allLines.subList(startIndex, allLines.size());
            return String.join("\n", resultLines);
        } catch (IOException e) {
            log.error("读取日志文件失败: {}", e.getMessage(), e);

            throw new BusinessException("读取日志文件失败");
        }
    }
}
