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

    /** 连接要素（从 spring.datasource + DataSource 元数据解析），供备份/恢复共用。 */
    private record ConnectionInfo(String host, String port, String database, String username, String password) {
    }

    /**
     * 解析数据库连接要素（原 createBackup 内联逻辑抽取，2026-11-14 P0-2B 恢复实现共用）。
     * 凭据一律取 Spring 配置 + DataSource 元数据兜底；密码缺失直接拒绝（fail-closed）。
     */
    private ConnectionInfo resolveConnection() {
        String jdbcUrl = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        if (jdbcUrl == null || username == null) {
            try (Connection metaConn = dataSource.getConnection()) {
                if (jdbcUrl == null) {
                    jdbcUrl = metaConn.getMetaData().getURL();
                }
                if (username == null) {
                    String metaUser = metaConn.getMetaData().getUserName();
                    if (metaUser != null && metaUser.contains("@")) {
                        metaUser = metaUser.substring(0, metaUser.indexOf('@'));
                    }
                    username = metaUser;
                }
            } catch (Exception metaEx) {
                log.warn("从 DataSource 获取连接元数据失败: {}", metaEx.getMessage());
            }
        }
        if (jdbcUrl == null || username == null || password == null || password.isEmpty()) {
            throw new BusinessException("数据库备份/恢复需要配置 spring.datasource 的 url/username/password"
                    + "（可通过环境变量 SPRING_DATASOURCE_URL/USERNAME/PASSWORD 或 MYSQL_ROOT_PASSWORD 注入），已取消执行");
        }

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
        return new ConnectionInfo(host, port, database, username, password);
    }

    /** 写临时凭据文件（0600 + finally 删除由调用方负责），返回绝对路径。 */
    private File writeTempCnf(ConnectionInfo conn) {
        try {
            File tempConfig = File.createTempFile("aicourse-mysql-", ".cnf");
            tempConfig.setReadable(false, false);
            tempConfig.setWritable(false, false);
            tempConfig.setReadable(true, true);
            tempConfig.setWritable(true, true);
            Files.writeString(tempConfig.toPath(),
                    "[client]\nhost=" + conn.host() + "\nport=" + conn.port()
                            + "\nuser=" + conn.username() + "\npassword=" + conn.password() + "\n");
            return tempConfig;
        } catch (IOException e) {
            throw new BusinessException("临时凭据文件创建失败，已取消执行: " + e.getMessage());
        }
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
            ConnectionInfo conn = resolveConnection();
            String host = conn.host();
            String port = conn.port();
            String database = conn.database();

            // 【安全加固】使用 --defaults-extra-file 临时配置文件传递密码，
            // 避免 -p密码/MYSQL_PWD 被 ps aux、/proc/PID/environ 等系统接口暴露数据库凭据。
            // 临时文件设置 0600 权限且在 finally 块中立即删除。
            File tempConfig = null;
            try {
                tempConfig = writeTempCnf(conn);

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

    /** 隔离恢复目标库的合法前缀（与 scripts/restore-mysql.sh 同口径）。 */
    private static final List<String> RESTORE_ISOLATION_PREFIXES = List.of("aicourse_restore_", "restore_");

    /**
     * 恢复备份（2026-11-14 P0-2B/Q2=B 真实现，替换原「仅校验不执行」的假实现）。
     *
     * <p>执行路径与 createBackup 对称：凭据经 --defaults-extra-file 临时文件传递（0600 +
     * finally 删除）；目标库二选一——</p>
     * <ul>
     *   <li>显式 {@code targetDb}：<b>必须带 {@code aicourse_restore_}/{@code restore_} 前缀</b>
     *       （与 shell 恢复脚本同款隔离护栏，杜绝把任意库名当恢复靶子）；</li>
     *   <li>缺省：恢复到当前应用库（{@code spring.datasource.url} 解析出的 schema）——
     *       这是明确的破坏性动作，调用方（管理端）须在维护窗口执行；恢复后应用连接池
     *       内的既有连接见到的是恢复后的表结构，无需重启（MySQL schema 级切换）。</li>
     * </ul>
     * <p>.gz 备份经 gunzip 子进程管道喂给 mysql stdin（数组式进程管道，不经 shell）；
     * 退出码非零抛出并携带 stderr 尾部（可操作报错，不再「恢复成功」假成功）；恢复后
     * 回读 information_schema 表数作为可核验读数。</p>
     *
     * @param fileName 备份目录内的文件名（沿用原路径防穿越五道校验）
     * @param targetDb 恢复目标库；null/空白 = 当前应用库
     * @return 可核验读数：targetDb / tableCount / durationMs / backupFile
     */
    public Map<String, Object> restoreBackup(String fileName, String targetDb) {
        // ---- 路径遍历防护（原五道校验原样保留）----
        if (fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException("非法文件名：禁止路径分隔符");
        }
        if (fileName.contains("..")) {
            throw new BusinessException("非法文件名：禁止父目录引用");
        }
        if (new File(fileName).isAbsolute()) {
            throw new BusinessException("非法文件名：禁止绝对路径");
        }
        Path backupDir = Paths.get(BACKUP_DIR).toAbsolutePath().normalize();
        Path filePath = backupDir.resolve(fileName).normalize();
        if (!filePath.startsWith(backupDir)) {
            throw new BusinessException("非法文件路径：文件不在备份目录内");
        }
        if (!Files.exists(filePath)) {
            throw new BusinessException("备份文件不存在");
        }

        // ---- 目标库解析与隔离护栏（先于凭据解析：安全护栏 fail-fast，与凭据无关）----
        String resolvedDb;
        if (targetDb == null || targetDb.isBlank()) {
            resolvedDb = null; // 由 resolveConnection 的结果补全
        } else {
            boolean isolated = RESTORE_ISOLATION_PREFIXES.stream().anyMatch(targetDb::startsWith);
            if (!isolated) {
                throw new BusinessException("恢复目标库须带 aicourse_restore_/restore_ 前缀"
                        + "（隔离演练口径，与 scripts/restore-mysql.sh 一致）；省略参数则恢复到当前应用库");
            }
            resolvedDb = targetDb;
        }
        ConnectionInfo conn = resolveConnection();
        if (resolvedDb == null) {
            resolvedDb = conn.database();
        }

        boolean gzipped = fileName.endsWith(".gz");
        long started = System.currentTimeMillis();
        File tempConfig = null;
        try {
            tempConfig = writeTempCnf(conn);

            // 先建隔离目标库（当前应用库由备份文件内的 CREATE TABLE 自建；隔离库需先 CREATE DATABASE）
            if (!resolvedDb.equals(conn.database())) {
                execMysql(tempConfig, "CREATE DATABASE IF NOT EXISTS `" + resolvedDb
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
            }

            List<String> mysqlCmd = List.of(
                    "mysql",
                    "--defaults-extra-file=" + tempConfig.getAbsolutePath(),
                    resolvedDb);

            Process mysqlProc;
            Process producer;
            Thread pump = null;
            if (gzipped) {
                // .gz：gunzip 子进程管道喂 mysql stdin；泵线程并发（stderr 由下方同步消费，
                // 不写满缓冲则无死锁）。数组式进程管道，不经 shell、无注入面。
                Process gunzip = new ProcessBuilder("gunzip", "-c", filePath.toAbsolutePath().toString()).start();
                mysqlProc = new ProcessBuilder(mysqlCmd).start();
                producer = gunzip;
                pump = new Thread(() -> {
                    try (var out = mysqlProc.getOutputStream()) {
                        gunzip.getInputStream().transferTo(out);
                    } catch (IOException ignored) {
                        // mysql 提前退出导致管道断开：退出码检查兜底报错
                    }
                });
                pump.start();
            } else {
                // 纯 SQL：直接以备份文件作为 mysql stdin
                mysqlProc = new ProcessBuilder(mysqlCmd)
                        .redirectInput(filePath.toFile())
                        .start();
                producer = null;
            }

            String stderr;
            try (BufferedReader err = new BufferedReader(
                    new InputStreamReader(mysqlProc.getErrorStream(), StandardCharsets.UTF_8))) {
                stderr = err.lines().reduce((a, b) -> a + "\n" + b).orElse("");
            }

            boolean finished = mysqlProc.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                mysqlProc.destroyForcibly();
                throw new BusinessException("恢复超时（mysql 10 分钟内未完成），已中止");
            }
            int exitCode = mysqlProc.exitValue();
            if (producer != null) {
                try {
                    if (!producer.waitFor(1, TimeUnit.MINUTES)) {
                        producer.destroyForcibly();
                    }
                } catch (InterruptedException ie) {
                    producer.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
            if (exitCode != 0) {
                String tail = stderr.length() > 400 ? "…" + stderr.substring(stderr.length() - 400) : stderr;
                throw new BusinessException("恢复失败（mysql 退出码 " + exitCode + "）："
                        + (tail.isBlank() ? "无 stderr 输出" : tail));
            }
            if (pump != null) {
                pump.join(60000);
            }

            long tableCount = countTables(tempConfig, resolvedDb);
            long durationMs = System.currentTimeMillis() - started;
            log.info("数据库恢复完成: backup={}, targetDb={}, tableCount={}, durationMs={}",
                    fileName, resolvedDb, tableCount, durationMs);
            Map<String, Object> report = new java.util.LinkedHashMap<>();
            report.put("backupFile", fileName);
            report.put("targetDb", resolvedDb);
            report.put("tableCount", tableCount);
            report.put("durationMs", durationMs);
            return report;
        } catch (IOException e) {
            throw new BusinessException("恢复执行失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("恢复被中断，已中止");
        } finally {
            if (tempConfig != null && tempConfig.exists()) {
                tempConfig.delete();
            }
        }
    }

    /** 执行单条管理 SQL（建库等），非零退出即抛。 */
    private void execMysql(File tempConfig, String sql) {
        try {
            Process p = new ProcessBuilder("mysql",
                    "--defaults-extra-file=" + tempConfig.getAbsolutePath(),
                    "-e", sql).redirectErrorStream(true).start();
            boolean done = p.waitFor(1, TimeUnit.MINUTES);
            if (!done) {
                p.destroyForcibly();
                throw new BusinessException("建库 SQL 超时，已中止恢复");
            }
            if (p.exitValue() != 0) {
                StringBuilder out = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        out.append(line).append("\n");
                    }
                }
                throw new BusinessException("建库失败：" + out);
            }
        } catch (IOException e) {
            throw new BusinessException("建库执行失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("建库被中断");
        }
    }

    /** 回读 information_schema 表数（恢复可核验读数）。 */
    private long countTables(File tempConfig, String db) {
        try {
            Process p = new ProcessBuilder("mysql",
                    "--defaults-extra-file=" + tempConfig.getAbsolutePath(),
                    "-N", "-B", "-e",
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='" + db + "';")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(1, TimeUnit.MINUTES);
            if (!done) {
                p.destroyForcibly();
                return -1;
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = r.readLine();
                return line != null ? Long.parseLong(line.trim()) : -1;
            }
        } catch (Exception e) {
            log.warn("恢复后表数回读失败（不影响恢复本身）: {}", e.getMessage());
            return -1;
        }
    }
}
