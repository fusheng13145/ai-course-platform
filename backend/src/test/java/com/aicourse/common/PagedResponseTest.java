package com.aicourse.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PagedResponse 单元测试：覆盖 of(IPage) / of(List,...)（含 pageSize=0 边界）/
 * empty() / 各 getter/setter。
 */
class PagedResponseTest {

    @Test
    void of_ipage() {
        IPage<String> page = mock(IPage.class);
        when(page.getRecords()).thenReturn(List.of("a", "b"));
        when(page.getTotal()).thenReturn(2L);
        when(page.getCurrent()).thenReturn(1L);
        when(page.getSize()).thenReturn(10L);
        when(page.getPages()).thenReturn(1L);

        PagedResponse<String> r = PagedResponse.of(page);
        assertEquals(List.of("a", "b"), r.getList());
        assertEquals(2L, r.getTotal());
        assertEquals(1L, r.getPage());
        assertEquals(10L, r.getPageSize());
        assertEquals(1L, r.getTotalPages());
        assertFalse(r.isHasNext());
        assertFalse(r.isHasPrevious());
    }

    @Test
    void of_list_withPageSizeZero() {
        PagedResponse<String> r = PagedResponse.of(List.of("a"), 5L, 2L, 0L);
        assertEquals(0L, r.getTotalPages()); // pageSize=0 -> 0 页，边界
        assertFalse(r.isHasNext());
        assertFalse(r.isHasPrevious());
    }

    @Test
    void of_list_normal() {
        PagedResponse<String> r = PagedResponse.of(List.of("a", "b"), 10L, 2L, 5L);
        assertEquals(5L, r.getPageSize());
        assertEquals(2L, r.getTotalPages()); // ceil(10/5)=2
        assertFalse(r.isHasNext()); // page(2) < totalPages(2) => false
        assertTrue(r.isHasPrevious()); // page 2 > 1
    }

    @Test
    void empty() {
        PagedResponse<String> r = PagedResponse.empty();
        assertTrue(r.getList().isEmpty());
        assertEquals(0L, r.getTotal());
        assertEquals(1L, r.getPage());
        assertEquals(10L, r.getPageSize());
        assertFalse(r.isHasNext());
        assertFalse(r.isHasPrevious());
    }

    @Test
    void setters() {
        PagedResponse<String> r = PagedResponse.empty();
        r.setList(List.of("x"));
        r.setTotal(3L);
        r.setPage(1L);
        r.setPageSize(3L);
        r.setTotalPages(1L);
        r.setHasNext(true);
        r.setHasPrevious(false);
        assertEquals(List.of("x"), r.getList());
        assertEquals(3L, r.getTotal());
        assertTrue(r.isHasNext());
    }
}
