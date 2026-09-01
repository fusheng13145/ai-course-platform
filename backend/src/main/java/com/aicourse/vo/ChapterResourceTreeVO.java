package com.aicourse.vo;

import com.aicourse.entity.Resource;
import lombok.Data;

import java.util.List;

/**
 * 课程资源树节点出参（按章节组织）。
 * 字段名与历史 Map 键一致（chapterId / resources）。
 * resources 为章节下资源实体列表（非 Map 嵌套，键集天然固定，直接承载实体）。
 * 原实现无条件 put 此两键（恒含），故默认 INCLUDE.ALWAYS，精确复刻。
 */
@Data
public class ChapterResourceTreeVO {

    private Long chapterId;
    private List<Resource> resources;

    public ChapterResourceTreeVO() {
    }

    public ChapterResourceTreeVO(Long chapterId, List<Resource> resources) {
        this.chapterId = chapterId;
        this.resources = resources;
    }
}
