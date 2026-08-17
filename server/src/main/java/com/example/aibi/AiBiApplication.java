package com.example.aibi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiBiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiBiApplication.class, args);
    }
}

