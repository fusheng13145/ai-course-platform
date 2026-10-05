package com.aicourse.controller;

import com.aicourse.entity.Course;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分页参数边界（T3）——CourseController 列表页。
 *
 * <p><b>按现状断言</b>：控制器使用 {@code Page.of(page, size)} 传参。实测 MyBatis-Plus 3.5.7 的
 * {@code Page} 对 <b>current 存在下界兜底</b>：{@code page<=0}（含 0、-1）会被归一化为 {@code 1}；
 * 但 <b>对 size 既不设上限也不设下界</b>——{@code size=0}、{@code size=100000} 均原样透传。
 * 真正的 {@code maxLimit} 若在别处生效也发生在 {@code PaginationInnerInterceptor} 查询阶段，
 * 而单元层 Service 被 mock，不触发该拦截器。</p>
 *
 * <p>据此锁定「页码非法被兜底、每页条数完全裸奔」的现状，并在汇报中提出
 * <b>风险待办</b>：生产代码对 size 无上限（size=100000 可致内存/DB 压力）、且允许 size=0。
 * 是否加钳制属影响面较大的改动，需团队评审后再定，此处不擅改 src/main。</p>
 */
@WebMvcTest(CourseController.class)
class CourseControllerPaginationBoundaryTest extends ControllerTestBase {

    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private CourseEnrollmentService enrollmentService;

    @ParameterizedTest(name = "page={0} size={1} -> current={2} size={3}")
    @CsvSource({
            "1,10,1,10",
            "0,10,1,10",
            "-1,10,1,10",
            "1,0,1,0",
            "1,100000,1,100000",
            "99999,10,99999,10"
    })
    @DisplayName("GET /courses - 页码≤0 被兜底为1、每页条数原样透传（无上限，风险项）")
    void list_normalizesCurrentButNotSize(long page, long size, long expectedCurrent, long expectedSize) throws Exception {
        when(courseService.page(any(), any())).thenReturn(new Page<Course>());
        mockMvc.perform(get("/courses")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk());

        ArgumentCaptor<IPage> captor = ArgumentCaptor.forClass(IPage.class);
        verify(courseService).page(captor.capture(), any());
        IPage<?> forwarded = captor.getValue();
        assertEquals(expectedCurrent, forwarded.getCurrent(), "页码≤0 应被兜底为 1，合法值原样透传");
        assertEquals(expectedSize, forwarded.getSize(), "每页条数应原样透传给 Service（无上限钳制，风险项）");
    }
}
