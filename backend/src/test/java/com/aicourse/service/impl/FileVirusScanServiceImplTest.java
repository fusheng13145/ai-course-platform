package com.aicourse.service.impl;

import com.aicourse.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 病毒扫描实现单测：覆盖 Defender 命令结果映射、ClamAV INSTREAM 协议、
 * fail-closed（命令缺失/服务不可达/未知结果）与 Multipart 临时文件回收。
 */
class FileVirusScanServiceImplTest {

    @TempDir
    Path tempDir;

    private Path file(String content) throws IOException {
        Path p = tempDir.resolve("sample.bin");
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    private String javaExecutable() {
        String exe = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe" : "java";
        return Paths.get(System.getProperty("java.home"), "bin", exe).toString();
    }

    private FileVirusScanServiceImpl defenderWith(int exitCode, String output) {
        return new FileVirusScanServiceImpl() {
            @Override
            protected int runDefenderScan(Path command, Path file, Path outputFile) throws IOException {
                Files.writeString(outputFile, output, StandardCharsets.UTF_8);
                return exitCode;
            }
        };
    }

    @Test
    @DisplayName("Defender exit=0：扫描通过")
    void defender_clean() throws IOException {
        FileVirusScanServiceImpl service = defenderWith(0, "Scanning ... found no threats.");
        ReflectionTestUtils.setField(service, "provider", "windows-defender");
        ReflectionTestUtils.setField(service, "defenderCommand", javaExecutable());

        assertDoesNotThrow(() -> service.scanOrThrow(file("hello"), "hello.txt"));
    }

    @Test
    @DisplayName("Defender exit=2：识别威胁名并拒绝（400）")
    void defender_infected() throws IOException {
        FileVirusScanServiceImpl service = defenderWith(2,
                "Threat                  : Eicar-Test-Signature\n");
        ReflectionTestUtils.setField(service, "provider", "windows-defender");
        ReflectionTestUtils.setField(service, "defenderCommand", javaExecutable());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.scanOrThrow(file("bad"), "bad.txt"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("Eicar-Test-Signature"), "应回传威胁名，实际=" + ex.getMessage());
    }

    @Test
    @DisplayName("Defender 未知退出码：fail-closed（503）")
    void defender_unknownExit_failClosed() throws IOException {
        FileVirusScanServiceImpl service = defenderWith(7, "engine crashed");
        ReflectionTestUtils.setField(service, "provider", "windows-defender");
        ReflectionTestUtils.setField(service, "defenderCommand", javaExecutable());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.scanOrThrow(file("x"), "x.txt"));
        assertEquals(503, ex.getCode());
        assertTrue(ex.getMessage().contains("引擎异常"), "实际=" + ex.getMessage());
    }

