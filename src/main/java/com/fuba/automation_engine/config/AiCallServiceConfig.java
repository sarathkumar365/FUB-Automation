package com.fuba.automation_engine.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiCallServiceProperties.class)
public class AiCallServiceConfig {
}
