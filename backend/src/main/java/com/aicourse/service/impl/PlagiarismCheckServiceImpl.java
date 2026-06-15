package com.aicourse.service.impl;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.PlagiarismMatch;
import com.aicourse.entity.Submission;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.PlagiarismCheckMapper;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.PlagiarismCheckService;
import com.aicourse.service.PlagiarismMatchService;
import com.aicourse.service.SubmissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 查重检测服务实现类
 * 使用基于词重叠（Jaccard相似度）的文本比较算法
 */
@Service
public class PlagiarismCheckServiceImpl extends ServiceImpl<PlagiarismCheckMapper, PlagiarismCheck> implements PlagiarismCheckService {

    private final SubmissionService submissionService;
    private final SubmissionMapper submissionMapper;
    private final PlagiarismMatchService plagiarismMatchService;

    /** 高相似度判定阈值（默认0.3即30%） */
    private static final BigDecimal HIGH_SIMILARITY_THRESHOLD = new BigDecimal("0.3");

    public PlagiarismCheckServiceImpl(PlagiarismCheckMapper plagiarismCheckMapper,
                                      @Lazy SubmissionService submissionService,
                                      SubmissionMapper submissionMapper,
                                      PlagiarismMatchService plagiarismMatchService) {
        this.baseMapper = plagiarismCheckMapper;
        this.submissionService = submissionService;
        this.submissionMapper = submissionMapper;
        this.plagiarismMatchService = plagiarismMatchService;
    }

