package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Semester;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.SemesterMapper;
import com.aicourse.service.SemesterService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link SemesterService} 实现。
 *
 * <p>三条口径（与控制器提示文案、手册一致）：
 * ① 学期名做唯一性校验并以表上的 {@code uk_semesters_name} 为第二道闸（并发下仍会被数据库拦）；
 * ② 归档是**幂等**的——重复归档只返回 false，不做二次写入（避免把原归档时间覆盖成"今天"）；
 * ③ 批量归档只扫 {@code ACTIVE 且 end_date < today}，不动未过期与已归档的行。</p>
 */
@Service
public class SemesterServiceImpl extends ServiceImpl<SemesterMapper, Semester> implements SemesterService {

    /** 在册状态。 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 已归档状态。 */
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    @Override
    public List<Semester> listByStatus(String status) {
        LambdaQueryWrapper<Semester> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq(Semester::getStatus, status.trim().toUpperCase());
        }
        // 'ACTIVE' < 'ARCHIVED'（第三字符 C < R），故升序即"在册在前"；同组内按开始日期倒序
        qw.orderByAsc(Semester::getStatus).orderByDesc(Semester::getStartDate);
        return this.list(qw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Semester create(String name, LocalDate startDate, LocalDate endDate) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "学期名称不能为空");
        }
        if (startDate == null || endDate == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "学期起止日期不能为空");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        long exists = this.count(new LambdaQueryWrapper<Semester>().eq(Semester::getName, trimmed));
        if (exists > 0) {
            throw new BusinessException(ApiCode.CONFLICT, "学期名称已存在：" + trimmed);
        }
        Semester semester = new Semester();
        semester.setName(trimmed);
        semester.setStartDate(startDate);
        semester.setEndDate(endDate);
        semester.setStatus(STATUS_ACTIVE);
        this.save(semester);
        return semester;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean archive(Long id) {
        Semester semester = this.getById(id);
        if (semester == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "学期不存在：" + id);
        }
        if (STATUS_ARCHIVED.equals(semester.getStatus())) {
            // 幂等：已归档不重复写（重复归档若刷新 archivedAt，会把"首次归档时间"覆盖掉）
            return false;
        }
        semester.setStatus(STATUS_ARCHIVED);
        semester.setArchivedAt(LocalDateTime.now());
        this.updateById(semester);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int archiveExpired(LocalDate today) {
        LocalDate base = today != null ? today : LocalDate.now();
        LambdaUpdateWrapper<Semester> uw = new LambdaUpdateWrapper<Semester>()
                .eq(Semester::getStatus, STATUS_ACTIVE)
                .lt(Semester::getEndDate, base)
                .set(Semester::getStatus, STATUS_ARCHIVED)
                .set(Semester::getArchivedAt, LocalDateTime.now());
        // 单条 UPDATE 由数据库完成筛选与改写，避免"先查后逐条改"在并发下漏行
        return this.getBaseMapper().update(null, uw);
    }
}
