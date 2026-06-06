package com.aicourse.service.impl;

import com.aicourse.entity.FileUploadSession;
import com.aicourse.mapper.FileUploadSessionMapper;
import com.aicourse.service.FileUploadSessionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class FileUploadSessionServiceImpl extends ServiceImpl<FileUploadSessionMapper, FileUploadSession> implements FileUploadSessionService {
}
