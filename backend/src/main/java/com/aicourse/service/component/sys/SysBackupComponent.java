package com.aicourse.service.component.sys;

import com.aicourse.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.aicourse.util.SysFileUtil;

/**
 * 数据库备份协作者：备份创建（mysqldump + 安全凭据传递）、备份列表、恢复校验（路径防穿越）。
 * <p>由 {@code SystemServiceImpl} 门面委托调用。生产环境建议禁用应用内备份，改用外部编排工具。</p>
 */
@Component
public class SysBackupComponent {

    private static final Logger log = LoggerFactory.getLogger(SysBackupComponent.class);

    private static final String BACKUP_DIR = "backups";
    private static final String SQL_SUFFIX = ".sql";
    private static final String TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN);

    private final DataSource dataSource;
    private final Environment environment;

    public SysBackupComponent(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    /**
     * 创建数据库备份：mysqldump 真实备份（安全加固：--defaults-extra-file 传递凭据、0600 权限、
     * 凭据缺失拒绝执行），失败回退为仅文件名方式。
     */
    public String createBackup() {
        File dir = new File(BACKUP_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String backupFileName = "backup_" + timestamp + SQL_SUFFIX;
        File backupFile = new File(BACKUP_DIR, backupFileName);

        // 尝试使用 mysqldump 执行真实备份
        try {
            // 【安全加固】数据库凭据一律从 Spring 配置读取（环境变量注入，见 application.yaml），
            // 不再从 system_configs 表读取、也不允许回退到硬编码的 root/空密码默认值。
            String jdbcUrl = environment.getProperty("spring.datasource.url");
            String username = environment.getProperty("spring.datasource.username");
            String password = environment.getProperty("spring.datasource.password");

            // 兜底：从已注入的 DataSource 元数据获取 url/username（与连接池必然一致）
            if (jdbcUrl == null || username == null) {
                try (Connection metaConn = dataSource.getConnection()) {
                    if (jdbcUrl == null) {
                        jdbcUrl = metaConn.getMetaData().getURL();
                    }
                    if (username == null) {
                        String metaUser = metaConn.getMetaData().getUserName();
                        // MySQL 驱动可能返回 "user@host"，只需保留用户名部分
                        if (metaUser != null && metaUser.contains("@")) {
                            metaUser = metaUser.substring(0, metaUser.indexOf('@'));
                        }
                        username = metaUser;
                    }
                } catch (Exception metaEx) {
                    log.warn("从 DataSource 获取连接元数据失败: {}", metaEx.getMessage());
                }
            }

            // 密码缺失时拒绝执行，避免使用空密码等不安全默认值
            if (jdbcUrl == null || username == null || password == null || password.isEmpty()) {
                throw new BusinessException("数据库备份需要配置 spring.datasource 的 url/username/password"
                        + "（可通过环境变量 SPRING_DATASOURCE_URL/USERNAME/PASSWORD 或 MYSQL_ROOT_PASSWORD 注入），已取消备份");
            }

            // 解析 JDBC URL: jdbc:mysql://host:port/database?params
            String host = "localhost";
            String port = "3306";
            String database = "ai_course_platform";

            String urlPart = jdbcUrl.replace("jdbc:mysql://", "");
            int paramsIndex = urlPart.indexOf('?');
            if (paramsIndex > 0) {
                urlPart = urlPart.substring(0, paramsIndex);
            }
            int slashIndex = urlPart.indexOf('/');
            if (slashIndex > 0) {
                database = urlPart.substring(slashIndex + 1);
                String hostPort = urlPart.substring(0, slashIndex);
                int colonIndex = hostPort.indexOf(':');
                if (colonIndex > 0) {
                    host = hostPort.substring(0, colonIndex);
                    port = hostPort.substring(colonIndex + 1);
                } else {
                    host = hostPort;
                }
            }

            // 【安全加固】使用 --defaults-extra-file 临时配置文件传递密码，
            // 避免 -p密码/MYSQL_PWD 被 ps aux、/proc/PID/environ 等系统接口暴露数据库凭据。
            // 临时文件设置 0600 权限且在 finally 块中立即删除。
            File tempConfig = null;
            try {
                tempConfig = File.createTempFile("mysqldump-backup-", ".cnf");
                // MySQL 5.6+ 安全要求：凭证文件必须仅 owner 可读写，否则报 "World-writable... ignored"
                if (!tempConfig.setReadable(false, false)) {
                    log.debug("tmp cnf setReadable");
                }
                if (!tempConfig.setWritable(false, false)) {
                    log.debug("tmp cnf setWritable");
                }
                tempConfig.setReadable(true, true);
                tempConfig.setWritable(true, true);

                Files.writeString(tempConfig.toPath(),
                        "[client]\nhost=" + host + "\nport=" + port
                                + "\nuser=" + username + "\npassword=" + password + "\n");

                ProcessBuilder pb = new ProcessBuilder(
                        "mysqldump",
                        "--defaults-extra-file=" + tempConfig.getAbsolutePath(),
                        "--single-transaction",
                        "--routines",
                        "--triggers",
                        database
                );
                pb.redirectErrorStream(true);
                pb.directory(dir);
                Process process = pb.start();

                // 安全修复 #28：必须给 mysqldump 设置超时，否则进程卡死会永久挂起请求线程；
                // 超时/打断一律强制销毁并抛异常，绝不回退为"假备份"。
                int exitCode;
                boolean finished;
                try {
                    finished = process.waitFor(10, TimeUnit.MINUTES);
                } catch (InterruptedException ie) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                    throw new BusinessException("数据库备份被中断，已取消备份");
                }
                if (!finished) {
                    process.destroyForcibly();
                    throw new BusinessException("数据库备份超时（mysqldump 10 分钟内未完成），已取消备份");
                }
                exitCode = process.exitValue();

                // 读取输出并写入文件
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                if (exitCode == 0) {
                    Files.writeString(backupFile.toPath(), output.toString(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    log.info("数据库备份成功: {}", backupFileName);
                    return backupFileName;
                } else {
                    // 非零退出：mysqldump 实际失败。
                    // 安全修复 #28：停止回退到"仅文件名"假备份——否则回滚预案依赖的"当次全量备份"
                    // 会建立在不存在的恢复点上。
                    throw new BusinessException("数据库备份失败（mysqldump 退出码=" + exitCode + "），未生成备份文件");
                }
            } finally {
                if (tempConfig != null && tempConfig.exists()) {
                    tempConfig.delete();
                }
            }
        } catch (BusinessException e) {
            log.error("创建备份失败: {}", e.getMessage(), e);

            // 配置缺失或备份失败属于明确失败，必须抛给调用方，不允许回退为"仅文件名"假备份
            throw e;
        } catch (Exception e) {
            log.error("mysqldump不可用或执行失败, 已取消备份: {}", e.getMessage(), e);
            throw new BusinessException("数据库备份执行异常，未生成备份文件: " + e.getMessage());
        }

        // 说明：任何失败路径均已显式抛出 BusinessException，不再返回"成功"的备份文件名。
    }

    /** 备份文件列表 */
    public List<Map<String, Object>> getBackups() {
        return SysFileUtil.getFileInfoList(BACKUP_DIR, SQL_SUFFIX);
    }

    /** 恢复备份（当前仅做路径防穿越与存在性校验） */
    public void restoreBackup(String fileName) {
        // ---- 路径遍历防护 ----
        // 1. 禁止目录分隔符（含 Unix / 与 Windows \）
        if (fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException("非法文件名：禁止路径分隔符");
        }
        // 2. 禁止父目录引用
        if (fileName.contains("..")) {
            throw new BusinessException("非法文件名：禁止父目录引用");
        }
        // 3. 禁止绝对路径
        if (new File(fileName).isAbsolute()) {
            throw new BusinessException("非法文件名：禁止绝对路径");
        }
        // 4. Path.normalize() + startsWith 校验（防止符号链接等方法绕过）
        Path backupDir = Paths.get(BACKUP_DIR).toAbsolutePath().normalize();
        Path filePath = backupDir.resolve(fileName).normalize();
        if (!filePath.startsWith(backupDir)) {
            throw new BusinessException("非法文件路径：文件不在备份目录内");
        }
        // 5. 文件存在性校验
        if (!Files.exists(filePath)) {
            throw new BusinessException("备份文件不存在");
        }
    }
}
