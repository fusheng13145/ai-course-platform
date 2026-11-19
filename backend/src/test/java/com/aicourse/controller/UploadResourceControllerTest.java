package com.aicourse.controller;

import com.aicourse.entity.Resource;
import com.aicourse.service.FileStorageService;
import com.aicourse.service.ResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UploadResourceControllerTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ResourceService resourceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UploadResourceController(fileStorageService, resourceService))
                .build();
    }

    @Test
    void serve_streamsMinioObjectWithDeclaredContentType() throws Exception {
        Resource resource = new Resource();
        resource.setFileName("a.png");
        resource.setFileType("image/png");
        when(resourceService.findByUrl("/uploads/resources/1/a.png")).thenReturn(resource);
        when(fileStorageService.downloadByUrl("/uploads/resources/1/a.png"))
                .thenReturn(new ByteArrayInputStream("PNG".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/uploads/resources/1/a.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes("PNG".getBytes(StandardCharsets.UTF_8)))
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    @Test
    void serve_fallsBackToExtensionWhenResourceRowIsMissing() throws Exception {
        when(resourceService.findByUrl("/uploads/resources/1/note.pdf")).thenReturn(null);
        when(fileStorageService.downloadByUrl("/uploads/resources/1/note.pdf"))
                .thenReturn(new ByteArrayInputStream("PDF".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/uploads/resources/1/note.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void serve_downgradesHtmlToOctetStream() throws Exception {
        Resource resource = new Resource();
        resource.setFileName("legacy.html");
        resource.setFileType("text/html");
        when(resourceService.findByUrl("/uploads/resources/1/legacy.html")).thenReturn(resource);
        when(fileStorageService.downloadByUrl("/uploads/resources/1/legacy.html"))
                .thenReturn(new ByteArrayInputStream("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/uploads/resources/1/legacy.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"));
    }

    @Test
    void serve_downgradesHtmlWithCharsetParameter() throws Exception {
        Resource resource = new Resource();
        resource.setFileName("legacy.html");
        resource.setFileType("text/html;charset=UTF-8");
        when(resourceService.findByUrl("/uploads/resources/1/legacy.html")).thenReturn(resource);
        when(fileStorageService.downloadByUrl("/uploads/resources/1/legacy.html"))
                .thenReturn(new ByteArrayInputStream("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/uploads/resources/1/legacy.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"));
    }

    @Test
    void serve_returns404WhenStorageHasNoObject() throws Exception {
        when(fileStorageService.downloadByUrl("/uploads/resources/1/missing.pdf")).thenReturn(null);

        mockMvc.perform(get("/uploads/resources/1/missing.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    void serve_returns404OnInvalidPathOrStorageFailure() throws Exception {
        mockMvc.perform(get("/uploads/../secret.txt"))
                .andExpect(status().isNotFound());

        when(fileStorageService.downloadByUrl("/uploads/resources/1/broken.pdf"))
                .thenThrow(new RuntimeException("MinIO down"));
        mockMvc.perform(get("/uploads/resources/1/broken.pdf"))
                .andExpect(status().isNotFound());
    }
}
