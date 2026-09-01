package com.aicourse.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 功能路线图出参（版本 + 功能项列表）。
 * 字段名与历史 Map 键一致（version / features），保证前端零改动。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeatureRoadmapVO {

    private String version;
    private List<FeatureItemVO> features;

    public FeatureRoadmapVO() {
    }

    public FeatureRoadmapVO(String version, List<FeatureItemVO> features) {
        this.version = version;
        this.features = features;
    }
}
