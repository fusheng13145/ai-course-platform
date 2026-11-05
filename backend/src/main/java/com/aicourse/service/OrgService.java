package com.aicourse.service;

import com.aicourse.vo.OrgTreeNodeVO;

import java.util.List;

/**
 * 组织架构业务接口（V12 / B-12-P-08 WP1）。
 */
public interface OrgService {

    /**
     * 查询组织树（学校 → 学院 → 专业 → 行政班）。
     *
     * <p>校级管理员返回全树；院系管理员只返回本学院及其下属。</p>
     *
     * @return 组织树根节点列表（单校部署下通常只有一个根）
     */
    List<OrgTreeNodeVO> getTree();
}
