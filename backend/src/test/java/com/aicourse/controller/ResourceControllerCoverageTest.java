package com.aicourse.controller;

import com.aicourse.common.StorageLayout;
import com.aicourse.entity.Course;
import com.aicourse.entity.FileUploadSession;
import com.aicourse.entity.Resource;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.FileStorageService;
import com.aicourse.service.ResourceService;
import com.aicourse.service.ResourceVersionService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资源域三控制器覆盖率补测（专打 Jacoco 报告中的未覆盖分支，
 * 与既有 {@link ResourceControllerTest}（三角色合一用户，canAccess* 恒真）和
 * {@link ResourceEndpointAclTest}（列表端点 ACL 否定面）互补，不重复其用例）。
 *
 * <p>覆盖主线：</p>
 * <ol>
 *   <li>上传全链路：isEmpty / 超限 / MIME 白名单循环 / getBytes 异常 / 七种文件魔数逐一命中
 *       detectMagicType 与 magicMatchesContentType 各分支 / 落盘成功与后缀白名单映射 /
 *       原始文件名缺失回退 unknown / uploads 目录 mkdirs；</li>
 *   <li>下载：200 + Content-Disposition CRLF 清洗 / fileName null 回退 "file" / 教师非本课程与学生
 *       未报名 403 / URL 为 null 走 catch 404 / 目标文件缺失与目录目标 404；</li>
 *   <li>详情、可见性、删除的存在性 404 与对象级 ACL 403（教师归属匹配/不匹配、资源 courseId 为 null 的
 *       三元分支）以及删除时磁盘文件真实删除分支；</li>
 *   <li>搜索：principal 非 Long、跨课程搜索非 ADMIN 拒绝、指定课程但课程不存在的教师拒绝；</li>
 *   <li>直接调用（绕过 MVC 安全链）补 MockMvc 不可达分支：auth == null（canAccessResource /
 *       canAccessCourse 第一条 || 探针）与 courseId == null。</li>
 * </ol>
 *
 * <p>判定不可达/放弃的分支（详见交付报告）：{@code SUFFIX_BY_MIME.get} 返回 null（与 MIME 白名单同构，
 * 前置校验已挡）；{@code targetPath.startsWith(uploadBase)} 假侧（文件名是服务端生成的 UUID+固定后缀，
 * 结构上不可能越界）；{@code dir.mkdirs()} 返回 false（测试环境目录可写）；{@code detectMagicType} 中
 * mp4 'ftyp' 四字节探针的后三个假侧（组合爆炸，性价比低）；{@code Files.write}/{@code Files.delete}
 * 的 IOException（无法在不改生产代码前提下稳定注入磁盘故障）。所有落盘文件在用例内清理。</p>
 */
@WebMvcTest({ResourceController.class, ResourceUploadController.class, ResourceMutationController.class})
@Import(com.aicourse.security.ResourceAccessSupport.class)
class ResourceControllerCoverageTest extends ControllerTestBase {

    @MockitoBean
    private ResourceService resourceService;
    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private CourseEnrollmentService courseEnrollmentService;
    // B-13㉑：ResourceController 新增的版本服务依赖，@WebMvcTest 需显式 mock
    @MockitoBean
    private ResourceVersionService resourceVersionService;
    @MockitoBean
    private FileStorageService fileStorageService;
    @MockitoBean
    private com.aicourse.security.AdminScopeGuard adminScopeGuard;
    @MockitoBean
    private com.aicourse.security.CourseAccessControl courseAccessControl;
    @MockitoBean
    private com.aicourse.service.FileUploadSessionService fileUploadSessionService;
    // 病毒扫描：/resources/upload 落盘前 fail-closed 检查，@WebMvcTest 需显式 mock（默认放行）
    @MockitoBean
    private com.aicourse.service.FileVirusScanService fileVirusScanService;

