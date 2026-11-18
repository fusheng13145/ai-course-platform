package com.aicourse.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 行政班成员批量结果：每一项都显式返回，便于前端展示“成功 / 跳过”并解释原因。
 */
@Data
@AllArgsConstructor
public class AdminClassMemberBatchVO {

    private int added;
    private int removed;
    private int skipped;
}
