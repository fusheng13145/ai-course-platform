package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.LearningRecord;
import com.aicourse.service.LearningRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学习记录接口控制器
 */
@RestController
@RequestMapping("/learning-records")
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
    public ApiResponse<LearningRecord> record(@RequestBody LearningRecord record, Authentication auth) {
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
}