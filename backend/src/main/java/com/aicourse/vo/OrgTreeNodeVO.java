package com.aicourse.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组织树节点（V12 / B-12-P-08 WP1）。
 *
 * <p>学校 → 学院 → 专业 → 行政班统一用同一节点形状，前端按 {@code type} 区分层级。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrgTreeNodeVO {

    /** 节点ID */
    private Long id;

    /** 节点类型：SCHOOL / COLLEGE / MAJOR / ADMIN_CLASS */
    private String type;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 子节点 */
    private List<OrgTreeNodeVO> children;
}
