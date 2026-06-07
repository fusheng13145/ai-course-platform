package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class KnowledgePointServiceImpl extends ServiceImpl<KnowledgePointMapper, KnowledgePoint> implements KnowledgePointService {
}
