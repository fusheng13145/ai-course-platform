package com.aicourse.service.component;

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
    @DisplayName("createBackup mysqldump 不可用时回退文件名")
    void createBackup_fallback() {
        when(environment.getProperty("spring.datasource.url")).thenReturn("jdbc:mysql://localhost:3306/db");
        when(environment.getProperty("spring.datasource.username")).thenReturn("root");
        when(environment.getProperty("spring.datasource.password")).thenReturn("pass");

        String r = newComponent().createBackup();
        assertTrue(r.startsWith("backup_") && r.endsWith(".sql"));
    }

    @Test
    @DisplayName("createBackup 用户名缺失时从 DataSource 元数据获取（含 @host 处理）")
    void createBackup_metadataFallback() throws Exception {
        when(environment.getProperty("spring.datasource.url")).thenReturn("jdbc:mysql://dbhost:3307/mydb");
        when(environment.getProperty("spring.datasource.username")).thenReturn(null);
        when(environment.getProperty("spring.datasource.password")).thenReturn("pw");
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(meta.getUserName()).thenReturn("root@dbhost");
        when(conn.getMetaData()).thenReturn(meta);
        when(dataSource.getConnection()).thenReturn(conn);

        String name = newComponent().createBackup();
        assertTrue(name.startsWith("backup_") && name.endsWith(".sql"));
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
        assertThrows(BusinessException.class, () -> c.restoreBackup("a/b.sql"));
        assertThrows(BusinessException.class, () -> c.restoreBackup("a\\b.sql"));
        assertThrows(BusinessException.class, () -> c.restoreBackup("..\\x.sql"));
        assertThrows(BusinessException.class, () -> c.restoreBackup("/etc/passwd"));
        assertThrows(BusinessException.class, () -> c.restoreBackup("../escape.sql"));
    }

    @Test
    @DisplayName("restoreBackup 文件不存在抛异常；存在则通过")
    void restoreBackup_exists() throws IOException {
        Files.createDirectories(Path.of("backups"));
        Path f = Path.of("backups", "valid.sql");
        Files.writeString(f, "x");
        try {
            SysBackupComponent c = newComponent();
            assertThrows(BusinessException.class, () -> c.restoreBackup("missing.sql"));
            assertDoesNotThrow(() -> c.restoreBackup("valid.sql"));
        } finally {
            Files.deleteIfExists(f);
        }
    }
}
