package com.aleruan.dealos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DealOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DealOsApplication.class, args);
    }
}
