package com.aicourse.mapper;

import com.aicourse.entity.FileUploadSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * FileUploadSession 数据访问 Mapper 接口
 * 继承自 MyBatis-Plus 的 {@code BaseMapper}，提供该实体的基础 CRUD 操作，未定义额外自定义方法。
 */
@Mapper
public interface FileUploadSessionMapper extends BaseMapper<FileUploadSession> {
}