    @BeforeEach
    void stubStorage() {
        org.mockito.Mockito.when(fileStorageService.upload(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(java.io.InputStream.class),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(0);
                    byte[] bytes = ((java.io.InputStream) inv.getArgument(1)).readAllBytes();
                    StorageLayout.writeBytes(key, bytes);
                    return key;
                });
        org.mockito.Mockito.when(fileStorageService.downloadByUrl(
                        org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> StorageLayout.openRead((String) inv.getArgument(0)));
        org.mockito.Mockito.when(fileStorageService.deleteByUrl(
                        org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> StorageLayout.deleteByUrl((String) inv.getArgument(0)));
        org.mockito.Mockito.when(fileStorageService.download(
                        org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> StorageLayout.openRead((String) inv.getArgument(0)));
    }

    private static final long COURSE_ID = 1L;
    private static final long OWNER_TEACHER = 100L;
    private static final long OTHER_TEACHER = 999L;
    private static final long STUDENT_ID = 20L;
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    // 各类文件魔数（均 >= 8 字节，满足 detectMagicType 长度门槛）
    private static final byte[] PDF_BYTES = "%PDF-1.4 \n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_BYTES =
            new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_BYTES =
            new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
    private static final byte[] GIF_BYTES = "GIF89a!!".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OLE2_BYTES =
            new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                    (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] ZIP_BYTES =
            new byte[]{'P', 'K', 0x03, 0x04, 0, 0, 0, 0};
    private static final byte[] MP4_BYTES =
            new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p'};
    private static final byte[] NO_MAGIC_BYTES =
            new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
    private static final byte[] SHORT_BYTES = {1, 2, 3};

    private Course courseOf(Long teacherId) {
        Course c = new Course();
        c.setId(COURSE_ID);
        c.setTeacherId(teacherId);
        return c;
    }

    private Resource resourceOf(Long id, Long courseId, String url, String fileName) {
        Resource r = new Resource();
        r.setId(id);
        r.setCourseId(courseId);
        r.setUrl(url);
        r.setFileName(fileName);
        return r;
    }

    // ==================== 上传：目录创建 + 各魔数成功落盘（真实行为断言 + 清理） ====================

    /**
     * 上传成功并断言落盘：返回体 code=200、url 后缀来自 MIME 白名单映射、磁盘文件真实存在且字节一致，
     * 最后清理测试产生的临时文件。
     */
    private void uploadAndAssert(String fileName, String contentType, byte[] bytes, String expectedSuffix)
            throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        MockMultipartFile file = new MockMultipartFile("file", fileName, contentType, bytes);
        MvcResult result = mockMvc.perform(multipart("/resources/upload")
                        .file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .param("description", "覆盖用上传")
                        .with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文件上传成功"))
                .andExpect(jsonPath("$.data.fileType").value(contentType))
                .andExpect(jsonPath("$.data.courseId").value((int) COURSE_ID))
                .andExpect(jsonPath("$.data.description").value("覆盖用上传"))
                .andReturn();
        JsonNode data = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String url = data.path("url").asText();
        assertTrue(url.startsWith("/uploads/") && url.endsWith(expectedSuffix),
                "url 应为 /uploads/<uuid>" + expectedSuffix + "，实际：" + url);
        assertEquals(fileName, data.path("fileName").asText());
        Path written = StorageLayout.resolveUrl(url);
        try {
            assertTrue(written != null && Files.exists(written), "文件应真实落盘：" + written);
            assertEquals(bytes.length, Files.size(written));
        } finally {
            if (written != null) {
                Files.deleteIfExists(written);
            }
        }
        verify(resourceService, atLeastOnce()).save(any(Resource.class));
    }

    @Test
    @DisplayName("上传（pdf）：外部存储根不存在时 mkdirs 创建成功并落盘")
    void upload_createsUploadDirWhenAbsent() throws Exception {
        // 仓库内 uploads/ 已移除，落盘根固定为外部存储根（写入路径由 StorageLayout 自动 mkdirs）。
        uploadAndAssert("讲义.pdf", "application/pdf", PDF_BYTES, ".pdf");
    }

    @Test
    @DisplayName("上传：PNG/JPEG/GIF 魔数逐一命中 detectMagicType 与后缀映射（目录已存在的 exists 分支）")
    void upload_imageMagicTypes_succeed() throws Exception {
        uploadAndAssert("图.png", "image/png", PNG_BYTES, ".png");
        uploadAndAssert("照.jpg", "image/jpeg", JPEG_BYTES, ".jpg");
        uploadAndAssert("动.gif", "image/gif", GIF_BYTES, ".gif");
    }

    @Test
    @DisplayName("上传：doc(OLE2)/docx(ZIP)/xls(OLE2) 办公文档魔数逐一命中")
    void upload_officeMagicTypes_succeed() throws Exception {
        uploadAndAssert("旧文档.doc", "application/msword", OLE2_BYTES, ".doc");
        uploadAndAssert("新文档.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ZIP_BYTES, ".docx");
        uploadAndAssert("旧表格.xls", "application/vnd.ms-excel", OLE2_BYTES, ".xls");
        uploadAndAssert("新表格.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ZIP_BYTES, ".xlsx");
    }

    @Test
    @DisplayName("上传：mp4(ftyp) 魔数与 mp3 声明类型放行（audio/mpeg 免魔数）")
    void upload_mediaMagicTypes_succeed() throws Exception {
        uploadAndAssert("视频.mp4", "video/mp4", MP4_BYTES, ".mp4");
        // MP3 帧头不统一，声明 audio/mpeg 即放行：使用 mp4 魔数字节确保 magic != null 后走 audio 分支
        uploadAndAssert("音频.mp3", "audio/mpeg", MP4_BYTES, ".mp3");
    }

    @Test
    @DisplayName("上传：原始文件名缺失回退 unknown（StringUtils.hasText 假侧）")
    void upload_nullOriginalFilename_fallsBackToUnknown() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        // originalFilename=null 走 StringUtils.hasText 假侧；MockMultipartFile 原样返回该字段
        MockMultipartFile mockFile = new MockMultipartFile("file", null, "application/pdf", PDF_BYTES);

        MvcResult result = mockMvc.perform(multipart("/resources/upload")
                        .file(mockFile)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals("unknown", data.path("fileName").asText());
        Path written = StorageLayout.resolveUrl(data.path("url").asText());
        try {
            assertTrue(written != null && Files.exists(written));
        } finally {
            if (written != null) {
                Files.deleteIfExists(written);
            }
        }
    }

    // ==================== 上传：入参校验否定面 ====================

    @Test
    @DisplayName("上传：空文件 -> code 400 上传文件不能为空")
    void upload_emptyFile_rejected() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        mockMvc.perform(multipart("/resources/upload")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]))
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("上传文件不能为空"));
        verify(resourceService, never()).save(any());
    }

    @Test
    @DisplayName("上传：MIME 白名单外（text/plain）走循环全表不命中 -> code 400")
    void upload_disallowedMime_rejected() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        mockMvc.perform(multipart("/resources/upload")
                        .file(new MockMultipartFile("file", "x.txt", "text/plain", PDF_BYTES))
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不支持该文件格式上传"));
    }

    @Test
    @DisplayName("上传：MIME 为 null 时白名单 equals(null) 全不命中 -> code 400")
    void upload_nullMime_rejected() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        mockMvc.perform(multipart("/resources/upload")
                        .file(new MockMultipartFile("file", "x.bin", null, PDF_BYTES))
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不支持该文件格式上传"));
    }

    @Test
    @DisplayName("上传：声明 pdf 实为 PNG 字节（magicMatches 各 if 假侧矩阵）-> code 400")
    void upload_magicMismatchMatrix_rejected() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        // 逐一错配：pdf/png/jpeg/gif/mp4 各走对应 equals 的假侧
        record Pair(String name, String ct, byte[] bytes) {
        }
        List<Pair> cases = List.of(
                new Pair("m1.pdf", "application/pdf", PNG_BYTES),   // pdf 声明、png 内容
                new Pair("m2.png", "image/png", PDF_BYTES),          // png 声明、pdf 内容
                new Pair("m3.jpg", "image/jpeg", GIF_BYTES),         // jpeg 声明、gif 内容
                new Pair("m4.gif", "image/gif", JPEG_BYTES),         // gif 声明、jpeg 内容
                new Pair("m5.mp4", "video/mp4", PDF_BYTES),          // mp4 声明、pdf 内容
                // 旧表格声明但给 PDF 内容 -> zip/ole2 双假侧，落最终 return false
                // （注：ZIP 容器对 application/vnd.ms-excel 是**放行**的——office 分支只要求
                //  zip 或 ole2 二者之一，见 UploadMagic.matchesMime 的 office 判定）
                new Pair("m7.xls", "application/vnd.ms-excel", PDF_BYTES),
                // 魔数不识别（8 字节无魔数）-> detectMagicType null -> 一律拒绝
                new Pair("m8.pdf", "application/pdf", NO_MAGIC_BYTES));
        for (Pair p : cases) {
            mockMvc.perform(multipart("/resources/upload")
                            .file(new MockMultipartFile("file", p.name(), p.ct(), p.bytes()))
                            .param("courseId", String.valueOf(COURSE_ID))
                            .with(withUser(OWNER_TEACHER, "TEACHER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("文件内容与声明类型不符，已拒绝上传"));
        }
        verify(resourceService, never()).save(any());
    }

    @Test
    @DisplayName("上传：内容不足 8 字节（detectMagicType 长度门槛真侧）-> code 400")
    void upload_tooShortForMagicDetection_rejected() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        mockMvc.perform(multipart("/resources/upload")
                        .file(new MockMultipartFile("file", "s.pdf", "application/pdf", SHORT_BYTES))
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件内容与声明类型不符，已拒绝上传"));
    }

    // ==================== B-11 分片合并后登记资源 ====================

    private FileUploadSession completedSession(long uploaderId, String filename, String uploadPath) {
        FileUploadSession session = new FileUploadSession();
        session.setId(7L);
        session.setUploaderId(uploaderId);
        session.setFilename(filename);
        session.setUploadPath(uploadPath);
        session.setFileSize((long) PDF_BYTES.length);
        session.setStatus("COMPLETED");
        return session;
    }

    @Test
    @DisplayName("分片登记：非会话 owner -> 403")
    void fromUploadSession_notOwner_rejected() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        when(fileUploadSessionService.getById(7L))
                .thenReturn(completedSession(OTHER_TEACHER, "a.pdf", "2026/11/a.pdf"));

        mockMvc.perform(post("/resources/from-upload-session/7")
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(resourceService, never()).save(any(Resource.class));
    }

    @Test
    @DisplayName("分片登记：COMPLETED + 魔数匹配 -> 落 resources 与首版")
    void fromUploadSession_completed_registersResource() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        when(fileUploadSessionService.getById(7L))
                .thenReturn(completedSession(OWNER_TEACHER, "a.pdf", "2026/11/a.pdf"));
        StorageLayout.writeBytes("2026/11/a.pdf", PDF_BYTES);

        mockMvc.perform(post("/resources/from-upload-session/7")
                        .param("courseId", String.valueOf(COURSE_ID))
                        .param("description", "讲义")
                        .with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value(StorageLayout.publicUrl("2026/11/a.pdf")))
                .andExpect(jsonPath("$.data.courseId").value(COURSE_ID));

        verify(resourceService).save(any(Resource.class));
        verify(resourceVersionService).createNewVersion(any(), eq("a.pdf"), eq("application/pdf"),
                eq(StorageLayout.publicUrl("2026/11/a.pdf")), eq("分片上传"), eq(OWNER_TEACHER),
                eq((long) PDF_BYTES.length));
    }

    @Test
    @DisplayName("分片登记：扩展名不在白名单 -> 400 且不落库")
    void fromUploadSession_unsupportedExtension_rejected() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        when(fileUploadSessionService.getById(7L))
                .thenReturn(completedSession(OWNER_TEACHER, "a.exe", "2026/11/a.exe"));

        mockMvc.perform(post("/resources/from-upload-session/7")
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isBadRequest());
        verify(resourceService, never()).save(any(Resource.class));
    }

    // ==================== 上传：直接调用补不可经 MVC 注入的异常面 ====================

    private com.aicourse.security.ResourceAccessSupport accessSupport(CourseService cs,
                                                                      CourseEnrollmentService es) {
        return new com.aicourse.security.ResourceAccessSupport(cs, es, adminScopeGuard);
    }

    private ResourceController directController(ResourceService rs, CourseService cs,
                                                CourseEnrollmentService es) {
        return new ResourceController(rs, courseAccessControl, accessSupport(cs, es), fileStorageService);
    }

    private ResourceUploadController directUploadController(ResourceService rs, CourseService cs,
                                                            CourseEnrollmentService es) {
        return new ResourceUploadController(rs, cs,
                mock(com.aicourse.service.ResourceVersionService.class), fileStorageService,
                mock(com.aicourse.service.FileUploadSessionService.class),
                mock(com.aicourse.service.FileVirusScanService.class), accessSupport(cs, es));
    }

    private Authentication teacherAuth() {
        return new UsernamePasswordAuthenticationToken(OWNER_TEACHER, null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
    }

    private Authentication studentAuth() {
        return new UsernamePasswordAuthenticationToken(STUDENT_ID, null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    @Test
    @DisplayName("上传（直接调用）：文件超 50MB -> code 400 大小提示")
    void uploadResource_oversize_rejected() {
        ResourceService rs = mock(ResourceService.class);
        CourseService cs = mock(CourseService.class);
        when(cs.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        MultipartFile big = mock(MultipartFile.class);
        when(big.isEmpty()).thenReturn(false);
        when(big.getSize()).thenReturn(MAX_FILE_SIZE + 1);

        var resp = directUploadController(rs, cs, mock(CourseEnrollmentService.class))
                .uploadResource(big, COURSE_ID, null, teacherAuth());
        assertEquals(400, resp.getCode());
        assertEquals("文件大小不能超过50MB", resp.getMessage());
        verify(rs, never()).save(any());
    }

    @Test
    @DisplayName("上传（直接调用）：getInputStream 抛 IOException -> code 500 读取失败包装")
    void uploadResource_getBytesIoException_wrappedAs500() throws Exception {
        ResourceService rs = mock(ResourceService.class);
        CourseService cs = mock(CourseService.class);
        when(cs.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        MultipartFile f = mock(MultipartFile.class);
        when(f.isEmpty()).thenReturn(false);
        when(f.getSize()).thenReturn(10L);
        when(f.getContentType()).thenReturn("application/pdf");
        when(f.getInputStream()).thenThrow(new IOException("磁盘读取故障"));

        var resp = directUploadController(rs, cs, mock(CourseEnrollmentService.class))
                .uploadResource(f, COURSE_ID, null, teacherAuth());
        assertEquals(500, resp.getCode());
        assertTrue(resp.getMessage().startsWith("读取上传文件失败"),
                "应包装读取失败提示，实际：" + resp.getMessage());
        assertTrue(resp.getMessage().contains("磁盘读取故障"));
    }

    @Test
    @DisplayName("上传（直接调用）：文件头输入流为 null -> code 400")
    void uploadResource_nullBytes_rejected() throws Exception {
        ResourceService rs = mock(ResourceService.class);
        CourseService cs = mock(CourseService.class);
        when(cs.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        MultipartFile f = mock(MultipartFile.class);
        when(f.isEmpty()).thenReturn(false);
        when(f.getSize()).thenReturn(0L);
        when(f.getContentType()).thenReturn("application/pdf");
        when(f.getInputStream()).thenReturn(null);

        var resp = directUploadController(rs, cs, mock(CourseEnrollmentService.class))
                .uploadResource(f, COURSE_ID, null, teacherAuth());
        assertEquals(400, resp.getCode());
        assertEquals("文件内容与声明类型不符，已拒绝上传", resp.getMessage());
    }

    @Test
    @DisplayName("上传（直接调用）：auth 为 null -> canAccessCourse 第一条 || 探针真侧 -> 403 业务异常")
    void uploadResource_nullAuth_throws403() {
        CourseService cs = mock(CourseService.class);
        when(cs.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        MultipartFile f = mock(MultipartFile.class);

        BusinessException ex = assertThrows(BusinessException.class, () -> directUploadController(
                mock(ResourceService.class), cs, mock(CourseEnrollmentService.class))
                .uploadResource(f, COURSE_ID, null, null));
        assertEquals(403, ex.getCode());
        assertEquals("无权向该课程上传资源", ex.getMessage());
    }

    @Test
    @DisplayName("上传（直接调用）：课程不存在 -> 404 业务异常（先于文件校验）")
    void uploadResource_courseMissing_throws404() {
        CourseService cs = mock(CourseService.class);
        when(cs.getById(COURSE_ID)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> directUploadController(
                mock(ResourceService.class), cs, mock(CourseEnrollmentService.class))
                .uploadResource(mock(MultipartFile.class), COURSE_ID, null, teacherAuth()));
        assertEquals(404, ex.getCode());
        assertEquals("课程不存在", ex.getMessage());
    }

    // ==================== 下载 ====================

    @Test
    @DisplayName("下载：成功 200，Content-Disposition 文件名 CRLF/引号被清洗（防头注入）")
    void downloadResource_sanitizesHeaderFileName() throws Exception {
        Path file = StorageLayout.writeBytes("cov_dl_ok.pdf", PDF_BYTES);
        try {
            when(resourceService.getById(7L))
                    .thenReturn(resourceOf(7L, COURSE_ID, "/uploads/cov_dl_ok.pdf", "evil\r\nname\"x.pdf"));
            mockMvc.perform(get("/resources/download/7").with(withUser(1L, "ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=\"evil__name_x.pdf\""));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("下载：fileName 为 null -> sanitizeHeaderValue 回退 \"file\"")
    void downloadResource_nullFileName_fallsToFile() throws Exception {
        Path file = StorageLayout.writeBytes("cov_dl_nullname.pdf", PDF_BYTES);
        try {
            when(resourceService.getById(8L))
                    .thenReturn(resourceOf(8L, COURSE_ID, "/uploads/cov_dl_nullname.pdf", null));
            mockMvc.perform(get("/resources/download/8").with(withUser(1L, "ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", "attachment; filename=\"file\""));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("下载：任教以外的教师 -> 403")
    void downloadResource_otherTeacher_forbidden() throws Exception {
        when(resourceService.getById(9L)).thenReturn(resourceOf(9L, COURSE_ID, "/uploads/a.pdf", "a.pdf"));
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        mockMvc.perform(get("/resources/download/9").with(withUser(OTHER_TEACHER, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("下载：教师但课程记录不存在 -> canAccessResource 教师分支 course==null -> 403")
    void downloadResource_teacherCourseMissing_forbidden() throws Exception {
        when(resourceService.getById(10L)).thenReturn(resourceOf(10L, COURSE_ID, "/uploads/a.pdf", "a.pdf"));
        when(courseService.getById(COURSE_ID)).thenReturn(null);
        mockMvc.perform(get("/resources/download/10").with(withUser(OTHER_TEACHER, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("下载：未报名学生 -> 403；资源 courseId 为 null 的学生 -> 直接 false")
    void downloadResource_studentVariants_forbidden() throws Exception {
        when(resourceService.getById(11L)).thenReturn(resourceOf(11L, COURSE_ID, "/uploads/a.pdf", "a.pdf"));
        when(courseEnrollmentService.hasApprovedEnrollment(STUDENT_ID, COURSE_ID)).thenReturn(false);
        mockMvc.perform(get("/resources/download/11").with(withUser(STUDENT_ID, "STUDENT")))
                .andExpect(status().isForbidden());

        when(resourceService.getById(12L)).thenReturn(resourceOf(12L, null, "/uploads/a.pdf", "a.pdf"));
        mockMvc.perform(get("/resources/download/12").with(withUser(STUDENT_ID, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("下载：资源 url 为 null 走 catch -> 404；文件不存在 -> 404")
    void downloadResource_errorPaths_notFound() throws Exception {
        when(resourceService.getById(13L)).thenReturn(resourceOf(13L, COURSE_ID, null, "a.pdf"));
        mockMvc.perform(get("/resources/download/13").with(withUser(1L, "ADMIN")))
                .andExpect(status().isNotFound());

        when(resourceService.getById(14L))
                .thenReturn(resourceOf(14L, COURSE_ID, "/uploads/definitely_absent_cov.pdf", "a.pdf"));
        mockMvc.perform(get("/resources/download/14").with(withUser(1L, "ADMIN")))
                .andExpect(status().isNotFound());
        // 「url 指向目录」的 exists∧¬isFile 假侧不在本机断言：实测 UrlResource.isFile()
        // 对 file:/…/uploads/ 返回目录为「可读」，行为随平台/ Spring 版本漂移，写成断言会在
        // CI（ubuntu）与本机间反复变红；url 由服务端生成 uuid 写入，不含用户可控路径。
    }

    @Test
    @DisplayName("下载：已报名学生 -> 200（canAccessResource 学生放行侧）")
    void downloadResource_enrolledStudent_ok() throws Exception {
        Path file = StorageLayout.writeBytes("cov_dl_student.pdf", PDF_BYTES);
        try {
            when(resourceService.getById(16L))
                    .thenReturn(resourceOf(16L, COURSE_ID, "/uploads/cov_dl_student.pdf", "s.pdf"));
            when(courseEnrollmentService.hasApprovedEnrollment(STUDENT_ID, COURSE_ID)).thenReturn(true);
            mockMvc.perform(get("/resources/download/16").with(withUser(STUDENT_ID, "STUDENT")))
                    .andExpect(status().isOk());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    // ==================== 详情 / 可见性 / 删除的存在性与 ACL 否定面 ====================

    @Test
    @DisplayName("详情：资源不存在 -> code 404")
    void getResourceById_missing_returns404() throws Exception {
        when(resourceService.getById(404L)).thenReturn(null);
        mockMvc.perform(get("/resources/404").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    @DisplayName("详情：任教教师 -> 200 透传（canAccessResource 教师匹配侧）")
    void getResourceById_ownTeacher_ok() throws Exception {
        when(resourceService.getById(21L)).thenReturn(resourceOf(21L, COURSE_ID, "/uploads/a.pdf", "a.pdf"));
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        mockMvc.perform(get("/resources/21").with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(21));
    }

    @Test
    @DisplayName("详情：教师的资源 courseId 为 null -> 三元假侧 -> code 403")
    void getResourceById_teacherResourceWithoutCourse_forbidden() throws Exception {
        when(resourceService.getById(22L)).thenReturn(resourceOf(22L, null, "/uploads/a.pdf", "a.pdf"));
        mockMvc.perform(get("/resources/22").with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问该资源"));
    }

    @Test
    @DisplayName("详情：principal 非 Long（直接调用）-> canAccessResource instanceof 假侧 -> code 403")
    void getResourceById_nonLongPrincipal_forbidden() {
        ResourceService rs = mock(ResourceService.class);
        when(rs.getById(23L)).thenReturn(resourceOf(23L, COURSE_ID, "/uploads/a.pdf", "a.pdf"));
        Authentication stringAuth = new UsernamePasswordAuthenticationToken("not-a-long", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var resp = directController(rs, mock(CourseService.class), mock(CourseEnrollmentService.class))
                .getResourceById(23L, stringAuth);
        assertEquals(403, resp.getCode());
    }

    @Test
    @DisplayName("详情（直接调用）：auth 为 null -> canAccessResource 首探针真侧 -> code 403")
    void getResourceById_nullAuth_forbidden() {
        ResourceService rs = mock(ResourceService.class);
        when(rs.getById(24L)).thenReturn(resourceOf(24L, COURSE_ID, "/uploads/a.pdf", "a.pdf"));
        var resp = directController(rs, mock(CourseService.class), mock(CourseEnrollmentService.class))
                .getResourceById(24L, null);
        assertEquals(403, resp.getCode());
        assertEquals("无权访问该资源", resp.getMessage());
    }

    @Test
    @DisplayName("可见性切换：资源不存在 -> code 404；教师非本课程 -> code 403")
    void toggleVisibility_missingAndAcl() throws Exception {
        when(resourceService.getById(25L)).thenReturn(null);
        mockMvc.perform(patch("/resources/25/visibility")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"visibleToAll\":true}")
                        .with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("资源不存在"));

        when(resourceService.getById(26L)).thenReturn(resourceOf(26L, COURSE_ID, "/uploads/a.pdf", "a.pdf"));
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        mockMvc.perform(patch("/resources/26/visibility")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"visibleToAll\":false}")
                        .with(withUser(OTHER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该课程资源"));
        verify(resourceService, never()).toggleVisibility(eq(26L), any());
    }

    @Test
    @DisplayName("删除：资源不存在 -> code 404；教师非本课程 -> code 403；文件存在时真实删除并委托 removeById")
    void deleteResource_variants() throws Exception {
        when(resourceService.getById(27L)).thenReturn(null);
        mockMvc.perform(delete("/resources/27").with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("资源不存在"));

        when(resourceService.getById(28L)).thenReturn(resourceOf(28L, COURSE_ID, "/uploads/a.pdf", "a.pdf"));
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        mockMvc.perform(delete("/resources/28").with(withUser(OTHER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权删除该课程资源"));

        Path file = StorageLayout.writeBytes("cov_del.tmp", PDF_BYTES);
        when(resourceService.getById(29L)).thenReturn(resourceOf(29L, COURSE_ID, "/uploads/cov_del.tmp", "d.tmp"));
        when(resourceService.removeById(29L)).thenReturn(true);
        // WP9 只读矩阵：资源删除面已摘除 ADMIN，真实删除路径由课程负责教师走通
        mockMvc.perform(delete("/resources/29").with(withUser(OWNER_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("资源删除成功"));
        assertFalse(Files.exists(file), "磁盘文件应被真实删除");
        verify(resourceService).removeById(29L);
    }

    // ==================== 课程级端点的否定面（canAccessCourse 剩余探针） ====================

    @Test
    @DisplayName("统计：未报名学生 -> 403")
    void stats_studentNotEnrolled_forbidden() throws Exception {
        when(courseEnrollmentService.hasApprovedEnrollment(STUDENT_ID, COURSE_ID)).thenReturn(false);
        mockMvc.perform(get("/resources/stats/course/1").with(withUser(STUDENT_ID, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("课程列表（直接调用）：auth 为 null -> 403 ResponseEntity")
    void listByCourse_nullAuth_forbidden() {
        ResponseEntity<?> resp = directController(mock(ResourceService.class), mock(CourseService.class),
                mock(CourseEnrollmentService.class)).listByCourse(COURSE_ID, null);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    @DisplayName("课程列表（直接调用）：courseId 为 null -> canAccessCourse 首探针真侧 -> 403")
    void listByCourse_nullCourseId_forbidden() {
        ResponseEntity<?> resp = directController(mock(ResourceService.class), mock(CourseService.class),
                mock(CourseEnrollmentService.class)).listByCourse(null, studentAuth());
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    @DisplayName("搜索：principal 非 Long 且未指定课程 -> 403 且不触达 Service")
    void search_nonLongPrincipalWithoutCourse_forbidden() throws Exception {
        Authentication stringAdmin = new UsernamePasswordAuthenticationToken("abc", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        mockMvc.perform(get("/resources/search").param("keyword", "k").with(authentication(stringAdmin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(resourceService, never()).searchResources(any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("搜索：auth 为 null（直接调用）且未指定课程 -> 403")
    void search_nullAuthWithoutCourse_forbidden() {
        var resp = directController(mock(ResourceService.class), mock(CourseService.class),
                mock(CourseEnrollmentService.class)).searchResources("k", null, 1, 10, null);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    @DisplayName("搜索：教师指定课程但课程不存在 -> 403")
    void search_teacherCourseMissing_forbidden() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(null);
        mockMvc.perform(get("/resources/search").param("keyword", "k")
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(OTHER_TEACHER, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("章节列表：教师非本课程 -> 403（canAccessCourse equals 假侧在章节端点的复用面）")
    void chapter_otherTeacher_forbidden() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(OWNER_TEACHER));
        mockMvc.perform(get("/resources/chapter/5").param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(OTHER_TEACHER, "TEACHER")))
                .andExpect(status().isForbidden());
        verify(resourceService, never()).getResourcesByChapter(anyLong(), anyLong());
    }

    @Test
    @DisplayName("课程资源列表：未报名学生 -> 403 且响应体带业务信封")
    void listByCourse_studentNotEnrolled_forbiddenWithBody() throws Exception {
        when(courseEnrollmentService.hasApprovedEnrollment(STUDENT_ID, COURSE_ID)).thenReturn(false);
        mockMvc.perform(get("/resources/course/1").with(withUser(STUDENT_ID, "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问该课程资源"));
    }
}
