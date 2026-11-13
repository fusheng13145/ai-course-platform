package com.aicourse.config;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Resource;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ResourceMapper;
import com.aicourse.security.CourseAccessControl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import jakarta.servlet.FilterChain;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上传直链 ACL 过滤器测试（B-05 余量，2026-11-06）。
 * <p>
 * 钉住三条边界：①**受限资源**（{@code visible_to_all=false}）的直链必须由课程归属判定拦住，
 * 否则「知道 UUID 就能读」＝绕过 {@code /resources/download/{id}} 的对象级 ACL（DR-16 留下的判据）；
 * ②查不到资源行的路径**不改判**（课程封面/头像等非资源模型文件，强判即坏功能）；
 * ③判定复用 {@code CourseAccessControl}，本过滤器不自写成员关系规则。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("受限资源直链：UploadResourceAccessFilter")
class UploadResourceAccessFilterTest {

    @Mock private ResourceMapper resourceMapper;
    @Mock private CourseAccessControl courseAccessControl;
    @Mock private FilterChain chain;

    private UploadResourceAccessFilter filter;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Resource.class);
        filter = new UploadResourceAccessFilter(resourceMapper, courseAccessControl);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Resource resource(Boolean visibleToAll, Long courseId) {
        Resource r = new Resource();
        r.setId(9L);
        r.setUrl("/uploads/3f2a-seed.pdf");
        r.setCourseId(courseId);
        r.setVisibleToAll(visibleToAll);
        return r;
    }

    private static MockHttpServletRequest uploadRequest(String relative) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/uploads/" + relative);
        request.setContextPath("/api");
        return request;
    }

    private static void loginAs(Long userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    @DisplayName("路径解析：带 context-path 的直链能取到相对路径，非直链返回空串")
    void pathResolutionIsCorrect() {
        MockHttpServletRequest withCtx = new MockHttpServletRequest("GET", "/api/uploads/a.pdf");
        withCtx.setContextPath("/api");
        org.assertj.core.api.Assertions.assertThat(
                UploadResourceAccessFilter.uploadRelativePath(withCtx)).isEqualTo("a.pdf");

        MockHttpServletRequest outside = new MockHttpServletRequest("GET", "/api/resources/download/9");
        outside.setContextPath("/api");
        org.assertj.core.api.Assertions.assertThat(
                UploadResourceAccessFilter.uploadRelativePath(outside)).isEmpty();
    }

    @Test
    @DisplayName("受限资源 + 未选课学生：403，静态处理器不被触及")
    void restrictedResourceBlocksForeignStudent() throws Exception {
        loginAs(5001L, "STUDENT");
        when(resourceMapper.selectOne(any(Wrapper.class))).thenReturn(resource(false, 4060L));
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "尚未加入该课程，无权查看课程内容"))
                .when(courseAccessControl).assertCanReadCourse(eq(4060L), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(uploadRequest("3f2a-seed.pdf"), response, chain);

        verify(resourceMapper).selectOne(any(Wrapper.class));
        verify(courseAccessControl).assertCanReadCourse(eq(4060L), any());
        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("受限资源：判定用的是资源行自己的 courseId（不是请求参数）")
    void restrictedResourceUsesStoredCourseId() throws Exception {
        loginAs(5001L, "STUDENT");
        when(resourceMapper.selectOne(any(Wrapper.class))).thenReturn(resource(false, 4027L));

        filter.doFilter(uploadRequest("3f2a-seed.pdf"), new MockHttpServletResponse(), chain);

        verify(courseAccessControl).assertCanReadCourse(eq(4027L), any());
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("受限资源 + 已选课学生：放行（不改变合法用户的读法）")
    void restrictedResourceAllowsEnrolledStudent() throws Exception {
        loginAs(5001L, "STUDENT");
        when(resourceMapper.selectOne(any(Wrapper.class))).thenReturn(resource(false, 4007L));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(uploadRequest("3f2a-seed.pdf"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("全员可见资源：放行且不判归属（与上传默认值 visible_to_all=true 一致）")
    void publicResourceSkipsOwnershipCheck() throws Exception {
        loginAs(5001L, "STUDENT");
        when(resourceMapper.selectOne(any(Wrapper.class))).thenReturn(resource(true, 4060L));

        filter.doFilter(uploadRequest("3f2a-seed.pdf"), new MockHttpServletResponse(), chain);

        verify(courseAccessControl, never()).assertCanReadCourse(any(), any());
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("visible_to_all 为 null（历史行）按受限处理，不默认放行")
    void nullVisibilityIsTreatedAsRestricted() throws Exception {
        loginAs(5001L, "STUDENT");
        when(resourceMapper.selectOne(any(Wrapper.class))).thenReturn(resource(null, 4060L));
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权查看其他教师课程的内容"))
                .when(courseAccessControl).assertCanReadCourse(any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(uploadRequest("3f2a-seed.pdf"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("查不到资源行的路径：不改判（课程封面/头像等仍走既有 capability 语义）")
    void unknownPathKeepsExistingBehaviour() throws Exception {
        loginAs(5001L, "STUDENT");
        when(resourceMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        filter.doFilter(uploadRequest("cover-abc.png"), new MockHttpServletResponse(), chain);

        verify(courseAccessControl, never()).assertCanReadCourse(any(), any());
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("含 .. 的路径不查库（穿越防护仍由静态处理器负责，这里不放大权限也不误判）")
    void traversalPathIsNotResolvedAgainstDatabase() throws Exception {
        loginAs(5001L, "STUDENT");

        filter.doFilter(uploadRequest("../secret.txt"), new MockHttpServletResponse(), chain);

        verify(resourceMapper, never()).selectOne(any(Wrapper.class));
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("非 uploads 路径根本不进过滤器")
    void nonUploadPathIsSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/resources/download/9");
        request.setContextPath("/api");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(resourceMapper, never()).selectOne(any(Wrapper.class));
        verify(chain).doFilter(any(), any());
    }
}
