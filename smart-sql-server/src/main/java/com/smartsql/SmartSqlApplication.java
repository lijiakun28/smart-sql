package com.smartsql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智能SQL - Java 中间层启动类
 */
@EnableScheduling
@SpringBootApplication
public class SmartSqlApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartSqlApplication.class, args);
    }
}
