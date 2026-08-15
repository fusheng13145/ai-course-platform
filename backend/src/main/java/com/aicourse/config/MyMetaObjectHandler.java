package com.aicourse.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器
 * 自动填充创建时间、更新时间
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    private static final Logger log = LoggerFactory.getLogger(MyMetaObjectHandler.class);

    /**
     * 插入数据时自动填充
     * @param metaObject 元数据对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        try {
            // 创建时间
            strictInsertFill(metaObject, "createdAt", LocalDateTime::now, LocalDateTime.class);
            // 更新时间
            strictInsertFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
        } catch (RuntimeException e) {
            log.error("自动填充创建/更新时间失败, metaObject={}", metaObject.getOriginalObject(), e);
            throw e;
        }
    }

    /**
     * 更新数据时自动填充
     * @param metaObject 元数据对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        try {
            // 更新时间
            strictUpdateFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
        } catch (RuntimeException e) {
            log.error("自动填充更新时间失败, metaObject={}", metaObject.getOriginalObject(), e);
            throw e;
        }
    }
}