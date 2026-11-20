package com.aicourse.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UploadMagic 覆盖率补测（I-04 第七批）：容器魔数识别、MIME/扩展名一致性、头部读取边界。
 */
class UploadMagicCoverageTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    @Test
    @DisplayName("detect：识别全部支持容器；过短/未知返回 null")
    void detect_allMagicTypes() {
        assertEquals("pdf", UploadMagic.detect(concat(bytes("%PDF"), bytes("-1.7"))));
        assertEquals("png", UploadMagic.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        assertEquals("jpeg", UploadMagic.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
        assertEquals("gif", UploadMagic.detect(bytes("GIF89a")));
        assertEquals("zip", UploadMagic.detect(new byte[]{'P', 'K', 0x03, 0x04}));
        assertEquals("ole2", UploadMagic.detect(new byte[]{
                (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}));
        assertEquals("rar", UploadMagic.detect(new byte[]{'R', 'a', 'r', '!', 0x1A, 0x07}));
        assertEquals("7z", UploadMagic.detect(new byte[]{'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C}));
        assertEquals("bmp", UploadMagic.detect(bytes("BM")));
        assertEquals("webp", UploadMagic.detect(concat(bytes("RIFF"), new byte[]{0, 0, 0, 0}, bytes("WEBP"))));
        assertEquals("avi", UploadMagic.detect(concat(bytes("RIFF"), new byte[]{0, 0, 0, 0}, bytes("AVI "))));
        assertEquals("mp4", UploadMagic.detect(concat(new byte[]{0, 0, 0, 0}, bytes("ftyp"), bytes("isom"))));
        assertEquals("mp3", UploadMagic.detect(bytes("ID3")));
        assertEquals("mp3", UploadMagic.detect(new byte[]{(byte) 0xFF, (byte) 0xE0}));
        assertEquals("webm", UploadMagic.detect(new byte[]{
                (byte) 0x1A, 'E', (byte) 0xDF, (byte) 0xA3}));

        assertNull(UploadMagic.detect(null));
        assertNull(UploadMagic.detect(new byte[]{'P'}));
        assertNull(UploadMagic.detect(bytes("PK")));
        assertNull(UploadMagic.detect(bytes("hello")));
        assertNull(UploadMagic.detect(concat(bytes("RIFF"), new byte[]{0, 0, 0, 0}, bytes("WAVE"))));
    }

    @Test
    @DisplayName("readHeader：null 文件、空输入流返回空数组；正常文件最多读 16 字节")
    void readHeader_boundaries() throws IOException {
        assertArrayEquals(new byte[0], UploadMagic.readHeader((MultipartFile) null));

        MultipartFile nullStream = mock(MultipartFile.class);
        when(nullStream.getInputStream()).thenReturn(null);
        assertArrayEquals(new byte[0], UploadMagic.readHeader(nullStream));

        MultipartFile file = mock(MultipartFile.class);
        byte[] content = new byte[32];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) i;
        }
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        byte[] header = UploadMagic.readHeader(file);
        assertEquals(UploadMagic.HEADER_BYTES, header.length);
        assertEquals(0, header[0]);
        assertEquals(15, header[15]);

        assertArrayEquals(new byte[0], UploadMagic.readHeader((InputStream) null));
        assertArrayEquals(new byte[]{(byte) 0x1A, 'E', (byte) 0xDF, (byte) 0xA3},
                UploadMagic.readHeader(new ByteArrayInputStream(new byte[]{
                        (byte) 0x1A, 'E', (byte) 0xDF, (byte) 0xA3})));
    }

    @Test
    @DisplayName("readHeader：打开输入流异常向外抛出")
    void readHeader_throwsIOException() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("io"));

        assertThrows(IOException.class, () -> UploadMagic.readHeader(file));
    }

    @Test
    @DisplayName("matchesMime：空值、精确匹配与容器类文档双容器放行")
    void matchesMime_allCases() {
        assertFalse(UploadMagic.matchesMime(null, "pdf"));
        assertFalse(UploadMagic.matchesMime("application/pdf", null));

        assertTrue(UploadMagic.matchesMime("application/pdf", "pdf"));
        assertFalse(UploadMagic.matchesMime("application/pdf", "png"));
        assertTrue(UploadMagic.matchesMime("image/png", "png"));
        assertTrue(UploadMagic.matchesMime("image/jpeg", "jpeg"));
        assertTrue(UploadMagic.matchesMime("image/gif", "gif"));
        assertTrue(UploadMagic.matchesMime("video/mp4", "mp4"));
        assertTrue(UploadMagic.matchesMime("video/webm", "webm"));
        assertTrue(UploadMagic.matchesMime("audio/mpeg", "mp3"));
        assertTrue(UploadMagic.matchesMime("application/msword", "ole2"));
        assertTrue(UploadMagic.matchesMime("application/vnd.ms-excel", "zip"));
        assertTrue(UploadMagic.matchesMime(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "zip"));
        assertTrue(UploadMagic.matchesMime(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "ole2"));
        assertFalse(UploadMagic.matchesMime("text/plain", "pdf"));
    }

    @Test
    @DisplayName("matchesExtension：文本类无限制、容器类逐一匹配、未知/空返回 false")
    void matchesExtension_allCases() {
        assertFalse(UploadMagic.matchesExtension(null, "pdf"));

        assertTrue(UploadMagic.matchesExtension("txt", null));
        assertTrue(UploadMagic.matchesExtension("csv", null));
        assertTrue(UploadMagic.matchesExtension("md", null));
        assertTrue(UploadMagic.matchesExtension("json", null));
        assertTrue(UploadMagic.matchesExtension("xml", null));
        assertTrue(UploadMagic.matchesExtension("pdf", "pdf"));
        assertTrue(UploadMagic.matchesExtension(".PNG", "png"));
        assertTrue(UploadMagic.matchesExtension("jpg", "jpeg"));
        assertTrue(UploadMagic.matchesExtension("jpeg", "jpeg"));
        assertTrue(UploadMagic.matchesExtension("gif", "gif"));
        assertTrue(UploadMagic.matchesExtension("bmp", "bmp"));
        assertTrue(UploadMagic.matchesExtension("webp", "webp"));
        assertTrue(UploadMagic.matchesExtension("webm", "webm"));
        assertTrue(UploadMagic.matchesExtension("doc", "ole2"));
        assertTrue(UploadMagic.matchesExtension("xls", "ole2"));
        assertTrue(UploadMagic.matchesExtension("ppt", "ole2"));
        assertTrue(UploadMagic.matchesExtension("docx", "zip"));
        assertTrue(UploadMagic.matchesExtension("xlsx", "zip"));
        assertTrue(UploadMagic.matchesExtension("pptx", "zip"));
        assertTrue(UploadMagic.matchesExtension("zip", "zip"));
        assertTrue(UploadMagic.matchesExtension("rar", "rar"));
        assertTrue(UploadMagic.matchesExtension("7z", "7z"));
        assertTrue(UploadMagic.matchesExtension("mp4", "mp4"));
        assertTrue(UploadMagic.matchesExtension("mov", "mp4"));
        assertTrue(UploadMagic.matchesExtension("avi", "avi"));
        assertTrue(UploadMagic.matchesExtension("mp3", "mp3"));
        assertFalse(UploadMagic.matchesExtension("exe", "zip"));
        assertFalse(UploadMagic.matchesExtension("pdf", "png"));
    }
}
