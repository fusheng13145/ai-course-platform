package com.aicourse.service;

import java.util.List;
import java.util.Map;

/**
 * 基于知识图谱结构的学习推荐服务。
 * <p>
 * 与 {@link AIService#recommendLearningWithContext} 的 LLM 解释层不同，
 * 本服务提供纯算法的推荐依据（图谱关系驱动的关联推荐 + 掌握度加权排序）：
 * <ol>
 *   <li><b>薄弱点关联推荐</b>：从学生薄弱知识点出发，沿图谱关系找到前置依赖与相关补充知识；</li>
 *   <li><b>下一步学习推荐</b>：从学生已掌握知识点出发，沿图谱关系推荐下一个应学的知识；</li>
 *   <li><b>难度梯度推荐</b>：按知识点难度等级 + 学生掌握度生成递进式学习路径。</li>
 * </ol>
 * 所有推荐结果均可作为 {@code recommendLearningWithContext} 的输入上下文，
 * 实现「数据驱动 + LLM 解释」的推荐闭环。
 */
public interface KnowledgeRecommendationService {

    /**
     * 基于薄弱知识点的图谱关联推荐。
     * 对每个薄弱知识点，查找其在知识图谱中的前置依赖与兄弟关联，
     * 按关联强度与掌握度加权排序，返回推荐列表。
     *
     * @param courseId  课程 ID
     * @param weakPoints 薄弱知识点（name → mastery 0~100）
     * @param limit      返回结果上限
     * @return 推荐列表：每个条目含知识点名称、推荐类型（前置复习/关联补充）、推荐权重
     */
    List<Map<String, Object>> recommendFromWeakPoints(Long courseId,
                                                      Map<String, Double> weakPoints,
                                                      int limit);

    /**
     * 基于已掌握知识点的下一步学习推荐。
     * 从学生已掌握/部分掌握的知识点出发，沿图谱关系找到下一个应学的知识点。
     *
     * @param courseId       课程 ID
     * @param masteredPoints 已掌握知识点（name → mastery 0~100）
     * @param limit          返回结果上限
     * @return 推荐列表
     */
    List<Map<String, Object>> recommendNextSteps(Long courseId,
                                                  Map<String, Double> masteredPoints,
                                                  int limit);

    /**
     * 综合推荐：结合薄弱点修复 + 下一步学习 + 难度梯度，
     * 生成一个排序后的推荐清单，可直接用于 LLM 解释或前端展示。
     *
     * @param courseId       课程 ID
     * @param weakPoints     薄弱知识点
     * @param masteredPoints 已掌握知识点
     * @param limit          返回结果上限
     * @return 综合推荐列表
     */
    List<Map<String, Object>> recommendComprehensive(Long courseId,
                                                      Map<String, Double> weakPoints,
                                                      Map<String, Double> masteredPoints,
                                                      int limit);
}
