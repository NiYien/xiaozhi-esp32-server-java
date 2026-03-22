package com.xiaozhi;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@MapperScan("com.xiaozhi.dao")
public class XiaozhiApplication {

    Logger logger = LoggerFactory.getLogger(XiaozhiApplication.class);

    public static void main(String[] args) {
        // 允许 HttpClient 设置 Connection 等受限头，修复阿里百炼等平台 MCP 连接复用超时问题
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Connection");
        SpringApplication.run(XiaozhiApplication.class, args);
    }
}