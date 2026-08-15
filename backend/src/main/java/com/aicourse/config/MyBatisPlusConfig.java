package com.aicourse.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MyBatis-Plus 全局配置类
 * 目前配置：分页插件
 */
@Configuration
public class MyBatisPlusConfig {

    private static final Logger log = LoggerFactory.getLogger(MyBatisPlusConfig.class);

    /**
     * 注册 MP 插件拦截器
     * 已集成 MySQL 分页插件，解决分页 count、数据截取问题
     * @return MybatisPlusInterceptor
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件：指定数据库类型为 MySQL
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大限制条数（默认无限制，此处设置 1000 条，防止恶意大数据分页）
        paginationInterceptor.setMaxLimit(1000L);
        // 溢出总页数后是否进行处理（默认 false，true 则查询最后一页数据）
        paginationInterceptor.setOverflow(true);

        interceptor.addInnerInterceptor(paginationInterceptor);
        log.info("[MyBatisPlusConfig] MyBatis-Plus 分页插件已注册（数据库类型=MySQL，单页最大 1000 条）");
        return interceptor;
    }
}