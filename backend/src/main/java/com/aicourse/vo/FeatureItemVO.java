package com.aicourse.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 功能路线图单项（名称 + 状态）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeatureItemVO {

    private String name;
    private String status;

    public FeatureItemVO() {
    }

    public FeatureItemVO(String name, String status) {
        this.name = name;
        this.status = status;
    }
}
