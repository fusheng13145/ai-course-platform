package com.aicourse.controller;

import jakarta.validation.Valid;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.LearningRecord;
import com.aicourse.service.LearningRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 学习记录接口控制器
 */
@RestController
@RequestMapping("/learning-records")
@Tag(name = "学习记录")
public class LearningRecordController {

    private final LearningRecordService learningRecordService;

    public LearningRecordController(LearningRecordService learningRecordService) {
        this.learningRecordService = learningRecordService;
    }

    /**
     * 新增学习记录（学生/管理员权限）
     * @param record 学习记录信息
     * @param auth 认证信息
     * @return 新增记录数据
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ApiResponse<LearningRecord> record(@Valid @RequestBody LearningRecord record, Authentication auth) {
        record.setUserId((Long) auth.getPrincipal());
        learningRecordService.save(record);
        return ApiResponse.success("记录成功", record);
    }

    /**
     * 查询当前用户的所有学习记录
     * @param auth 认证信息
     * @return 学习记录列表
     */
    @GetMapping
    public ApiResponse<List<LearningRecord>> myRecords(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<LearningRecord> queryWrapper = new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId);
        List<LearningRecord> recordList = learningRecordService.list(queryWrapper);
        return ApiResponse.success(recordList);
    }

    /**
     * 获取当前用户学习统计数据
     * @param auth 认证信息
     * @return 统计数据
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Map<String, Object> stats = learningRecordService.getLearningStats(userId);
        return ApiResponse.success(stats);
    }

    /**
     * 获取当前用户学习趋势数据
     * @param auth 认证信息
     * @param days 天数（默认7天）
     * @return 趋势数据列表
     */
    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> getTrend(
            Authentication auth,
            @RequestParam(defaultValue = "7") int days) {
        Long userId = (Long) auth.getPrincipal();
        List<Map<String, Object>> trend = learningRecordService.getLearningTrend(userId, days);
        return ApiResponse.success(trend);
    }

    /**
     * 获取当前用户月度学习数据（用于日历）
     * @param auth 认证信息
     * @param year 年份
     * @param month 月份
     * @return 每日学习数据
     */
    @GetMapping("/calendar")
    public ApiResponse<List<Map<String, Object>>> getCalendarData(
            Authentication auth,
            @RequestParam int year,
            @RequestParam int month) {
        Long userId = (Long) auth.getPrincipal();
        List<Map<String, Object>> calendarData = learningRecordService.getMonthlyLearningData(userId, year, month);
        return ApiResponse.success(calendarData);
    }
}