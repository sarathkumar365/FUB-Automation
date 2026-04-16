# Java Test Suite Basics (Spring Boot + Maven + JUnit + Testcontainers)

This note explains how tests work in this repository and in a typical Java Spring Boot stack.

## 1) What runs tests

- Command: `./mvnw test`
- Maven uses the Surefire plugin to discover and run test classes.
- JUnit 5 is the test framework used for test methods and assertions.

You do not manually start the app before tests. Test execution starts what it needs inside the test process.

## 2) Common test levels

### Unit tests
- Scope: one class or method.
- Dependencies are mocked (usually with Mockito).
- Fastest type of test.

### Slice tests
- Scope: part of Spring context (example: web layer only with `@WebMvcTest`).
- Useful when you want Spring behavior but not full boot.

### Integration tests
- Scope: real component wiring (service + repository + DB).
- Usually use `@SpringBootTest`.
- In this repo, many integration tests run against real PostgreSQL using Testcontainers.

### End-to-end style backend flow tests
- Scope: complete business flow inside backend boundaries.
- Example: webhook input -> processing -> DB state changes -> workflow steps.

## 3) How tests run when app is “not running”

In tests, Spring Boot creates an application context in the test JVM:

1. JUnit starts a test class.
2. Spring test support boots required beans/context.
3. Test methods call services/controllers/repositories directly.
4. Assertions verify outputs and persisted state.
5. Context is shut down after test/class (based on caching/reuse rules).

So `./mvnw spring-boot:run` is not required for tests.

## 4) Role of Docker and Testcontainers

Docker is used to provide real external dependencies during tests (mainly PostgreSQL here).

With Testcontainers:
- A temporary `postgres` container starts automatically for eligible tests.
- Spring gets container JDBC settings dynamically.
- Flyway migrations run against that test DB.
- Tests execute against realistic DB behavior.
- Container is cleaned up at the end.

Why this helps:
- Better fidelity than in-memory DB for SQL behavior.
- Better isolation than sharing one local DB manually.

## 5) What to look for in a successful run

From terminal output:

1. `BUILD SUCCESS`
2. Surefire summary with `Failures: 0, Errors: 0`
3. Testcontainers lines like:
   - `Found Docker environment ...`
   - `Container postgres:... started`

Example good footer:

```text
[INFO] Tests run: 389, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 6) About common “scary” but non-failing logs

You may still see warnings during shutdown:

- Hikari warning about `maxLifetime` / closed connection during teardown
- Surefire line:
  `Surefire is going to kill self fork JVM...`

If summary still shows `Failures: 0, Errors: 0` and `BUILD SUCCESS`, those warnings did not fail your suite.

## 7) How this repo currently executes tests

- This project currently runs tests through `./mvnw test`.
- Integration and end-to-end style test classes are named `*Test`, so they are included by default.
- Since they rely on Testcontainers, Docker Desktop must be running.

## 8) Practical workflow

1. Start Docker Desktop.
2. Run `./mvnw test`.
3. Check final summary for failures/errors.
4. If a failure appears:
   - read the first failing test name and stack trace
   - reproduce only that test with `-Dtest=ClassName` (or specific method)
   - fix, rerun focused test, then rerun full suite

