package com.aicourse.vo;

import lombok.Data;

/**
 * 试卷统计「试卷信息」子结构出参。
 * 字段名与历史 Map 键一致（id / title / status）。
 * 原实现无条件 put 此三键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class ExamPaperStatsPaperVO {

    private Long id;
    private String title;
    private String status;

    public ExamPaperStatsPaperVO() {
    }

    public ExamPaperStatsPaperVO(Long id, String title, String status) {
        this.id = id;
        this.title = title;
        this.status = status;
    }
}
