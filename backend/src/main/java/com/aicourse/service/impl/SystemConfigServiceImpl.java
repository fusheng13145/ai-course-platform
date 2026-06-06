package com.aicourse.service.impl;

import com.aicourse.entity.SystemConfig;
import com.aicourse.mapper.SystemConfigMapper;
import com.aicourse.service.SystemConfigService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig> implements SystemConfigService {
}
