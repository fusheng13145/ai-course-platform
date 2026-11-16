package com.aicourse.service.component.sys;

import com.aicourse.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SysBackupComponent 单元测试（纯 Mockito + 真实备份目录临时文件）。
 * <p>覆盖备份创建（凭据缺失抛异常/元数据兜底/mysqldump 回退）、备份列表、恢复校验（路径防穿越）。</p>
 */
@ExtendWith(MockitoExtension.class)
class SysBackupComponentTest {

    @Mock
    private DataSource dataSource;
    @Mock
    private Environment environment;

    private SysBackupComponent newComponent() {
        return new SysBackupComponent(dataSource, environment);
    }

    // ===================== createBackup =====================
    @Test
    @DisplayName("createBackup 缺少密码抛 BusinessException")
    void createBackup_missingPassword() throws Exception {
        when(environment.getProperty(anyString())).thenReturn(null);
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(meta.getURL()).thenReturn("jdbc:mysql://localhost:3306/ai_course_platform");
        when(meta.getUserName()).thenReturn("root");
        when(conn.getMetaData()).thenReturn(meta);
        when(dataSource.getConnection()).thenReturn(conn);

        assertThrows(BusinessException.class, () -> newComponent().createBackup());
    }

    @Test
    @DisplayName("createBackup 配置缺失抛异常（含提示）")
    void createBackup_missingConfig() {
        when(environment.getProperty("spring.datasource.url")).thenReturn(null);
        when(environment.getProperty("spring.datasource.username")).thenReturn(null);
        when(environment.getProperty("spring.datasource.password")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> newComponent().createBackup());
        assertTrue(ex.getMessage().contains("需要配置"));
    }

    @Test
    @DisplayName("createBackup mysqldump 失败时抛业务异常（不再假备份，#28 修复）")
    void createBackup_fallback() {
        when(environment.getProperty("spring.datasource.url")).thenReturn("jdbc:mysql://localhost:3306/db");
        when(environment.getProperty("spring.datasource.username")).thenReturn("root");
        when(environment.getProperty("spring.datasource.password")).thenReturn("pass");

        // 修复 #28：mysqldump 退出码非 0（如本测试环境的退出码=2）必须如实抛出，
        // 不能再以「回退文件名」掩盖备份失败（假备份 = 静默数据丢失风险）。
        BusinessException ex = assertThrows(BusinessException.class, () -> newComponent().createBackup());
        assertTrue(ex.getMessage().contains("备份失败") || ex.getMessage().contains("mysqldump"));
    }

    @Test
    @DisplayName("createBackup 用户名从元数据获取后 mysqldump 失败仍如实抛异常（#28 修复）")
    void createBackup_metadataFallback() throws Exception {
        when(environment.getProperty("spring.datasource.url")).thenReturn("jdbc:mysql://dbhost:3307/mydb");
        when(environment.getProperty("spring.datasource.username")).thenReturn(null);
        when(environment.getProperty("spring.datasource.password")).thenReturn("pw");
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(meta.getUserName()).thenReturn("root@dbhost");
        when(conn.getMetaData()).thenReturn(meta);
        when(dataSource.getConnection()).thenReturn(conn);

        // 无论用户名取自配置还是元数据，mysqldump 失败都应如实抛出，而非伪造备份文件。
        assertThrows(BusinessException.class, () -> newComponent().createBackup());
    }

