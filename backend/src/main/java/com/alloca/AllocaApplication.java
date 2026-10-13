package com.alloca;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AllocaApplication {
    public static void main(String[] args) {
        SpringApplication.run(AllocaApplication.class, args);
    }
}
