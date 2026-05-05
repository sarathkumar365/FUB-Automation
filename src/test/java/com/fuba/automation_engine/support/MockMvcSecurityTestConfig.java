package com.fuba.automation_engine.support;

import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

/**
 * Restores the Spring-Security ↔ MockMvc bridge that Spring Boot 4 stopped auto-applying.
 *
 * <p>Without this bean, `@WithMockUser` writes to {@code TestSecurityContextHolder} but the
 * MockMvc-driven request never reads from it, so admin endpoints respond with 401 even
 * when a test class is annotated `@WithMockUser(roles = "ADMIN")`. The
 * {@code SecurityMockMvcConfigurers.springSecurity()} configurer wires that bridge for
 * every `@AutoConfigureMockMvc` `MockMvc` instance.
 *
 * <p>The class is auto-discovered by `@SpringBootTest` because it is in the test
 * classpath under the application's base package. There is no need to import it
 * explicitly per test.
 */
@Configuration
public class MockMvcSecurityTestConfig {

    @Bean
    MockMvcBuilderCustomizer securityMockMvcBuilderCustomizer() {
        return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
    }
}
