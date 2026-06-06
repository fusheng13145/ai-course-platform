package com.aicourse.service.impl;

import com.aicourse.entity.WrongQuestion;
import com.aicourse.mapper.WrongQuestionMapper;
import com.aicourse.service.WrongQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class WrongQuestionServiceImpl extends ServiceImpl<WrongQuestionMapper, WrongQuestion> implements WrongQuestionService {
}
