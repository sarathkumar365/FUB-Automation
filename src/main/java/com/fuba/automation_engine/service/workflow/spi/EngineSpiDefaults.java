package com.fuba.automation_engine.service.workflow.spi;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default engine SPI beans, registered only when a host supplies none, so the
 * engine boots standalone. ({@link RunContextContributor} / {@link GraphValidationRule}
 * need no default — an absent bean injects as an empty {@code List}.)
 */
@Configuration
public class EngineSpiDefaults {

    @Bean
    @ConditionalOnMissingBean(TriggerValidator.class)
    public TriggerValidator noopTriggerValidator() {
        return trigger -> List.of();
    }
}
