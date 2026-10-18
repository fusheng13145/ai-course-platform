package com.aicourse.service;

import java.util.List;
import java.util.Map;

/**
 * 系统管理业务接口
 */
public interface SystemService {

    /**
     * 获取系统概览数据
     * @return 系统统计信息
     */
    Map<String, Object> getOverview();

    /**
     * 根据配置键获取配置值
     * @param key 配置键名
     * @return 配置值
     */
    String getConfig(String key);

    /**
     * 保存系统配置
     * @param key 配置键名
     * @param value 配置值
     * @param group 配置分组
     */
    void saveConfig(String key, String value, String group);

    /**
     * 根据分组获取配置列表
     * @param group 配置分组
     * @return 分组下所有配置
     */
    List<Map<String, Object>> getConfigs(String group);

    /**
     * 获取系统健康状态
     * @return 健康检测数据
     */
    Map<String, Object> getHealth();

    /**
     * 创建数据备份
     * @return 备份文件名称/标识
     */
    String createBackup();

    /**
     * 获取所有备份记录
     * @return 备份文件列表信息
     */
    List<Map<String, Object>> getBackups();

    /**
     * 执行备份恢复
     * @param fileName 备份文件名
     */
    void restoreBackup(String fileName);

    /**
     * 获取日志文件列表
     * @return 日志文件信息
     */
    List<Map<String, Object>> getLogFiles();

    /**
     * 读取日志文件内容
     * @param fileName 日志文件名
     * @param lines 读取行数
     * @param keyword 过滤关键词
     * @return 日志内容
     */
    String readLogFile(String fileName, int lines, String keyword);

    /**
     * 获取AI功能使用统计
     * @return AI使用统计数据
     */
    Map<String, Object> getAIUsageStats();

    /**
     * 按课程聚合的 AI 使用统计（跨课程 AI 统计，管理端）。
     * <p>数据源为 {@code ai_call_logs}——真实逐次调用留痕，与 {@link #getAIUsageStats()}
     * 的无留痕口径不同。⚠️ 只统计自 2026-10-13 埋点上线后的调用，历史不可回填。</p>
     *
     * @param days 统计天数（1–365，越界自动收敛）
     * @return 总览 + 按课程分组的用量明细
     */
    Map<String, Object> getAIUsageByCourse(int days);

    /**
     * AI 异常监控：汇总最近登录/操作日志，调用 LLM 检测异常（暴力破解、成绩异动等）。
     * 失败时返回包含降级提示的监控结果（不抛异常，与原 HTTP 行为一致）。
     * @return {"alerts":[{level,title,detail}],"loginCount":N,"operationCount":N,"generatedAt":...}
     */
    Map<String, Object> aiMonitor();
}