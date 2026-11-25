package com.aicourse.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PageSupport} 的字段口径守卫：分页包装收敛后，对外字段必须与原 8 份
 * {@code toPagedXxx} 完全一致（list/total/page/pageSize/totalPages/hasNext/hasPrevious）。
 */
class PageSupportTest {

    @Test
    void mapsRecordsAndKeepsPagingFields() {
        Page<String> page = new Page<>(2, 3);
        page.setRecords(List.of("a", "bb"));
        page.setTotal(10);

        PagedResponse<Integer> response = PageSupport.toPage(page, String::length);

        assertThat(response.getList()).containsExactly(1, 2);
        assertThat(response.getTotal()).isEqualTo(10);
        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getPageSize()).isEqualTo(3);
        assertThat(response.getTotalPages()).isEqualTo(4);
        assertThat(response.isHasNext()).isTrue();
        assertThat(response.isHasPrevious()).isTrue();
    }

    @Test
    void emptyPageKeepsRequestedPageAndSize() {
        Page<String> page = new Page<>(3, 5);
        page.setRecords(List.of());
        page.setTotal(0);

        PagedResponse<String> response = PageSupport.toPage(page, value -> value);

        assertThat(response.getList()).isEmpty();
        assertThat(response.getPage()).isEqualTo(3);
        assertThat(response.getPageSize()).isEqualTo(5);
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.isHasNext()).isFalse();
        // 总页数为 0 时不输出“上一页”，避免前端出现可点但无数据的翻页
        assertThat(response.isHasPrevious()).isFalse();
    }

    @Test
    void zeroPageSizeDoesNotDivideByZero() {
        Page<String> page = new Page<>(1, 0);
        page.setRecords(List.of("a"));
        page.setTotal(1);

        PagedResponse<String> response = PageSupport.toPage(page, value -> value);

        assertThat(response.getTotalPages()).isZero();
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.isHasPrevious()).isFalse();
    }
}
