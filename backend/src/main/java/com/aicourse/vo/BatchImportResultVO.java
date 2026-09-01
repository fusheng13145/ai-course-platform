package com.aicourse.vo;

import lombok.Data;

/**
 * 批量导入题目结果出参。
 * 字段名与历史 Map 键一致（imported / total）。
 * 原实现无条件 put 此两键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class BatchImportResultVO {

    private int imported;
    private int total;

    public BatchImportResultVO() {
    }

    public BatchImportResultVO(int imported, int total) {
        this.imported = imported;
        this.total = total;
    }
}
