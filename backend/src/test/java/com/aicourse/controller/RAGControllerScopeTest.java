package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.RAGService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库（RAG）端点的对象级授权接线测试（2026-11-06 验收批，B-05）。
 * <p>
 * 改前事实：{@code GET /rag/documents} 无任何注解与归属校验（学生 200 可达，不带 courseId 时
 * 更是全量列举），{@code /rag/ask}、{@code /rag/retrieve} 接受任意 courseId，
 * {@code POST/DELETE /index/**} 只判角色不判归属 —— 甲课教师可清空乙课索引。
 * 归属判定复用 {@link CourseAccessControl}，本类只验证「有没有调到、调到的是哪个课程」。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("知识库授权：RAGController")
class RAGControllerScopeTest {

    @Mock private RAGService ragService;
    @Mock private CourseAccessControl courseAccessControl;

    private static Authentication as(String role) {
        return new UsernamePasswordAuthenticationToken(
                5001L, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private RAGController controller() {
        return new RAGController(ragService, courseAccessControl);
    }

    private static BusinessException forbidden() {
        return new BusinessException(ApiCode.FORBIDDEN, "无权操作其他教师的课程");
    }

    @Test
    @DisplayName("文档列举必须先过课程归属（他课教师 403，且不落到引擎）")
    void listDocumentsChecksCourseOwnership() {
        doThrow(forbidden()).when(courseAccessControl).assertCanManageCourse(eq(4060L), any());

        assertThatThrownBy(() -> controller().listDocuments(4060L, as("TEACHER")))
                .isInstanceOf(BusinessException.class);

        verify(ragService, never()).listDocuments(any());
    }

    @Test
    @DisplayName("不带 courseId 的全量列举：教师 403，管理员放行")
    void fullListingIsAdminOnly() {
        assertThatThrownBy(() -> controller().listDocuments(null, as("TEACHER")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅管理员可用");

        when(ragService.listDocuments(null)).thenReturn("{}");
        controller().listDocuments(null, as("ADMIN"));
        verify(ragService).listDocuments(null);
    }

    @Test
    @DisplayName("问答与检索按 courseId 接地前必须判成员关系")
    void askAndRetrieveRequireMembership() {
        doThrow(forbidden()).when(courseAccessControl).assertCanReadCourse(anyLong(), any());

        assertThatThrownBy(() -> controller().ask("题", 4060L, 3, as("STUDENT")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller().retrieve("关键词", 4060L, 5, as("STUDENT")))
                .isInstanceOf(BusinessException.class);

        verify(courseAccessControl, times(2)).assertCanReadCourse(eq(4060L), any());
        verify(ragService, never()).ask(any(), any(), anyInt());
    }

    @Test
    @DisplayName("索引构建/清空判课程归属（甲课教师不能清乙课索引）")
    void indexOperationsRequireOwnership() {
        doThrow(forbidden()).when(courseAccessControl).assertCanManageCourse(eq(4060L), any());

        assertThatThrownBy(() -> controller().buildIndex(4060L, as("TEACHER")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller().clearIndex(4060L, as("TEACHER")))
                .isInstanceOf(BusinessException.class);

        verify(ragService, never()).buildIndex(anyLong());
        verify(ragService, never()).clearIndex(anyLong());
    }
}
