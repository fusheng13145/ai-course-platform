package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.LearningRecord;
import com.aicourse.service.LearningRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/learning-records")
public class LearningRecordController {
    private final LearningRecordService learningRecordService;

    public LearningRecordController(LearningRecordService learningRecordService) {
        this.learningRecordService = learningRecordService;
    }

    @PostMapping
    public ApiResponse<LearningRecord> record(@RequestBody LearningRecord record, Authentication auth) {
        record.setUserId((Long) auth.getPrincipal());
        learningRecordService.save(record);
        return ApiResponse.success("记录成功", record);
    }

    @GetMapping
    public ApiResponse<List<LearningRecord>> myRecords(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(learningRecordService.list(
            new LambdaQueryWrapper<LearningRecord>().eq(LearningRecord::getUserId, userId)));
    }
}
