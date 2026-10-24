package com.blob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 博客系统后端启动类。
 */
@EnableScheduling
@ConfigurationPropertiesScan("com.blob")
@SpringBootApplication
public class BlobApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlobApplication.class, args);
    }
}
