package com.aicourse.service.impl;

import com.aicourse.entity.LearningRecord;
import com.aicourse.mapper.LearningRecordMapper;
import com.aicourse.service.LearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements LearningRecordService {
}
