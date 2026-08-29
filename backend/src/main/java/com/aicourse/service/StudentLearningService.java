package com.aicourse.service;

import java.util.List;
import java.util.Map;

/**
 * 学生端学习核心服务
 * <p>
 * 对标超星学习通的学生核心模块，聚合：
 * 1) 学生首页/学习空间驾驶舱（课程+待办+消息+学情）
 * 2) 章节任务点学习中心（完整章节树 + 任务点 + 完成态 + 闯关解锁）
 * 3) 任务点完成态（视频续播/作业提交/测验即时判分）
 * 4) 成绩总览
 * <p>
 * 设计纪律（沿用教师端重构）：
 * - 聚合结果不进 Redis 缓存（规避泛型擦除/序列化陷阱）。
 * - 每个子聚合独立 try/catch 兜底，保证驾驶舱永不整体空白。
 */
public interface StudentLearningService {

    /**
     * 学生首页/学习空间聚合驾驶舱
     * @param userId 当前学生ID
     * @return 聚合 Map：courses / todos(assignments,exams,signIns) / notifications / learningStats / analytics / summary
     */
    Map<String, Object> getStudentDashboard(Long userId);

    /**
     * 课程章节任务点学习中心（含闯关解锁态）
     * @param userId 学生ID
     * @param courseId 课程ID
     * @return 章节树（含任务点列表、完成态、解锁态）+ 整体完成统计
     */
    Map<String, Object> getCourseLearningCenter(Long userId, Long courseId);

    /**
     * 课程各章节任务点完成度统计（X/Y）
     * @param userId 学生ID
     * @param courseId 课程ID
     */
    Map<String, Object> getCourseProgressSummary(Long userId, Long courseId);

    /**
     * 标记某任务点完成（视频/文档/阅读/测验非提交类）
     * @return 最新任务点状态 {taskId, completed, completionType}
     */
    Map<String, Object> markTaskComplete(Long userId, Long courseId, Long taskId, String completionType);

    /**
     * 获取视频任务点续播状态
     * @return {positionSeconds, watchedDuration, totalDuration, completed}
     */
    Map<String, Object> getVideoState(Long userId, Long courseId, Long taskId);

    /**
     * 上报视频观看心跳（累计观看时长 + 当前位置），达到阈值自动判定完成。
     * <p>
     * 视频进度唯一真相源收口（#65）：优先以 resourceId 委托 {@code VideoLearningRecordService}
     * 进行「有效观看时长 + 防拖拽」累计（P0-5 已实现的完整性逻辑），再将其结果回写
     * {@code task_completions[VIDEO]}，使学习中心统计/闯关解锁以 task_completions 为唯一真相源，
     * 同时与细粒度 VideoLearningRecord 保持一致（两表打通）。resourceId 缺省时退化为
     * 基于 task_completions 自身存储状态的反拖拽累计，仍具备基础完整性。
     *
     * @param resourceId     视频资源ID（可选，提供则启用权威累计器）
     * @param playbackSpeed  播放倍速（可选）
     * @return 更新后的视频状态
     */
    Map<String, Object> reportVideoHeartbeat(Long userId, Long courseId, Long taskId, Long resourceId,
                                             Integer position, Integer watchedDuration,
                                             Integer totalDuration, Double playbackSpeed);

    /**
     * 测验任务点即时判分
     * <p>
     * 统一收口原始表单解析：由服务层将题目ID字符串键转为 {@code Long}（非法键跳过并告警），
     * 控制器不再手写 Map 解析逻辑。
     *
     * @param answers 原始表单：题目ID(字符串) -> 学生作答
     * @return {total, correct, score, details:[{questionId, correct, correctAnswer, explanation}]}
     */
    Map<String, Object> submitQuiz(Long userId, Long courseId, Long taskId, Map<String, String> answers);

    /**
     * 学生成绩总览（按课程聚合作业/考试/总评）
     */
    Map<String, Object> getGrades(Long userId);

    /**
     * 学生端学情可视化数据（D7 增强，供 Dashboard 图表渲染）。
     * 纯聚合查询、不落缓存、子项独立兜底：
     * <ul>
     *   <li>{@code durationTrend}：近 14 天学习时长趋势（learning_records.duration 按日聚合）；</li>
     *   <li>{@code radarData}：课程薄弱度雷达（wrong_questions 按课程统计未掌握率，取前 6）；</li>
     *   <li>{@code heatmapData}：课程 × 近 7 天学习活跃热力图（learning_records 按课程×日期聚合时长）。</li>
     * </ul>
     * @param userId 当前学生ID
     * @return 上述三组数据的聚合 Map
     */
    Map<String, Object> getVisualizationData(Long userId);
}
