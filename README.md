# automation-engine

Follow Up Boss call automation engine.

## Startup

### 1. Create local env file
Use `.env` for local development secrets and settings.

```bash
cp .env.example .env
```

### 2. Fill required variables in `.env`
At minimum for outbound Follow Up Boss calls:

- `FUB_BASE_URL`
- `FUB_API_KEY`
- `FUB_X_SYSTEM`
- `FUB_X_SYSTEM_KEY`

Database values are also required for app startup (`DB_URL`, `DB_USER`, `DB_PASS`).

Optional for Step 5 behavior:

- `DEV_TEST_USER_ID` (only enforced when `local` profile is active)
- `FUB_RETRY_MAX_ATTEMPTS`
- `FUB_RETRY_INITIAL_DELAY_MS`
- `FUB_RETRY_MAX_DELAY_MS`
- `FUB_RETRY_MULTIPLIER`
- `FUB_RETRY_JITTER_FACTOR`

### 3. Export `.env` into shell environment
Spring Boot reads real environment variables; it does not automatically read (unless it's manually setup.  Here, we have not) `.env`.

```bash
set -a
source .env
set +a
```

### 4. Run the app

```bash
./mvnw spring-boot:run
```

### 5. Run tests

```bash
./mvnw clean test
```
