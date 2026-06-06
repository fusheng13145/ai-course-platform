package com.aicourse.service.impl;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.mapper.PlagiarismCheckMapper;
import com.aicourse.service.PlagiarismCheckService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class PlagiarismCheckServiceImpl extends ServiceImpl<PlagiarismCheckMapper, PlagiarismCheck> implements PlagiarismCheckService {
}
