package com.aicourse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.aicourse.flowcontrol.SentinelProperties;

/**
 * AI智能课程平台后端 启动类
 *
 * @author leyon
 */
@EnableAsync
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(SentinelProperties.class)
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}