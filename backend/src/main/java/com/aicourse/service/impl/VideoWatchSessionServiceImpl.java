package com.aicourse.service.impl;

import com.aicourse.entity.VideoWatchSession;
import com.aicourse.mapper.VideoWatchSessionMapper;
import com.aicourse.service.VideoWatchSessionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class VideoWatchSessionServiceImpl extends ServiceImpl<VideoWatchSessionMapper, VideoWatchSession> implements VideoWatchSessionService {
}
