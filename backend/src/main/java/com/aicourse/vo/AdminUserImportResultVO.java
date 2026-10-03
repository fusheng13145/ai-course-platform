package com.aicourse.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 管理员批量导入用户结果出参。
 *
 * 覆盖 auth 无关的两处管理端导入端点：
 * - POST /admin/users/import（JSON 导入）：恒含 successCount / failCount / total，无 tempPasswords
 * - POST /admin/users/import-excel（Excel 导入）：恒含上述三键 + tempPasswords
 *
 * tempPasswords 为动态键嵌套 Map（键=用户名，值=该用户的系统随机生成明文初始密码），
 * 保留 Map 形态（同 VoteResultVO.counts 模式）。
 *
 * errors 为逐行失败原因（键=可读文案，含用户名），供前端展示「哪一行为什么没导入」；
 * 全部成功时为 null，NON_NULL 下该键不出现。
 *
 * 采用 NON_NULL：JSON 导入场景 tempPasswords 为 null 时该键不出现，
 * 精确复刻历史 Map 差异（Excel 场景才返回临时密码）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminUserImportResultVO {

    private int successCount;
    private int failCount;
    private int total;
    private Map<String, String> tempPasswords;
    private List<String> errors;

    public AdminUserImportResultVO() {
    }

    public AdminUserImportResultVO(int successCount, int failCount, int total) {
        this.successCount = successCount;
        this.failCount = failCount;
        this.total = total;
    }

    public AdminUserImportResultVO(int successCount, int failCount, int total,
                                   Map<String, String> tempPasswords) {
        this.successCount = successCount;
        this.failCount = failCount;
        this.total = total;
        this.tempPasswords = tempPasswords;
    }

    public AdminUserImportResultVO(int successCount, int failCount, int total,
                                   Map<String, String> tempPasswords, List<String> errors) {
        this.successCount = successCount;
        this.failCount = failCount;
        this.total = total;
        this.tempPasswords = tempPasswords;
        this.errors = errors;
    }
}
