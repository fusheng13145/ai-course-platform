package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.FileVirusScanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 上传文件病毒扫描实现。
 *
 * <p>提供三种落地姿态：</p>
 * <ul>
 *   <li>{@code windows-defender}：调用 Windows Defender 的 {@code MpCmdRun.exe}
 *       （本机可用的真实引擎；exit 0=干净，2=发现威胁）；</li>
 *   <li>{@code clamav}：通过 ClamAV daemon 的 INSTREAM 协议扫描，不依赖后端容器内
 *       安装 clamdscan，适合 Linux 部署；</li>
 *   <li>{@code auto}：Windows 选 Defender，其他系统选 ClamAV。</li>
 * </ul>
 *
 * <p>安全语义是 fail-closed：命令缺失、服务不可达、超时、未知退出码一律拒绝上传，
 * 绝不允许“扫描失败但文件照收”。</p>
 *
 * <p>{@code file.scan.enabled=false} 只为自动化测试/CI（无 Defender、无 clamd）保留，
 * 命中时跳过扫描并打 WARN；生产与 compose 一律保持 true。</p>
 */
@Slf4j
@Service
public class FileVirusScanServiceImpl implements FileVirusScanService {

    private static final String PROVIDER_AUTO = "auto";
    private static final String PROVIDER_WINDOWS_DEFENDER = "windows-defender";
    private static final String PROVIDER_CLAMAV = "clamav";

    private static final int CLAMAV_CHUNK_BYTES = 8192;
    private static final int CLAMAV_RESPONSE_MAX_BYTES = 4096;

    @Value("${file.scan.provider:auto}")
    private String provider = PROVIDER_AUTO;

    /** Defender 命令路径；为空时自动探测 Program Files / Platform 目录。 */
    @Value("${file.scan.defender-command:}")
    private String defenderCommand = "";

    @Value("${file.scan.clamav-host:127.0.0.1}")
    private String clamavHost = "127.0.0.1";

    @Value("${file.scan.clamav-port:3310}")
    private int clamavPort = 3310;

    @Value("${file.scan.timeout-seconds:60}")
    private int timeoutSeconds = 60;

    /**
     * 扫描总开关，缺省 true（fail-closed）。
     * <p>只有测试/CI（既无 Defender 也无 clamd）显式置 false；命中时跳过扫描并留 WARN，
     * 不静默改变生产语义。</p>
     */
    @Value("${file.scan.enabled:true}")
    private boolean enabled = true;

