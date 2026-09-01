package com.aicourse.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 回复点赞/取消点赞出参。
 * 字段名与历史 Map 键一致（likeCount / liked），保证前端零改动。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LikePostVO {

    private Integer likeCount;
    private Boolean liked;

    public LikePostVO() {
    }

    public LikePostVO(Integer likeCount, Boolean liked) {
        this.likeCount = likeCount;
        this.liked = liked;
    }
}
