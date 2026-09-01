package com.aicourse.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异常登录检测出参。
 * 字段名与历史 Map 键一致（userId / recentLoginCount / isAbnormal / abnormalDetails /
 * uniqueIpCount / uniqueLocationCount / recentFailedCount / recentSuccessCount / checkedAt），
 * 保证前端零改动。
 * <p>
 * 注意 isAbnormal 的序列化命名：字段声明为 {@code abnormal} 时 Lombok 生成 {@code isAbnormal()}，
 * Jackson 按 Bean 规范会将其属性名推导为 {@code abnormal}（与历史键 isAbnormal 不符），
 * 故此处用 {@code @JsonProperty("isAbnormal")} 显式钉死 JSON 键名。
 * <p>
 * 全部键在原实现中均无条件 put，故采用默认 INCLUDE.ALWAYS 策略（不加 NON_NULL）。
 */
@Data
public class AbnormalLoginVO {

    private Long userId;
    /** 最近 24 小时内的登录次数 */
    private int recentLoginCount;
    /** 是否判定为异常登录（JSON 键固定为 isAbnormal） */
    @JsonProperty("isAbnormal")
    private boolean abnormal;
    /** 异常判定依据描述列表 */
    private List<String> abnormalDetails;
    private int uniqueIpCount;
    private int uniqueLocationCount;
    private long recentFailedCount;
    private long recentSuccessCount;
    /** 检测执行时间 */
    private LocalDateTime checkedAt;

    public AbnormalLoginVO() {
    }

    public AbnormalLoginVO(Long userId, int recentLoginCount, boolean abnormal,
                           List<String> abnormalDetails, int uniqueIpCount, int uniqueLocationCount,
                           long recentFailedCount, long recentSuccessCount, LocalDateTime checkedAt) {
        this.userId = userId;
        this.recentLoginCount = recentLoginCount;
        this.abnormal = abnormal;
        this.abnormalDetails = abnormalDetails;
        this.uniqueIpCount = uniqueIpCount;
        this.uniqueLocationCount = uniqueLocationCount;
        this.recentFailedCount = recentFailedCount;
        this.recentSuccessCount = recentSuccessCount;
        this.checkedAt = checkedAt;
    }
}