    /**
     * 创建查重任务（初始状态为PENDING）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlagiarismCheck createCheck(Long submissionId, String checkType) {
        // 验证提交记录是否存在
        Submission submission = submissionService.getById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }
        if (submission.getContent() == null || submission.getContent().isEmpty()) {
            throw new BusinessException("提交内容为空，无法进行查重");
        }
        // 创建查重任务
        PlagiarismCheck check = new PlagiarismCheck();
        check.setSubmissionId(submissionId);
        check.setTaskId(submission.getTaskId());
        check.setStudentId(submission.getStudentId());
        check.setCheckType(checkType != null ? checkType : "TEXT");
        check.setStatus("PENDING");
        check.setSimilarityScore(BigDecimal.ZERO);
        check.setHighSimilarityCount(0);
        this.save(check);
        return check;
    }

    /**
     * 执行查重检测
     * 算法流程：
     * 1. 获取当前提交内容
     * 2. 获取同任务下其他提交
     * 3. 逐对进行词重叠/Jaccard相似度计算
     * 4. 对超过阈值的匹配创建明细记录
     * 5. 汇总结果并更新状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeCheck(Long checkId) {
        // 获取查重任务
        PlagiarismCheck check = this.getById(checkId);
        if (check == null) {
            throw new BusinessException("查重任务不存在");
        }
        try {
            // 更新状态为检测中
            check.setStatus("CHECKING");
            this.updateById(check);
            // 获取当前提交的内容
            Submission currentSubmission = submissionService.getById(check.getSubmissionId());
            if (currentSubmission == null || currentSubmission.getContent() == null) {
                throw new BusinessException("无法获取提交内容");
            }
            String currentText = currentSubmission.getContent().trim();
            // 获取同任务下其他提交（排除自身）
            LambdaQueryWrapper<Submission> otherWrapper = new LambdaQueryWrapper<Submission>()
                    .eq(Submission::getTaskId, check.getTaskId())
                    .ne(Submission::getId, check.getSubmissionId())
                    .isNotNull(Submission::getContent);
            List<Submission> otherSubmissions = submissionMapper.selectList(otherWrapper);
            // 存储所有匹配结果
            List<Map<String, Object>> allMatches = new ArrayList<>();
            BigDecimal maxSimilarity = BigDecimal.ZERO;
            int highSimCount = 0;
            // 逐对比较
            for (Submission other : otherSubmissions) {
                String otherText = other.getContent().trim();
                if (otherText.isEmpty()) {
                    continue;
                }
                // 计算Jaccard相似度（基于词汇重叠）
                Map<String, Object> compareResult = calculateSimilarity(currentText, otherText);
                BigDecimal similarity = (BigDecimal) compareResult.get("similarity");
                // 更新最高相似度
                if (similarity.compareTo(maxSimilarity) > 0) {
                    maxSimilarity = similarity;
                }
                // 判定是否为高相似度匹配
                if (similarity.compareTo(HIGH_SIMILARITY_THRESHOLD) >= 0) {
                    highSimCount++;
                    // 构建匹配明细
                    Map<String, Object> matchData = new HashMap<>();
                    matchData.put("matchedSubmissionId", other.getId());
                    matchData.put("matchedSource", "学生ID:" + other.getStudentId() + "的提交");
                    matchData.put("similarity", similarity);
                    matchData.put("originalText", compareResult.get("originalFragment"));
                    matchData.put("matchedText", compareResult.get("matchedFragment"));
                    // 根据相似度判断匹配类型
                    String matchType = determineMatchType(similarity);
                    matchData.put("matchType", matchType);
                    allMatches.add(matchData);
                }
            }
            // 批量保存高相似度匹配明细
            if (!allMatches.isEmpty()) {
                plagiarismMatchService.batchSaveMatches(checkId, allMatches);
            }
            // 生成检测结果摘要
            String summary = generateResultSummary(maxSimilarity, highSimCount, otherSubmissions.size());

            // 将详细匹配结果序列化为JSON存入checkDetail字段（含算法版本信息）
            String checkDetailJson = buildCheckDetailJson(allMatches, maxSimilarity, highSimCount, otherSubmissions.size());
            check.setCheckDetail(checkDetailJson);

            // 更新查重任务结果
            check.setSimilarityScore(maxSimilarity.setScale(4, RoundingMode.HALF_UP));
            check.setHighSimilarityCount(highSimCount);
            check.setResultSummary(summary);
            check.setStatus("COMPLETED");
            check.setCheckedAt(LocalDateTime.now());
            this.updateById(check);
        } catch (BusinessException e) {
            // 业务异常直接抛出
            check.setStatus("FAILED");
            check.setErrorMessage(e.getMessage());
            this.updateById(check);
            throw e;
        } catch (Exception e) {
            // 其他异常记录后抛出
            check.setStatus("FAILED");
            check.setErrorMessage("查重执行异常: " + e.getMessage());
            this.updateById(check);
            throw new BusinessException(500, "查重执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取查重结果详情
     */
    @Override
    public Map<String, Object> getCheckResult(Long checkId) {
        PlagiarismCheck check = this.getById(checkId);
        if (check == null) {
            throw new BusinessException("查重任务不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("checkInfo", check);
        result.put("similarityScore", check.getSimilarityScore());
        result.put("highSimilarityCount", check.getHighSimilarityCount());
        result.put("resultSummary", check.getResultSummary());
        result.put("status", check.getStatus());
        // 获取该查重任务的所有匹配明细
        LambdaQueryWrapper<PlagiarismMatch> matchWrapper = new LambdaQueryWrapper<PlagiarismMatch>()
                .eq(PlagiarismMatch::getPlagiarismCheckId, checkId)
                .orderByDesc(PlagiarismMatch::getSimilarity);
        List<PlagiarismMatch> matches = plagiarismMatchService.list(matchWrapper);
        result.put("matches", matches);

        // 解析checkDetail JSON返回结构化数据
        if (check.getCheckDetail() != null && !check.getCheckDetail().isEmpty()) {
            result.put("checkDetailRaw", check.getCheckDetail());
            // 提取算法版本信息
            if (check.getCheckDetail().contains("algorithmVersion")) {
                result.put("algorithmVersion", "Jaccard-v1.0");
            }
        }

        return result;
    }

    /**
     * 获取高相似度匹配列表（超过指定阈值）
     */
    @Override
    public List<PlagiarismMatch> getHighSimilarityMatches(Long checkId, BigDecimal threshold) {
        LambdaQueryWrapper<PlagiarismMatch> queryWrapper = new LambdaQueryWrapper<PlagiarismMatch>()
                .eq(PlagiarismMatch::getPlagiarismCheckId, checkId)
                .ge(PlagiarismMatch::getSimilarity, threshold)
                .orderByDesc(PlagiarismMatch::getSimilarity);
        return plagiarismMatchService.list(queryWrapper);
    }

    /**
     * 获取某提交的所有查重历史记录
     */
    @Override
    public List<PlagiarismCheck> getSubmissionChecks(Long submissionId) {
        LambdaQueryWrapper<PlagiarismCheck> queryWrapper = new LambdaQueryWrapper<PlagiarismCheck>()
                .eq(PlagiarismCheck::getSubmissionId, submissionId)
                .orderByDesc(PlagiarismCheck::getCreatedAt);
        return this.list(queryWrapper);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 计算两段文本之间的Jaccard相似度（基于词汇重叠）
     * Jaccard = |A ∩ B| / |A ∪ B|
     *
     * @param text1 文本一
     * @param text2 文本二
     * @return 包含similarity、originalFragment、matchedFragment的结果Map
     */
    private Map<String, Object> calculateSimilarity(String text1, String text2) {
        // 分词（按空格和标点切分）
        Set<String> words1 = tokenize(text1);
        Set<String> words2 = tokenize(text2);
        // 计算交集和并集
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        // 计算Jaccard相似度
        double similarity = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
        // 提取公共片段用于展示
        String originalFragment = extractCommonFragment(text1, words1, intersection);
        String matchedFragment = extractCommonFragment(text2, words2, intersection);
        Map<String, Object> result = new HashMap<>();
        result.put("similarity", BigDecimal.valueOf(similarity));
        result.put("originalFragment", originalFragment);
        result.put("matchedFragment", matchedFragment);
        return result;
    }

    /**
     * 简单分词：将文本按非字母数字字符切分为单词集合
     */
    private Set<String> tokenize(String text) {
        // 使用正则表达式拆分文本为词语（支持中英文混合）
        String[] tokens = text.split("[\\s\\p{Punct}]+");
        return Arrays.stream(tokens)
                .filter(t -> t.length() > 1)  // 过滤掉单字符
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    /**
     * 从原文中提取包含公共词汇的片段
     */
    private String extractCommonFragment(String fullText, Set<String> allWords, Set<String> commonWords) {
        List<String> commonList = new ArrayList<>(commonWords);
        // 取前几个公共词作为展示片段（最多显示200字符）
        StringBuilder fragment = new StringBuilder();
        int charCount = 0;
        for (String word : commonList) {
            if (fullText.contains(word)) {
                fragment.append(word).append(" ");
                charCount += word.length() + 1;
                if (charCount > 200) {
                    break;
                }
            }
        }
        return fragment.toString().trim();
    }

    /**
     * 根据相似度值判定匹配类型
     */
    private String determineMatchType(BigDecimal similarity) {
        if (similarity.compareTo(new BigDecimal("0.7")) >= 0) {
            return "EXACT";       // 高度疑似整段复制
        } else if (similarity.compareTo(new BigDecimal("0.4")) >= 0) {
            return "PARAPHRASE";  // 中等疑似改写
        } else {
            return "STRUCTURE";   // 低度疑似结构相似
        }
    }

    /**
     * 生成检测结果摘要
     */
    private String generateResultSummary(BigDecimal maxSimilarity, int highSimCount, int totalCompared) {
        StringBuilder sb = new StringBuilder();
        sb.append("本次查重共与").append(totalCompared).append("份其他提交进行了比对。");
        sb.append("最高相似度为").append(maxSimilarity.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)).append("%。");
        if (highSimCount > 0) {
            sb.append("发现").append(highSimCount).append("处高相似度匹配（≥30%），建议重点关注。");
        } else {
            sb.append("未发现明显的高相似度匹配，原创性较好。");
        }
        return sb.toString();
    }

    /**
     * 构建查重详细结果JSON（含算法版本、匹配明细等结构化数据）
     */
    private String buildCheckDetailJson(List<Map<String, Object>> allMatches,
                                         BigDecimal maxSimilarity, int highSimCount, int totalCompared) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        // 算法版本信息
        json.append("\"algorithmVersion\":\"Jaccard-v1.0\",");
        json.append("\"algorithm\":\"Jaccard_Similarity\",");
        json.append("\"checkedAt\":\"").append(LocalDateTime.now().toString()).append("\",");
        // 统计信息
        json.append("\"totalCompared\":").append(totalCompared).append(",");
        json.append("\"maxSimilarity\":").append(maxSimilarity.setScale(4, RoundingMode.HALF_UP)).append(",");
        json.append("\"highSimilarityCount\":").append(highSimCount).append(",");
        // 匹配详情列表
        json.append("\"matches\":[");
        for (int i = 0; i < allMatches.size(); i++) {
            if (i > 0) json.append(",");
            Map<String, Object> match = allMatches.get(i);
            json.append("{");
            json.append("\"matchedSubmissionId\":").append(match.get("matchedSubmissionId")).append(",");
            json.append("\"matchedSource\":\"").append(match.get("matchedSource")).append("\",");
            json.append("\"similarity\":").append(match.get("similarity")).append(",");
            json.append("\"matchType\":\"").append(match.get("matchType")).append("\"");
            json.append("}");
        }
        json.append("]");
        json.append("}");
        return json.toString();
    }
}
