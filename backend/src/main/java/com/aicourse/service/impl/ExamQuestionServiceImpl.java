package com.aicourse.service.impl;

import com.aicourse.entity.ExamQuestion;
import com.aicourse.mapper.ExamQuestionMapper;
import com.aicourse.service.ExamQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ExamQuestionServiceImpl extends ServiceImpl<ExamQuestionMapper, ExamQuestion> implements ExamQuestionService {
}
