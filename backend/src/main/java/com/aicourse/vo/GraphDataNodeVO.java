package com.aicourse.vo;

import lombok.Data;

/**
 * 知识图谱可视化「图节点」子结构出参。
 * 字段名与历史 Map 键一致（id / name / category）。
 * 原实现无条件 put 此三键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class GraphDataNodeVO {

    private Long id;
    private String name;
    private String category;

    public GraphDataNodeVO() {
    }

    public GraphDataNodeVO(Long id, String name, String category) {
        this.id = id;
        this.name = name;
        this.category = category;
    }
}
