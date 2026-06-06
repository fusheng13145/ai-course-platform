package com.aicourse.service.impl;

import com.aicourse.entity.LearningPath;
import com.aicourse.mapper.LearningPathMapper;
import com.aicourse.service.LearningPathService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class LearningPathServiceImpl extends ServiceImpl<LearningPathMapper, LearningPath> implements LearningPathService {
}
