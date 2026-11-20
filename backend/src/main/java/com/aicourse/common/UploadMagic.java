package com.aicourse.common;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 上传文件魔数校验的唯一口径。
 *
 * <p>客户端声明的 Content-Type 与文件名都可伪造，上传入口只能读文件头若干字节判断真实容器类型。
 * 本类只读取头部，不把整文件读入内存；调用方随后应重新打开输入流交给存储层。</p>
 */
public final class UploadMagic {

    /** 读取头部字节数：覆盖 ftyp/RIFF 等需要读到第 12 字节的格式。 */
    public static final int HEADER_BYTES = 16;

    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF = {'G', 'I', 'F', '8'};
    private static final byte[] ZIP = {'P', 'K', 0x03, 0x04};
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] RAR = {'R', 'a', 'r', '!', 0x1A, 0x07};
    private static final byte[] SEVEN_Z = {'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
    private static final byte[] BMP = {'B', 'M'};
    private static final byte[] EBML = {(byte) 0x1A, 'E', (byte) 0xDF, (byte) 0xA3};

    private UploadMagic() {
    }

    /**
     * 只读 multipart 文件头部，最多 {@link #HEADER_BYTES} 字节。
     */
    public static byte[] readHeader(MultipartFile file) throws IOException {
        if (file == null) {
            return new byte[0];
        }
        try (InputStream in = file.getInputStream()) {
            if (in == null) {
                return new byte[0];
            }
            return readHeader(in);
        }
    }

    /**
     * 只读输入流头部，最多 {@link #HEADER_BYTES} 字节；流由调用方负责关闭。
     */
    public static byte[] readHeader(InputStream in) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        return in.readNBytes(HEADER_BYTES);
    }

    /**
     * 识别文件头对应的容器类型。
     *
     * @return pdf/png/jpeg/gif/zip/ole2/mp4/webm/webp/avi/bmp/mp3/rar/7z；无法识别返回 null
     */
    public static String detect(byte[] header) {
        if (header == null || header.length < 2) {
            return null;
        }
        if (startsWith(header, EBML)) {
            return "webm";
        }
        if (startsWith(header, PDF)) {
            return "pdf";
        }
        if (startsWith(header, PNG)) {
            return "png";
        }
        if (startsWith(header, JPEG)) {
            return "jpeg";
        }
        if (startsWith(header, GIF)) {
            return "gif";
        }
        if (startsWith(header, ZIP)) {
            return "zip";
        }
        if (startsWith(header, OLE2)) {
            return "ole2";
        }
        if (startsWith(header, RAR)) {
            return "rar";
        }
        if (startsWith(header, SEVEN_Z)) {
            return "7z";
        }
        if (startsWith(header, BMP)) {
            return "bmp";
        }
        if (header.length >= 12 && startsWith(header, new byte[]{'R', 'I', 'F', 'F'})) {
            String tag = ascii(header, 8, 4);
            if ("WEBP".equals(tag)) {
                return "webp";
            }
            if ("AVI ".equals(tag)) {
                return "avi";
            }
        }
        if (header.length >= 8 && header[4] == 'f' && header[5] == 't'
                && header[6] == 'y' && header[7] == 'p') {
            return "mp4";
        }
        if (startsWith(header, new byte[]{'I', 'D', '3'})) {
            return "mp3";
        }
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0) {
            return "mp3";
        }
        return null;
    }

    /**
     * 声明 MIME 与魔数是否一致。
     *
     * <p>MP3 帧头不统一，仅在声明 {@code audio/mpeg} 时放行；
     * docx/xlsx/pptx 是 ZIP 容器，doc/xls 是 OLE2 复合文档。</p>
     */
    public static boolean matchesMime(String contentType, String magic) {
        if (magic == null || contentType == null) {
            return false;
        }
        return switch (contentType) {
            case "application/pdf" -> "pdf".equals(magic);
            case "image/png" -> "png".equals(magic);
            case "image/jpeg" -> "jpeg".equals(magic);
            case "image/gif" -> "gif".equals(magic);
            case "video/mp4" -> "mp4".equals(magic);
            case "video/webm" -> "webm".equals(magic);
            case "audio/mpeg" -> true;
            case "application/msword", "application/vnd.ms-excel",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                    "zip".equals(magic) || "ole2".equals(magic);
            default -> false;
        };
    }

    /**
     * 扩展名（不含点，小写）与魔数是否一致；文本类扩展名不做容器限制。
     *
     * <p>txt/csv/md/json/xml 没有稳定文件头，只能放行扩展名与编码边界，不能声称验证了“真身”。</p>
     */
    public static boolean matchesExtension(String extension, String magic) {
        if (extension == null) {
            return false;
        }
        String ext = extension.startsWith(".") ? extension.substring(1) : extension;
        ext = ext.toLowerCase(java.util.Locale.ROOT);
        return switch (ext) {
            case "txt", "csv", "md", "json", "xml" -> true;
            case "pdf" -> "pdf".equals(magic);
            case "png" -> "png".equals(magic);
            case "jpg", "jpeg" -> "jpeg".equals(magic);
            case "gif" -> "gif".equals(magic);
            case "bmp" -> "bmp".equals(magic);
            case "webp" -> "webp".equals(magic);
            case "webm" -> "webm".equals(magic);
            case "doc", "xls", "ppt" -> "ole2".equals(magic);
            case "docx", "xlsx", "pptx" -> "zip".equals(magic);
            case "zip" -> "zip".equals(magic);
            case "rar" -> "rar".equals(magic);
            case "7z" -> "7z".equals(magic);
            case "mp4", "mov" -> "mp4".equals(magic);
            case "avi" -> "avi".equals(magic);
            case "mp3" -> "mp3".equals(magic);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String ascii(byte[] data, int offset, int length) {
        if (data.length < offset + length) {
            return "";
        }
        return new String(data, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
