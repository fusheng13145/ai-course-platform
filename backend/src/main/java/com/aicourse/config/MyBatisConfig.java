package com.aicourse.config;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus Mapper 扫描配置。
 *
 * <p>单独抽离自 {@code Application}，避免在 {@code @WebMvcTest} 切片测试中触发 Mapper 接口
 * 的 Bean 注册（Mapper 需要 sqlSessionFactory，而 web 切片不加载数据源，会导致上下文加载失败）。
 * 运行时该配置仍随组件扫描正常生效。
 */
@Configuration
@MapperScan("com.aicourse.mapper")
public class MyBatisConfig {

    private static final Logger log = LoggerFactory.getLogger(MyBatisConfig.class);

    public MyBatisConfig() {
        log.debug("MyBatisConfig 已加载，Mapper 扫描包: com.aicourse.mapper");
    }
}
