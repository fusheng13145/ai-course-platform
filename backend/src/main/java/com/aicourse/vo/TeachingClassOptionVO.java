package com.aicourse.vo;

import com.aicourse.entity.TeachingClass;
import lombok.Data;

/**
 * 学生端「可选教学班」出参模型（3E-05B · Q-05 全量改 VO）。
 *
 * <p>字段名与历史 {@code Map} 键（id / name / code / capacity）逐字一致，前端零改动。</p>
 */
@Data
public class TeachingClassOptionVO {

    private Long id;
    private String name;
    private String code;
    private Integer capacity;

    public static TeachingClassOptionVO from(TeachingClass item) {
        TeachingClassOptionVO vo = new TeachingClassOptionVO();
        vo.setId(item.getId());
        vo.setName(item.getName());
        vo.setCode(item.getCode());
        vo.setCapacity(item.getCapacity());
        return vo;
    }
}
