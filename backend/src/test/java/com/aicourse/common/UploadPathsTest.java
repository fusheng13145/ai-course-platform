package com.aicourse.common;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V-1：上传路径解析与删盘的边界（穿越防护、非法 URL、缺失文件）。
 */
class UploadPathsTest {

    @Test
    void stored_url_resolves_inside_upload_base() {
        Path p = UploadPaths.resolveUrl("/uploads/abc.pdf");
        assertNotNull(p);
        assertTrue(p.startsWith(UploadPaths.base()));
        assertEquals("abc.pdf", p.getFileName().toString());
    }

    @Test
    void url_without_leading_slash_is_also_accepted() {
        assertNotNull(UploadPaths.resolveUrl("uploads/abc.pdf"));
    }

    @Test
    void path_traversal_is_rejected() {
        assertNull(UploadPaths.resolveUrl("/uploads/../application.yaml"));
        assertNull(UploadPaths.resolveUrl("/uploads/..\\application.yaml"));
        assertNull(UploadPaths.resolveUrl("/uploads/sub/dir.pdf"));
    }

    @Test
    void illegal_inputs_are_rejected() {
        assertNull(UploadPaths.resolveUrl(null));
        assertNull(UploadPaths.resolveUrl("/uploads/"));
        assertNull(UploadPaths.resolveUrl("/other/abc.pdf"));
    }

    @Test
    void delete_on_missing_file_returns_false_and_does_not_throw() {
        assertFalse(UploadPaths.deleteQuietly("/uploads/__nope_never_exists__.pdf"));
        assertFalse(UploadPaths.deleteQuietly(null));
    }
}
