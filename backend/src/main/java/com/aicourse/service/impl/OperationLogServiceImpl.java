package com.aicourse.service.impl;

import com.aicourse.entity.OperationLog;
import com.aicourse.mapper.OperationLogMapper;
import com.aicourse.service.OperationLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {
}