    @Test
    @DisplayName("Defender 命令缺失：fail-closed（503），不调用进程")
    void defender_commandMissing_failClosed() throws IOException {
        FileVirusScanServiceImpl service = new FileVirusScanServiceImpl();
        ReflectionTestUtils.setField(service, "provider", "windows-defender");
        ReflectionTestUtils.setField(service, "defenderCommand",
                tempDir.resolve("missing-MpCmdRun.exe").toString());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.scanOrThrow(file("x"), "x.txt"));
        assertEquals(503, ex.getCode());
        assertTrue(ex.getMessage().contains("未找到"), "实际=" + ex.getMessage());
    }

    @Test
    @DisplayName("Defender 进程启动异常：fail-closed（503）")
    void defender_processError_failClosed() throws IOException {
        FileVirusScanServiceImpl service = new FileVirusScanServiceImpl() {
            @Override
            protected int runDefenderScan(Path command, Path file, Path outputFile) throws IOException {
                throw new IOException("cannot start");
            }
        };
        ReflectionTestUtils.setField(service, "provider", "windows-defender");
        ReflectionTestUtils.setField(service, "defenderCommand", javaExecutable());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.scanOrThrow(file("x"), "x.txt"));
        assertEquals(503, ex.getCode());
    }

    @Test
    @DisplayName("文件不存在 / 空文件：400")
    void fileBoundary_rejected() throws IOException {
        FileVirusScanServiceImpl service = new FileVirusScanServiceImpl();
        ReflectionTestUtils.setField(service, "provider", "windows-defender");

        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.scanOrThrow(tempDir.resolve("nope.bin"), "nope.bin")).getCode());

        Path empty = tempDir.resolve("empty.bin");
        Files.createFile(empty);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.scanOrThrow(empty, "empty.bin")).getCode());
    }

    @Test
    @DisplayName("未知 provider：fail-closed（503）")
    void unknownProvider_failClosed() throws IOException {
        FileVirusScanServiceImpl service = new FileVirusScanServiceImpl();
        ReflectionTestUtils.setField(service, "provider", "typo-engine");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.scanOrThrow(file("x"), "x.txt"));
        assertEquals(503, ex.getCode());
        assertTrue(ex.getMessage().contains("未配置"), "实际=" + ex.getMessage());
    }

    @Test
    @DisplayName("ClamAV INSTREAM 返回 OK：通过")
    void clamav_clean() throws Exception {
        try (FakeClamAvServer server = new FakeClamAvServer("stream: OK\0")) {
            FileVirusScanServiceImpl service = new FileVirusScanServiceImpl();
            ReflectionTestUtils.setField(service, "provider", "clamav");
            ReflectionTestUtils.setField(service, "clamavHost", "127.0.0.1");
            ReflectionTestUtils.setField(service, "clamavPort", server.port());
            ReflectionTestUtils.setField(service, "timeoutSeconds", 10);

            assertDoesNotThrow(() -> service.scanOrThrow(file("hello"), "hello.txt"));
            server.awaitRequest();
        }
    }

    @Test
    @DisplayName("ClamAV INSTREAM 返回 FOUND：拒绝（400）")
    void clamav_infected() throws Exception {
        try (FakeClamAvServer server = new FakeClamAvServer("stream: Eicar-Test-Signature FOUND\0")) {
            FileVirusScanServiceImpl service = new FileVirusScanServiceImpl();
            ReflectionTestUtils.setField(service, "provider", "clamav");
            ReflectionTestUtils.setField(service, "clamavHost", "127.0.0.1");
            ReflectionTestUtils.setField(service, "clamavPort", server.port());
            ReflectionTestUtils.setField(service, "timeoutSeconds", 10);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.scanOrThrow(file("bad"), "bad.txt"));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("Eicar-Test-Signature"), "实际=" + ex.getMessage());
            server.awaitRequest();
        }
    }

    @Test
    @DisplayName("ClamAV 返回未知结果（非 OK/FOUND）：fail-closed（503）")
    void clamav_unknownResponse_failClosed() throws Exception {
        try (FakeClamAvServer server = new FakeClamAvServer("stream: INSTREAM size limit exceeded. ERROR\0")) {
            FileVirusScanServiceImpl service = new FileVirusScanServiceImpl();
            ReflectionTestUtils.setField(service, "provider", "clamav");
            ReflectionTestUtils.setField(service, "clamavHost", "127.0.0.1");
            ReflectionTestUtils.setField(service, "clamavPort", server.port());
            ReflectionTestUtils.setField(service, "timeoutSeconds", 10);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.scanOrThrow(file("x"), "x.txt"));
            assertEquals(503, ex.getCode());
            assertTrue(ex.getMessage().contains("未知结果"), "实际=" + ex.getMessage());
            server.awaitRequest();
        }
    }

    @Test
    @DisplayName("MultipartFile 读取失败：fail-closed（500）")
    void multipart_readFailure_failClosed() throws IOException {
        MockMultipartFile broken = new MockMultipartFile("file", "a.pdf", "application/pdf",
                new byte[]{1}) {
            @Override
            public java.io.InputStream getInputStream() throws IOException {
                throw new IOException("stream broken");
            }
        };
        FileVirusScanServiceImpl service = new FileVirusScanServiceImpl();
        ReflectionTestUtils.setField(service, "provider", "windows-defender");
        ReflectionTestUtils.setField(service, "defenderCommand", javaExecutable());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.scanOrThrow(broken, "a.pdf"));
        assertEquals(500, ex.getCode());
        assertTrue(ex.getMessage().contains("临时文件"), "实际=" + ex.getMessage());
    }

    @Test
    @DisplayName("ClamAV 连接失败：fail-closed（503）")
    void clamav_unreachable_failClosed() throws IOException {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = probe.getLocalPort();
        }
        FileVirusScanServiceImpl service = new FileVirusScanServiceImpl();
        ReflectionTestUtils.setField(service, "provider", "clamav");
        ReflectionTestUtils.setField(service, "clamavHost", "127.0.0.1");
        ReflectionTestUtils.setField(service, "clamavPort", closedPort);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.scanOrThrow(file("x"), "x.txt"));
        assertEquals(503, ex.getCode());
        assertTrue(ex.getMessage().contains("不可用"), "实际=" + ex.getMessage());
    }

    @Test
    @DisplayName("MultipartFile 扫描：复制临时文件、扫描后删除")
    void multipart_tempFileDeleted() {
        CapturingScanner service = new CapturingScanner();
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        assertDoesNotThrow(() -> service.scanOrThrow(multipart, "a.txt"));
        assertNotNull(service.seen, "应实际进入扫描");
        assertFalse(Files.exists(service.seen), "multipart 扫描的临时文件必须回收");
    }

    @Test
    @DisplayName("file.scan.enabled=false：Path 与 Multipart 两条入口都跳过扫描（测试/CI 兜底）")
    void disabled_skipsScanning() throws IOException {
        FileVirusScanServiceImpl service = new FileVirusScanServiceImpl();
        ReflectionTestUtils.setField(service, "enabled", false);
        // provider 故意设为不存在的引擎：若开关未生效，Path 分支会立刻 503
        ReflectionTestUtils.setField(service, "provider", "no-such-engine");
        Path sample = file("hello");

        assertDoesNotThrow(() -> service.scanOrThrow(sample, "hello.txt"));

        MockMultipartFile multipart = new MockMultipartFile(
                "file", "a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        assertDoesNotThrow(() -> service.scanOrThrow(multipart, "a.txt"));
    }

    private static class CapturingScanner extends FileVirusScanServiceImpl {
        private Path seen;

        @Override
        public void scanOrThrow(Path file, String originalFilename) {
            seen = file;
            assertTrue(Files.exists(file));
        }
    }

    /** 极简 clamd INSTREAM 服务端：读协议后回写固定响应。 */
    private static class FakeClamAvServer implements AutoCloseable {
        private final ServerSocket server;
        private final String response;
        private final Thread thread;
        private volatile Throwable failure;

        FakeClamAvServer(String response) throws IOException {
            this.server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            this.response = response;
            this.thread = new Thread(this::serve, "fake-clamav");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        private void serve() {
            try (Socket socket = server.accept();
                 InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {
                byte[] magic = in.readNBytes(10);
                assertEquals("zINSTREAM\0", new String(magic, StandardCharsets.US_ASCII));
                DataInputStream data = new DataInputStream(in);
                int len;
                while ((len = data.readInt()) > 0) {
                    in.readNBytes(len);
                }
                out.write(response.getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } catch (Throwable t) {
                failure = t;
            }
        }

        void awaitRequest() throws Exception {
            thread.join(5000);
            assertFalse(thread.isAlive(), "fake clamd 未在 5s 内完成请求");
            if (failure != null) {
                throw new AssertionError("fake clamd 请求处理失败", failure);
            }
        }

        @Override
        public void close() throws Exception {
            server.close();
            thread.join(1000);
        }
    }
}
