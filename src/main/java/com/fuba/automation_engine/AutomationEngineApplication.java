package com.fuba.automation_engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AutomationEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutomationEngineApplication.class, args);
    }
}
