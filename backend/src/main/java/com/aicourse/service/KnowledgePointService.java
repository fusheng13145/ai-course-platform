package com.aicourse.service;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.vo.KnowledgeTreeNodeVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 知识点服务接口
 * 提供知识树构建、父子关系查询、前置依赖、掌握度计算等功能
 */
public interface KnowledgePointService extends IService<KnowledgePoint> {

    /**
     * 获取课程的完整知识树（层级结构，递归父子关系）
     * @param courseId 课程ID
     * @return 树形结构的知识点列表
     */
    List<KnowledgeTreeNodeVO> getKnowledgeTree(Long courseId);

    /**
     * 获取某知识点的直接子节点
     * @param parentId 父知识点ID
     * @return 子知识点列表（按orderIndex排序）
     */
    List<KnowledgePoint> getChildPoints(Long parentId);

    /**
     * 获取知识点的前置依赖（relationType=PREREQUISITE）
     * @param pointId 知识点ID
     * @return 前置依赖知识点列表
     */
    List<KnowledgePoint> getPrerequisites(Long pointId);

    /**
     * 获取知识点的后续知识点（被谁依赖）
     * @param pointId 知识点ID
     * @return 后续知识点列表
     */
    List<KnowledgePoint> getSubsequentPoints(Long pointId);

    /**
     * 计算知识点掌握度（基于错题本和考试成绩）
     * @param pointId 知识点ID
     * @param studentId 学生ID
     * @return 掌握度百分比（0-100）
     */
    BigDecimal calculateMastery(Long pointId, Long studentId);

    /**
     * 获取课程的根级知识点（parentId为null的）
     * @param courseId 课程ID
     * @return 根级知识点列表
     */
    List<KnowledgePoint> getRootPoints(Long courseId);

    /**
     * 创建知识点（校验difficultyLevel和importance的取值范围）
     * @param knowledgePoint 知识点实体（需包含courseId、name等必填字段）
     * @return 创建后的知识点
     */
    KnowledgePoint createWithValidation(KnowledgePoint knowledgePoint);

    /**
     * 获取知识点列表（支持按难度等级和重要度排序/筛选）
     * @param courseId 课程ID
     * @param difficultyLevel 难度等级筛选（可选，如"EASY"/"MEDIUM"/"HARD"）
     * @param minImportance 最小重要度（可选）
     * @param maxImportance 最大重要度（可选）
     * @param sortBy 排序字段（可选：importance/difficultyLevel/orderIndex）
     * @param isAsc 是否升序
     * @return 筛选排序后的知识点列表
     */
    List<KnowledgePoint> getListWithFilters(Long courseId, String difficultyLevel,
                                              Integer minImportance, Integer maxImportance,
                                              String sortBy, boolean isAsc);

    /**
     * 统计某课程下的知识点数量（用于图谱摄取/子图的本地降级统计）。
     * @param courseId 课程ID
     * @return 知识点数量
     */
    long countByCourse(Long courseId);

    /**
     * 获取某课程下的全部知识点（用于图谱子图的本地降级）。
     * @param courseId 课程ID
     * @return 知识点列表（按 orderIndex 升序）
     */
    List<KnowledgePoint> listByCourse(Long courseId);

    /**
     * AI 抽取结果落库（2026-11-14 P2-3 下沉自 KnowledgeController，事务化）：
     * 解析 LLM 抽取的结构化 JSON，幂等创建课程知识点（同名跳过）与关系，节点/关系
     * 逐条容错。控制器只做 AI 调用与参数校验，不再承载解析与逐条落库。
     *
     * @param courseId 课程ID
     * @param rawJson  LLM 返回的原始文本（可为栅栏包裹；解析失败返回 parseError 提示而非抛错）
     * @return {nodeCount, nodes, relationCount, relations[, parseError]}
     */
    java.util.Map<String, Object> extractAndSaveGraph(Long courseId, String rawJson);

    /**
     * 学生知识点掌握度快照（2026-11-14 P2-1，红线②：薄弱点诊断必须基于图谱）。
     * <p>按课程批量计算知识点掌握度（与 {@link #calculateMastery} 同公式：题库按知识点
     * 名称精确关联 + 错题本已掌握占比），批量查询避免逐知识点 3 次 SQL 的放大。
     * 结果按掌握度升序、截取最薄弱 {@code limit} 条。</p>
     *
     * @param studentId 学生ID
     * @param courseIds 课程ID集合（已在调用方按选课过滤；空集合返回空列表）
     * @param limit     返回条数上限
     * @return [{courseId, kpId, name, mastery}]，mastery 为 0-100
     */
    java.util.List<java.util.Map<String, Object>> masterySnapshot(Long studentId, java.util.List<Long> courseIds, int limit);
}