    // ===================== getBackups =====================
    @Test
    @DisplayName("getBackups 返回文件信息")
    void getBackups() throws IOException {
        Files.createDirectories(Path.of("backups"));
        Path f = Path.of("backups", "sysbackup_tmp.sql");
        Files.writeString(f, "select 1;");
        try {
            List<Map<String, Object>> r = newComponent().getBackups();
            assertTrue(r.stream().anyMatch(m -> "sysbackup_tmp.sql".equals(m.get("fileName"))));
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    @DisplayName("目录不存在时 getBackups 返回空")
    void getBackups_empty() {
        assertTrue(newComponent().getBackups().isEmpty());
    }

    // ===================== restoreBackup =====================
    @Test
    @DisplayName("restoreBackup 各类非法文件名抛异常")
    void restoreBackup_illegal() {
        SysBackupComponent c = newComponent();
        assertThrows(BusinessException.class, () -> c.restoreBackup("a/b.sql", null, false));
        assertThrows(BusinessException.class, () -> c.restoreBackup("a\\b.sql", null, false));
        assertThrows(BusinessException.class, () -> c.restoreBackup("..\\x.sql", null, false));
        assertThrows(BusinessException.class, () -> c.restoreBackup("/etc/passwd", null, false));
        assertThrows(BusinessException.class, () -> c.restoreBackup("../escape.sql", null, false));
    }

    @Test
    @DisplayName("restoreBackup 文件不存在抛异常；非隔离目标库拒绝（P0-2B：真实执行由恢复演练取证）")
    void restoreBackup_exists() throws IOException {
        Files.createDirectories(Path.of("backups"));
        Path f = Path.of("backups", "valid.sql");
        Files.writeString(f, "x");
        try {
            stubDatasourceForRestore();
            SysBackupComponent c = newComponent();
            assertThrows(BusinessException.class, () -> c.restoreBackup("missing.sql", null, false));
            // P0-2B 后 valid.sql 会真实执行 mysql——单元环境无凭据/无 mysql 客户端，
            // 改为钉隔离前缀护栏（演练执行取证走 scripts/restore-mysql.sh，PRD §13 外部项）
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> c.restoreBackup("valid.sql", "not_isolated_db", false));
            assertTrue(ex.getMessage().contains("前缀"), "应拒绝非隔离目标库，实际=" + ex.getMessage());
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    @DisplayName("restoreBackup 缺省 targetDb 直接拒绝，不再隐式回落到当前应用库")
    void restoreBackup_missingTargetDbRejected() throws IOException {
        Files.createDirectories(Path.of("backups"));
        Path f = Path.of("backups", "valid2.sql");
        Files.writeString(f, "x");
        try {
            // targetDb 校验先于凭据解析，故此处不打桩 datasource（保持 Mockito 严格桩检查通过）
            SysBackupComponent c = newComponent();
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> c.restoreBackup("valid2.sql", null, false));
            assertTrue(ex.getMessage().contains("必须显式指定"), "应要求显式目标库，实际=" + ex.getMessage());
            BusinessException blank = assertThrows(BusinessException.class,
                    () -> c.restoreBackup("valid2.sql", "  ", false));
            assertTrue(blank.getMessage().contains("必须显式指定"), "空白目标库同样拒绝，实际=" + blank.getMessage());
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    @DisplayName("restoreBackup 目标库为当前应用库时必须显式 confirmCurrentDb=true")
    void restoreBackup_currentDbRequiresConfirm() throws IOException {
        Files.createDirectories(Path.of("backups"));
        Path f = Path.of("backups", "valid3.sql");
        Files.writeString(f, "x");
        try {
            stubDatasourceForRestore();
            SysBackupComponent c = newComponent();
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> c.restoreBackup("valid3.sql", "ai_course_platform", false));
            assertTrue(ex.getMessage().contains("confirmCurrentDb=true"),
                    "覆盖当前应用库须显式确认，实际=" + ex.getMessage());
        } finally {
            Files.deleteIfExists(f);
        }
    }

    /** 恢复路径的凭据解析桩：datasource 元数据给出当前库名 ai_course_platform。 */
    private void stubDatasourceForRestore() throws IOException {
        when(environment.getProperty("spring.datasource.url"))
                .thenReturn("jdbc:mysql://localhost:3306/ai_course_platform");
        when(environment.getProperty("spring.datasource.username")).thenReturn("root");
        when(environment.getProperty("spring.datasource.password")).thenReturn("secret");
    }
}
