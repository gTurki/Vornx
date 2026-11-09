package com.example.vornx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VornxApplication {

    public static void main(String[] args) {
        SpringApplication.run(VornxApplication.class, args);
    }

}
