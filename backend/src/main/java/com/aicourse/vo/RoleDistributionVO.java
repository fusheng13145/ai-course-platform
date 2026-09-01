package com.aicourse.vo;

import lombok.Data;

/**
 * 角色分布出参（平台数据概览子结构）。
 * 字段名与历史 Map 键一致（student / teacher / admin）。
 * 原实现无条件 put 此三键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class RoleDistributionVO {

    private long student;
    private long teacher;
    private long admin;

    public RoleDistributionVO() {
    }

    public RoleDistributionVO(long student, long teacher, long admin) {
        this.student = student;
        this.teacher = teacher;
        this.admin = admin;
    }
}
