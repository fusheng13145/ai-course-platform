package com.aicourse.vo;

import lombok.Data;

import java.util.List;

/**
 * 文件分片上传进度出参。
 * 字段名与历史 Map 键一致：uploaded(已上传分片数)/total(总分片数)/percentage(百分比字符串，如 "50.0%")/status(会话状态)。
 * 原实现无条件 put 四键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 *
 * <p>missingIndexes 为 D1-2 批次 2 新增键（0 基、升序的缺失分片索引），uploaded/percentage
 * 与它同源取自磁盘分片，不再沿用会话行计数；旧客户端忽略新键即可，属向后兼容的加键。
 */
@Data
public class UploadProgressVO {

    private Integer uploaded;
    private Integer total;
    private String percentage;
    private String status;
    private List<Integer> missingIndexes;

    public UploadProgressVO() {
    }

    public UploadProgressVO(Integer uploaded, Integer total, String percentage, String status,
                            List<Integer> missingIndexes) {
        this.uploaded = uploaded;
        this.total = total;
        this.percentage = percentage;
        this.status = status;
        this.missingIndexes = missingIndexes;
    }
}
