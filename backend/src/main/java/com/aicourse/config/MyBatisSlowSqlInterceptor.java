package com.aicourse.config;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * MyBatis 慢 SQL 监控拦截器
 * <p>
 * 统计每条 SQL 执行耗时，超过阈值（{@code app.mybatis.slow-sql-threshold-ms}，默认 500ms，
 * 配置 ≤0 关闭）时打印 WARN 日志（含 SQL 与参数），作为全表扫描/缺索引/大数据量查询的运维抓手。
 * 与 OperationLog 审计互补：审计记录"谁做了什么"，本拦截器暴露"哪条 SQL 慢"。
 * <p>
 * 作为实现 {@link Interceptor} 的 Spring Bean，由 mybatis-spring-boot-starter 自动注册到
 * SqlSessionFactory（与 MybatisPlusInterceptor 分页插件并存，本拦截器更外层先执行）。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class MyBatisSlowSqlInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(MyBatisSlowSqlInterceptor.class);

    @Value("${app.mybatis.slow-sql-threshold-ms:500}")
    private long slowSqlThresholdMs;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (slowSqlThresholdMs <= 0) {
            return invocation.proceed();
        }
        long start = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000;
            if (costMs >= slowSqlThresholdMs) {
                log.warn("[slow-sql] {} ms 超过阈值 {} ms | {}",
                        costMs, slowSqlThresholdMs, buildSqlDesc(invocation));
            }
        }
    }

    private String buildSqlDesc(Invocation invocation) {
        try {
            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            BoundSql boundSql;
            if (args.length > 5 && args[5] instanceof BoundSql) {
                boundSql = (BoundSql) args[5];
            } else {
                Object param = args[1];
                boundSql = ms.getBoundSql(param);
            }
            String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
            if (sql.length() > 600) {
                sql = sql.substring(0, 600) + "...";
            }
            return ms.getId() + " | " + sql;
        } catch (Exception e) {
            return "（SQL 描述解析失败: " + e.getMessage() + "）";
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 无自定义属性
    }
}
