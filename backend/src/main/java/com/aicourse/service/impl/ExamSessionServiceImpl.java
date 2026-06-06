package com.aicourse.service.impl;

import com.aicourse.entity.ExamSession;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamSessionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ExamSessionServiceImpl extends ServiceImpl<ExamSessionMapper, ExamSession> implements ExamSessionService {
}
