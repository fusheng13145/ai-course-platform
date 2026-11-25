package com.aicourse.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.function.Function;

/**
 * 分页出参统一装配：把 MyBatis-Plus 的 {@link IPage} 与实体→VO 映射收敛到一处。
 *
 * <p>此前 8 个应用服务/控制器各写一份 {@code toPagedXxx}，字段口径
 * （list/total/page/pageSize）完全一致、命名却各不相同；改分页语义时容易漏改。
 * 这里只保留一个入口，映射函数由调用方给出，对外字段与 {@link PagedResponse} 保持不变。</p>
 */
public final class PageSupport {

    private PageSupport() {
    }

    /**
     * 把分页查询结果映射为对外分页响应。
     *
     * @param page   数据源分页对象（记录、总数、当前页、页大小）
     * @param mapper 实体 → 出参 VO 的映射函数
     * @return 字段口径与原 {@code PagedResponse.of(list, total, current, size)} 完全一致
     */
    public static <S, T> PagedResponse<T> toPage(IPage<S> page, Function<? super S, T> mapper) {
        java.util.List<T> records = page.getRecords().stream().map(mapper).toList();
        return PagedResponse.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
