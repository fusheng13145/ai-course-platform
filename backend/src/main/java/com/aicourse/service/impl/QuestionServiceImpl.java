package com.aicourse.service.impl;

import com.aicourse.entity.Question;
import com.aicourse.mapper.QuestionMapper;
import com.aicourse.service.QuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {
}
