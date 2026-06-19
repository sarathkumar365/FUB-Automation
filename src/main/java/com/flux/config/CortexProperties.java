package com.flux.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "cortex")
public class CortexProperties {

    private String baseUrl = "";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
    private String localSafeToNumber = "";
}