    @Override
    public void scanOrThrow(Path file, String originalFilename) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "待扫描文件不存在，已拒绝上传");
        }
        try {
            if (Files.size(file) <= 0) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "空文件无法完成病毒扫描，已拒绝上传");
            }
        } catch (IOException e) {
            throw new BusinessException(ApiCode.INTERNAL_SERVER_ERROR, "待扫描文件读取失败，已拒绝上传", e);
        }

        if (!enabled) {
            log.warn("文件病毒扫描已禁用（file.scan.enabled=false），跳过扫描: file={}", originalFilename);
            return;
        }

        String effectiveProvider = resolveProvider();
        switch (effectiveProvider) {
            case PROVIDER_WINDOWS_DEFENDER -> scanWithWindowsDefender(file, originalFilename);
            case PROVIDER_CLAMAV -> scanWithClamAv(file, originalFilename);
            default -> throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE,
                    "病毒扫描未配置可用的引擎（file.scan.provider=" + effectiveProvider + "），已拒绝上传");
        }
    }

    @Override
    public void scanOrThrow(MultipartFile multipartFile, String originalFilename) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "上传文件为空，无法完成病毒扫描");
        }
        if (!enabled) {
            // 开关关闭时不做临时文件拷贝：测试/CI 里几十 MB 的样例文件没必要落一次盘。
            log.warn("文件病毒扫描已禁用（file.scan.enabled=false），跳过扫描: file={}", originalFilename);
            return;
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("aicourse-virus-scan-", ".upload");
            try (InputStream in = multipartFile.getInputStream()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            scanOrThrow(temp, originalFilename);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ApiCode.INTERNAL_SERVER_ERROR,
                    "病毒扫描临时文件处理失败，已拒绝上传", e);
        } finally {
            deleteQuietly(temp);
        }
    }

    private String resolveProvider() {
        String configured = provider == null ? PROVIDER_AUTO : provider.trim().toLowerCase(Locale.ROOT);
        if (PROVIDER_AUTO.equals(configured) || configured.isEmpty()) {
            return isWindows() ? PROVIDER_WINDOWS_DEFENDER : PROVIDER_CLAMAV;
        }
        return configured;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    // ==================== Windows Defender ====================

    private void scanWithWindowsDefender(Path file, String originalFilename) {
        Path command = resolveDefenderCommand();
        if (command == null) {
            throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE,
                    "病毒扫描不可用：未找到 Windows Defender MpCmdRun.exe，已拒绝上传");
        }

        Path outputFile = null;
        try {
            outputFile = Files.createTempFile("aicourse-defender-scan-", ".log");
            int exitCode = runDefenderScan(command, file, outputFile);

            String output = readSmallText(outputFile);
            if (exitCode == 0) {
                log.info("病毒扫描通过: file={}, provider=windows-defender", originalFilename);
                return;
            }
            if (exitCode == 2 || output.toLowerCase(Locale.ROOT).contains("found")
                    && output.toLowerCase(Locale.ROOT).contains("threat")) {
                String threat = firstThreat(output);
                log.warn("病毒扫描命中: file={}, provider=windows-defender, threat={}, exit={}",
                        originalFilename, threat, exitCode);
                throw new BusinessException(ApiCode.BAD_REQUEST,
                        "文件未通过病毒扫描" + (threat.isBlank() ? "" : "（" + threat + "）") + "，已拒绝上传");
            }
            log.error("病毒扫描引擎异常: file={}, provider=windows-defender, exit={}, output={}",
                    originalFilename, exitCode, tail(output));
            throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE,
                    "病毒扫描引擎异常（退出码 " + exitCode + "），已拒绝上传");
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("Windows Defender 扫描执行失败: file={}, {}", originalFilename, e.getMessage());
            throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE,
                    "病毒扫描不可用：Windows Defender 执行失败，已拒绝上传", e);
        } finally {
            deleteQuietly(outputFile);
        }
    }

    /**
     * 执行 Defender 扫描命令，返回退出码。
     * <p>单列为 protected 方法只为单测可替换进程执行；生产路径仍是真实 MpCmdRun.exe。</p>
     */
    protected int runDefenderScan(Path command, Path file, Path outputFile)
            throws IOException {
        ProcessBuilder pb = new ProcessBuilder(List.of(
                command.toAbsolutePath().toString(),
                "-Scan",
                "-ScanType", "3",
                "-File", file.toAbsolutePath().toString(),
                "-DisableRemediation"))
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile());
        Process process = pb.start();
        try {
            if (!process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE,
                        "病毒扫描超时（" + timeoutSeconds + " 秒），已拒绝上传");
            }
            return process.exitValue();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE,
                    "病毒扫描被中断，已拒绝上传", e);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /** 解析 Defender 命令：显式配置优先，其次 Program Files，最后 Platform 下最新版本目录。 */
    private Path resolveDefenderCommand() {
        if (defenderCommand != null && !defenderCommand.isBlank()) {
            Path configured = Paths.get(defenderCommand.trim());
            return Files.isRegularFile(configured) ? configured : null;
        }
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null) {
            Path standard = Paths.get(programFiles, "Windows Defender", "MpCmdRun.exe");
            if (Files.isRegularFile(standard)) {
                return standard;
            }
        }
        String programData = System.getenv("ProgramData");
        if (programData == null) {
            return null;
        }
        Path platformRoot = Paths.get(programData, "Microsoft", "Windows Defender", "Platform");
        if (!Files.isDirectory(platformRoot)) {
            return null;
        }
        try (Stream<Path> children = Files.list(platformRoot)) {
            return children.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .map(p -> p.resolve("MpCmdRun.exe"))
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Windows Defender Platform 目录读取失败: {}", e.getMessage());
            return null;
        }
    }

    private static String firstThreat(String output) {
        return output.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("Threat") && line.contains(":"))
                .map(line -> line.substring(line.indexOf(':') + 1).trim())
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse("");
    }

    // ==================== ClamAV daemon INSTREAM ====================

    private void scanWithClamAv(Path file, String originalFilename) {
        int timeoutMs = Math.max(1000, timeoutSeconds * 1000);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(clamavHost, clamavPort), Math.min(5000, timeoutMs));
            socket.setSoTimeout(timeoutMs);

            try (OutputStream rawOut = socket.getOutputStream();
                 InputStream rawIn = socket.getInputStream();
                 DataOutputStream out = new DataOutputStream(rawOut);
                 InputStream fileIn = Files.newInputStream(file)) {
                out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                byte[] buffer = new byte[CLAMAV_CHUNK_BYTES];
                int read;
                while ((read = fileIn.read(buffer)) != -1) {
                    out.writeInt(read);
                    out.write(buffer, 0, read);
                }
                out.writeInt(0);
                out.flush();

                String response = readClamAvResponse(rawIn);
                if (response.contains("FOUND")) {
                    String signature = response.substring(0, response.indexOf("FOUND")).trim();
                    log.warn("病毒扫描命中: file={}, provider=clamav, signature={}", originalFilename, signature);
                    throw new BusinessException(ApiCode.BAD_REQUEST,
                            "文件未通过病毒扫描（" + signature + "），已拒绝上传");
                }
                if (response.contains("OK")) {
                    log.info("病毒扫描通过: file={}, provider=clamav", originalFilename);
                    return;
                }
                log.error("ClamAV 返回未知结果: file={}, response={}", originalFilename, response);
                throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE,
                        "病毒扫描引擎返回未知结果，已拒绝上传");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("ClamAV 扫描不可达: host={}:{}, file={}, {}",
                    clamavHost, clamavPort, originalFilename, e.getMessage());
            throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE,
                    "病毒扫描服务不可用（ClamAV " + clamavHost + ":" + clamavPort + "），已拒绝上传", e);
        }
    }

    /** 读取 clamd 响应：以 NUL 或换行结束，兼容 `stream: OK` / `... FOUND`。 */
    private static String readClamAvResponse(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == 0 || b == '\n' || b == '\r') {
                break;
            }
            out.write(b);
            if (out.size() >= CLAMAV_RESPONSE_MAX_BYTES) {
                break;
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    // ==================== 公共工具 ====================

    private static String readSmallText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String tail(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= 400 ? normalized : "…" + normalized.substring(normalized.length() - 400);
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("病毒扫描临时文件清理失败: {}", file);
        }
    }
}
