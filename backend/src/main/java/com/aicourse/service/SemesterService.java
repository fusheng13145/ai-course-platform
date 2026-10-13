package com.aicourse.service;

import com.aicourse.entity.Semester;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.List;

/**
 * 学期服务：角色矩阵「① 创建学期 / ② 归档过期学期」的业务入口。
 *
 * <p>⚠️ 如实边界：学期表与 {@code courses} 的绑定刻意未建立（`courses.semester` 仍是自由文本），
 * 因此本服务只负责学期这一治理维度本身，不改变任何课程归属。</p>
 */
public interface SemesterService extends IService<Semester> {

    /**
     * 列出学期；可按状态过滤。
     *
     * @param status ACTIVE / ARCHIVED（空或 null 表示全部），大小写不敏感
     * @return 在册在前、其后按开始日期倒序
     */
    List<Semester> listByStatus(String status);

    /**
     * 创建学期（学期名唯一）。
     *
     * @param name      学期名称
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 新建的学期
     */
    Semester create(String name, LocalDate startDate, LocalDate endDate);

    /**
     * 归档指定学期（幂等：已归档再次调用不重复写、返回 false）。
     *
     * @param id 学期ID
     * @return true=本次发生状态变更；false=已处于归档态
     */
    boolean archive(Long id);

    /**
     * 批量归档「已过期」的在册学期（结束日期早于 {@code today}）。
     *
     * @param today 判定基准日（null 时取系统当天）
     * @return 本次归档的条数
     */
    int archiveExpired(LocalDate today);
}
