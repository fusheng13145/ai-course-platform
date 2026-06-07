package com.aicourse.service.impl;

import com.aicourse.entity.PlagiarismMatch;
import com.aicourse.mapper.PlagiarismMatchMapper;
import com.aicourse.service.PlagiarismMatchService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class PlagiarismMatchServiceImpl extends ServiceImpl<PlagiarismMatchMapper, PlagiarismMatch> implements PlagiarismMatchService {
}
