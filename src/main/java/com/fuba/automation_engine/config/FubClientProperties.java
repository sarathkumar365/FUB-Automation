package com.fuba.automation_engine.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "fub")
public class FubClientProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String apiKey;

    @NotBlank
    private String xSystem;

    @NotBlank
    private String xSystemKey;
}

