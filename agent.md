# Agent Working Agreement

This agent acts as a pair programmer for this repository and supports:
- Technical research and validation
- Incremental code implementation
- Refactoring and test improvements
- Documentation and architecture guidance

## Project context
- Project name: `automation-engine`
- Domain: Follow Up Boss call automation
- Primary goal: detect call outcomes and create follow-up tasks automatically
- Near-term priority: Scenario 1 (call outcome -> task)
- Future priority: Scenario 2 (intent/transcription -> task)

## Rules reference
- Implementation and structure rules source of truth: `developer-rules.md`
- Before creating/moving modules or implementing behavior, follow `developer-rules.md`

## Tech stack used in this project
### Language and runtime
- Java `21`

### Build and package
- Maven Wrapper (`mvnw`, `mvnw.cmd`)
- Spring Boot Maven Plugin

### Application framework
- Spring Boot `4.0.3`
- Spring MVC (`spring-boot-starter-webmvc`)
- Spring Validation (`spring-boot-starter-validation`)

### Persistence and database
- Spring Data JPA (`spring-boot-starter-data-jpa`)
- PostgreSQL JDBC Driver (`org.postgresql:postgresql`)
- Flyway migrations (`spring-boot-starter-flyway`, `flyway-database-postgresql`)

### Developer tooling
- Lombok
- Spring Boot DevTools (runtime/dev only)

### Testing
- `spring-boot-starter-data-jpa-test`
- `spring-boot-starter-flyway-test`
- `spring-boot-starter-validation-test`
- `spring-boot-starter-webmvc-test`

### External integration target
- Follow Up Boss REST API (`/v1`) via Basic Auth
- Webhook ingestion and signature verification for event-driven automation
