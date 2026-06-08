package com.aicourse.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器
 * 自动填充创建时间、更新时间
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入数据时自动填充
     * @param metaObject 元数据对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 创建时间
        strictInsertFill(metaObject, "createdAt", LocalDateTime::now, LocalDateTime.class);
        // 更新时间
        strictInsertFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
    }

    /**
     * 更新数据时自动填充
     * @param metaObject 元数据对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新时间
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
    }
}