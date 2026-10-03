package com.aicourse.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 教师端题目批量导入结果出参（JSON 与 Excel 两用）。
 * 字段名与历史 Map 键一致（importedCount / total）。
 * JSON 导入原 Map 仅含 importedCount 单键；Excel 导入含 importedCount/total 双键。
 * 借 {@code @JsonInclude(NON_NULL)} 精确复刻：total 为 null（JSON 导入）时键缺失，保证前端零改动。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeacherImportResultVO {

    private int importedCount;
    private Integer total;

    /**
     * 因「同课程同题干已存在」而跳过的条数（2026-09-23 新增，修复 P2-2 重复导入不幂等）。
     */
    private Integer skippedCount;

    /**
     * 写入失败的条数（2026-09-23 新增）。
     * 原实现单条失败会导致整批回滚且报错不指明具体条目，现改为逐条容错并报告。
     */
    private Integer failedCount;

    /**
     * 失败明细（每条含序号与原因），便于用户直接定位问题行。失败数为 0 时不输出该键。
     */
    private java.util.List<String> errors;

    public TeacherImportResultVO() {
    }

    public TeacherImportResultVO(int importedCount, Integer total) {
        this.importedCount = importedCount;
        this.total = total;
    }
}
