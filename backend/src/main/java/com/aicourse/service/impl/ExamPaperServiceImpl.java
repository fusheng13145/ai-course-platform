package com.aicourse.service.impl;

import com.aicourse.entity.ExamPaper;
import com.aicourse.mapper.ExamPaperMapper;
import com.aicourse.service.ExamPaperService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ExamPaperServiceImpl extends ServiceImpl<ExamPaperMapper, ExamPaper> implements ExamPaperService {
}
