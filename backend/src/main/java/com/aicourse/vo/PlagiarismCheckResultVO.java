package com.aicourse.vo;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.PlagiarismMatch;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 查重检测结果详情出参（查重任务信息 + 相似度指标 + 匹配明细）。
 * 字段名与历史 Map 键一致（checkInfo / similarityScore / highSimilarityCount / resultSummary /
 * status / matches / checkDetailRaw / algorithmVersion），保证前端零改动。
 * <p>
 * 序列化策略按原 Map 语义精确复刻：
 * <ul>
 *   <li>前六个键无条件 put —— 采用默认 INCLUDE.ALWAYS；</li>
 *   <li>checkDetailRaw / algorithmVersion 仅在 checkDetail 非空（且含算法版本标识）时 put
 *       —— 采用字段级 {@code @JsonInclude(NON_NULL)}，为 null 时键不出现。</li>
 * </ul>
 */
@Data
public class PlagiarismCheckResultVO {

    private PlagiarismCheck checkInfo;

    private BigDecimal similarityScore;

    private Integer highSimilarityCount;

    private String resultSummary;

    private String status;

    private List<PlagiarismMatch> matches;

    /** 原始 checkDetail JSON 串；仅当 checkDetail 非空时存在 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String checkDetailRaw;

    /** 算法版本标识；仅当 checkDetail 含 algorithmVersion 时存在 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String algorithmVersion;

    public PlagiarismCheckResultVO() {
    }

    public PlagiarismCheckResultVO(PlagiarismCheck checkInfo,
                                   BigDecimal similarityScore,
                                   Integer highSimilarityCount,
                                   String resultSummary,
                                   String status,
                                   List<PlagiarismMatch> matches,
                                   String checkDetailRaw,
                                   String algorithmVersion) {
        this.checkInfo = checkInfo;
        this.similarityScore = similarityScore;
        this.highSimilarityCount = highSimilarityCount;
        this.resultSummary = resultSummary;
        this.status = status;
        this.matches = matches;
        this.checkDetailRaw = checkDetailRaw;
        this.algorithmVersion = algorithmVersion;
    }
}
