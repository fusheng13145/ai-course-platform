package com.aicourse.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 批量文件上传单项结果出参（POST /common/upload/batch）。
 * 字段名与历史 Map 键一致（url / fileName / size / error）。
 * 原实现为条件键：成功项含 url/fileName/size（error 不出现），
 * 失败项含 url(空)/fileName/size(0)/error —— 以 @JsonInclude(NON_NULL) 精确复刻「error 仅失败时出现」。
 * 全部值为 String（与历史 Map<String, String> 一致）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadResultVO {

    /** 访问路径（失败为空串） */
    private String url;
    /** 原始文件名（空文件名为"未命名文件"） */
    private String fileName;
    /** 文件大小（字符串，失败为 "0"） */
    private String size;
    /** 失败原因（仅失败项出现，成功项为 null 即键缺失） */
    private String error;

    public UploadResultVO() {
    }

    public UploadResultVO(String url, String fileName, String size) {
        this.url = url;
        this.fileName = fileName;
        this.size = size;
    }

    public UploadResultVO(String url, String fileName, String size, String error) {
        this.url = url;
        this.fileName = fileName;
        this.size = size;
        this.error = error;
    }
}
