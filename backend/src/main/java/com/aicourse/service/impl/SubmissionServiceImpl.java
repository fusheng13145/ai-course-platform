package com.aicourse.service.impl;

import com.aicourse.entity.Submission;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.SubmissionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {
}
