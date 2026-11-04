package com.aicourse.service;

import java.util.List;
import java.util.Map;

/**
 * 课程教学核心服务
 * <p>
 * 对标超星学习通教师端核心模块（课程建设 / 学情统计 / 章节任务点），
 * 并引入 AI 建课、AI 学情解读等超越能力。本服务聚合课程、章节、选课、
 * 作业、考试、题库、学习记录等多维数据，为教师端「课程驾驶舱」与
 * 「教师仪表盘」提供一站式数据支撑。
 * </p>
 */
public interface TeacherCourseService {

    /**
     * 单课程教学驾驶舱（超星「课程门户 / 教学进度」对齐升级版）
     *
     * @param courseId   课程ID
     * @param operatorId 操作者ID（用于归属校验与 AI 上下文）
     * @return 聚合数据：概览 / 内容量 / 学习指标 / 待批改 / 14天趋势 / 预警学生 / 章节树 / AI解读
     */
    Map<String, Object> getCourseTeachingDashboard(Long courseId, Long operatorId);

    /**
     * AI 智能建课大纲（超越超星：输入主题即可生成章节 + 任务点建议）
     *
     * @param topic        课程主题
     * @param category     课程分类（可选）
     * @param level        难度等级（可选，如 入门/进阶/高级）
     * @param chapterCount 建议章节数（默认 6）
     * @param operatorId   操作者ID
     * @return 结构化大纲：title / description / chapters[]
     */
    Map<String, Object> generateCourseOutlineByAI(String topic, String category, String level,
                                                  int chapterCount, Long operatorId);

    /**
     * 课程克隆（超星「复用课程 / 建课模板」对齐版）：复制课程元信息与章节结构
     *
     * @param sourceCourseId 源课程ID
     * @param newName        新课程名称
     * @param operatorId     操作者ID（新课程的授课教师）
     * @return 新课程ID与克隆章节数
     */
    Map<String, Object> cloneCourse(Long sourceCourseId, String newName, Long operatorId);

    /**
     * 我的课程聚合指标（教师「课程列表」卡片化）：每门课程带学生数/平均进度/完成率/待批改
     *
     * @param teacherId 教师ID
     * @return 课程摘要列表
     */
    List<Map<String, Object>> getMyCourseSummaries(Long teacherId);

    /**
     * 增强版教师仪表盘（超星「学情统计」超越版）
     * 在基础统计之上补充：课程下钻卡片、跨课程预警学生、任务点完成概览、AI 教学总览、平台均值对比
     *
     * @param teacherId 教师ID
     * @return 增强仪表盘数据
     */
    Map<String, Object> getTeacherDashboard(Long teacherId);

    /**
     * 完整章节树（超星「章节任务点」对齐版）：返回带父子嵌套、内容、发布状态的章节树
     *
     * @param courseId 课程ID
     * @return 嵌套章节树
     */
    List<Map<String, Object>> getFullChapterTree(Long courseId);

    /**
     * 课程关联班级列表（用于教师端课程驾驶舱班级维度切换）。
     * 按选课学生所归属的班级聚合，返回每个班级的学生数与待批改提交数。
     *
     * @param courseId 课程ID
     * @return 班级聚合列表（key / name / code / students / pending / schedule）
     */
    List<Map<String, Object>> getCourseClasses(Long courseId);

    /**
     * 由选课记录集合计算「掉队预警」学生列表（进度＜40% 或 成绩＜60）
     * <p>供全校/院系/班级/课程多维聚合复用，避免重复判定逻辑。</p>
     *
     * @param enrollments 选课记录集合
     * @return 预警学生列表（含 studentId / studentName / progress / finalScore / level）
     */
    List<Map<String, Object>> computeAtRisk(List<com.aicourse.entity.CourseEnrollment> enrollments);
}
