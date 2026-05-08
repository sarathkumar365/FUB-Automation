# Jackson 2 → Jackson 3 migration plan

**Status:** drafted, not executed.
**Author:** drafted via planning session 2026-05-08.
**Scope:** end the dual-Jackson hazard introduced by Spring Boot 4 / Hibernate 7 / JJWT 0.12.6.

## 1. Why this migration exists

The admin UI lead-snapshot and webhook-payload panels render garbage like:

```json
{ "array": false, "bigDecimal": false, "containerNode": true, "nodeType": "OBJECT", "object": true, "valueNode": false, ... }
```

instead of the actual lead/webhook JSON content.

### Root cause

Two Jackson majors are on the classpath simultaneously:

| Jar | Provides | Pulled in by |
|---|---|---|
| `tools.jackson.core:jackson-databind:3.0.4` | Jackson 3 (package `tools.jackson.databind.*`) | `spring-boot-starter-jackson:4.0.3` |
| `com.fasterxml.jackson.core:jackson-databind:2.20.2` | Jackson 2 (package `com.fasterxml.jackson.databind.*`) | `io.jsonwebtoken:jjwt-jackson:0.12.6` (transitive) |

Jackson 3 renamed the `databind` and `core` packages from `com.fasterxml.jackson.*` to `tools.jackson.*`. Annotations stayed in `com.fasterxml.jackson.annotation.*` for back-compat. The two `JsonNode` classes (Jackson 2 vs Jackson 3) are **unrelated to the JVM** — different fully-qualified names, no shared interface.

The application code, including every DTO, entity, service, and the user-defined `@Bean ObjectMapper`, imports the **Jackson 2** packages. Spring Boot 4's HTTP response converter (`JacksonJsonHttpMessageConverter`) uses **Jackson 3**'s `ObjectMapper`. When it serializes a record like `LeadFeedItemResponse` and reaches a `JsonNode` field, the value's class is `com.fasterxml.jackson.databind.node.ObjectNode` — to Jackson 3, that's an unknown POJO. It does not implement Jackson 3's `JacksonSerializable`, so the JsonSerializable short-circuit doesn't fire. Jackson 3 falls back to `BeanSerializer` and walks the bean properties — `isArray()`, `isBigDecimal()`, `isContainerNode()`, `isObject()`, `getNodeType()`, etc. — exactly the field set users see in the UI.

The user-defined `@Bean ObjectMapper` is Jackson 2 and is irrelevant to the HTTP path; Spring Boot 4 cannot wire a `com.fasterxml.jackson.databind.ObjectMapper` into a converter that expects `tools.jackson.databind.ObjectMapper`.

### Why fix it sustainably (not via the quick converter override)

Spring Framework 7 still ships `MappingJackson2HttpMessageConverter`, so the bug *can* be papered over by registering it ahead of the Jackson 3 converter (~10 lines). That option was rejected because:

- Spring Framework 8 will likely remove `MappingJackson2HttpMessageConverter`. We'd be forced to migrate later under upgrade pressure rather than now on our own schedule.
- Spring Boot 4 internals (actuator, problem-details, possibly RestClient outbound) use Jackson 3 regardless of the MVC converter override. We'd have two JSON regimes in one app, hard to debug when they disagree.
- The codebase is small and active. Migration cost grows monthly. Now is the cheapest moment.

## 2. Strategic decision

**Strategy A: switch JsonNode-typed entity fields to `String`, migrate all application code to Jackson 3, leave JJWT alone.**

| Element | Decision | Reason |
|---|---|---|
| HTTP layer | Use Spring Boot 4's default Jackson 3 converter. | No fight with auto-config. |
| Application code (DTOs, services, clients, controllers) | Migrate `com.fasterxml.jackson.databind.*` and `com.fasterxml.jackson.core.*` imports to `tools.jackson.*`. Annotation imports (`com.fasterxml.jackson.annotation.*`) stay. | Aligns with Spring Boot 4's runtime mapper. |
| The 3 `JsonNode`-typed entity fields | Change type to `String`. Service code wraps reads with `objectMapper.readTree(...)` and writes with `node.toString()`. | Hibernate 7.2.4's `JacksonJsonFormatMapper` accepts only Jackson 2's `ObjectMapper`. If the entity field were Jackson 3 `JsonNode`, Hibernate would have no way to (de)serialize it. `String` sidesteps the FormatMapper entirely (identity round-trip on the Java side; Postgres JDBC driver casts to JSONB at the column boundary via Hibernate's PG dialect). |
| The 9 `Map<String,Object>` / `List<String>`-typed JSONB fields | Leave alone. Hibernate continues to use Jackson 2 internally to (de)serialize them. | Map and List values cross the HTTP boundary as plain Java collections — Jackson 3 serializes those natively. The dual-Jackson exists at this layer but is invisible at the type system level. |
| `JacksonConfig` | Rewrite `new ObjectMapper()` → `JsonMapper.builder().build()`. | Jackson 3 dropped the bare `new ObjectMapper()` constructor. |
| JJWT | Keep `jjwt-jackson:0.12.6`. Do not exclude its transitive Jackson 2. | Jackson 2 must remain on the classpath because (1) JJWT uses it internally for JWT claim serialization, and (2) Hibernate auto-detects Jackson 2 to instantiate its `JacksonJsonFormatMapper` for the `Map`/`List` JSONB fields. |
| Build hygiene | Add a CI grep / Maven enforcer rule that fails the build if `com.fasterxml.jackson.databind.*` or `com.fasterxml.jackson.core.*` reappears in `src/main/java` or `src/test/java`. | Annotation imports (`com.fasterxml.jackson.annotation.*`) are explicitly allowed and do not match the rule. |

### Why other strategies were rejected

| Strategy | Rejected because |
|---|---|
| Quick converter override (use `MappingJackson2HttpMessageConverter`). | Defers the migration; future Spring upgrade will force it under pressure. Causes Jackson 2/3 split between MVC vs actuator. |
| Custom Jackson-3 Hibernate `FormatMapper`. | Hibernate's `FormatMapper` SPI is undocumented; future Hibernate upgrades may break it. The `String`-field approach achieves the same isolation with less code. |
| Stay on Jackson 2 by overriding Spring's auto-config wholesale. | Same as quick fix but bigger; still doesn't address Spring Boot 4 internals. |
| Downgrade Spring Boot to 3.x. | Platform regression. |

## 3. Surface area inventory

### 3a. File counts

- 76 files import `com.fasterxml.jackson.*`: 35 in `src/main/java`, 41 in `src/test/java`.
- All discovered via `grep -rln "com\.fasterxml\.jackson" src/main/java src/test/java`.

### 3b. Distinct Jackson 2 imports (frequency from `grep -rh`)

| Import | Count | Migration target |
|---|---|---|
| `com.fasterxml.jackson.databind.ObjectMapper` | 46 | `tools.jackson.databind.ObjectMapper` |
| `com.fasterxml.jackson.databind.JsonNode` | 37 | `tools.jackson.databind.JsonNode` |
| `com.fasterxml.jackson.databind.node.ObjectNode` | 26 | `tools.jackson.databind.node.ObjectNode` |
| `com.fasterxml.jackson.annotation.JsonInclude` | 8 | **STAYS** |
| `com.fasterxml.jackson.core.type.TypeReference` | 3 | `tools.jackson.core.type.TypeReference` |
| `com.fasterxml.jackson.annotation.JsonIgnoreProperties` | 3 | **STAYS** |
| `com.fasterxml.jackson.databind.node.ArrayNode` | 2 | `tools.jackson.databind.node.ArrayNode` |
| `com.fasterxml.jackson.databind.annotation.JsonSerialize` | 2 | **DELETE** (misapplied — see §4.4) |
| `com.fasterxml.jackson.core.JsonProcessingException` | 2 | `tools.jackson.core.JacksonException` (now unchecked) |
| `com.fasterxml.jackson.annotation.JsonProperty` | 2 | **STAYS** |

### 3c. Entity JSONB fields — full inventory

Three fields are typed `JsonNode` and **need migration**:

| Field | File | Column |
|---|---|---|
| `LeadEntity.leadDetails` | [LeadEntity.java:55-57](src/main/java/com/fuba/automation_engine/persistence/entity/LeadEntity.java) | `leads.lead_details` JSONB NOT NULL DEFAULT `'{}'::jsonb` |
| `WebhookEventEntity.payload` | [WebhookEventEntity.java:73-75](src/main/java/com/fuba/automation_engine/persistence/entity/WebhookEventEntity.java) | `webhook_events.payload` JSONB NOT NULL |
| `ProcessedCallEntity.rawPayload` | [ProcessedCallEntity.java:71-73](src/main/java/com/fuba/automation_engine/persistence/entity/ProcessedCallEntity.java) | `processed_calls.raw_payload` JSONB |

Nine fields are typed `Map<String,Object>` or `List<String>` and **stay untouched**:

| Field | File | Column |
|---|---|---|
| `AutomationWorkflowEntity.trigger` | [AutomationWorkflowEntity.java](src/main/java/com/fuba/automation_engine/persistence/entity/AutomationWorkflowEntity.java) | `automation_workflows.trigger` |
| `AutomationWorkflowEntity.graph` | same | `automation_workflows.graph` |
| `WorkflowRunEntity.workflowGraphSnapshot` | [WorkflowRunEntity.java](src/main/java/com/fuba/automation_engine/persistence/entity/WorkflowRunEntity.java) | `workflow_runs.workflow_graph_snapshot` |
| `WorkflowRunEntity.triggerPayload` | same | `workflow_runs.trigger_payload` |
| `WorkflowRunStepEntity.dependsOnNodeIds` | [WorkflowRunStepEntity.java](src/main/java/com/fuba/automation_engine/persistence/entity/WorkflowRunStepEntity.java) | `workflow_run_steps.depends_on_node_ids` |
| `WorkflowRunStepEntity.configSnapshot` | same | `workflow_run_steps.config_snapshot` |
| `WorkflowRunStepEntity.resolvedConfig` | same | `workflow_run_steps.resolved_config` |
| `WorkflowRunStepEntity.outputs` | same | `workflow_run_steps.outputs` |
| `WorkflowRunStepEntity.stepState` | same | `workflow_run_steps.step_state` |

### 3d. JSONB schema (no Flyway migration needed)

Verified all relevant columns are already `JSONB` in:
- `db/migration/V1__create_webhook_events.sql` — `payload JSONB NOT NULL`
- `db/migration/V2__create_processed_calls.sql` — `raw_payload JSONB`
- `db/migration/V14__create_leads_table.sql` — `lead_details JSONB NOT NULL DEFAULT '{}'::jsonb`

PostgreSQL accepts a `String` parameter on a JSONB column via Hibernate's PG dialect (`PostgreSQLJsonbJdbcType`), which wraps it as a `PGobject(type=jsonb)`. **No DB migration required.**

### 3e. Inbound HTTP — no `JsonNode` exposure

Verified by grepping all controllers for `@RequestBody`. Inbound bodies are typed records (`CreateWorkflowRequest`, `LoginRequest`, etc.) or raw `String` (`WebhookIngressController`). **No `@RequestBody JsonNode` anywhere.** Migration affects response serialization only.

### 3f. JJWT footprint

| Element | Status |
|---|---|
| pom: `io.jsonwebtoken:jjwt-api:0.12.6`, `jjwt-impl:0.12.6` (runtime), `jjwt-jackson:0.12.6` (runtime) | Untouched. |
| Code: [JwtService.java](src/main/java/com/fuba/automation_engine/service/auth/JwtService.java) — uses `Jwts.builder()` / `Jwts.parser()` only | No direct Jackson imports. No code change. |

JJWT will continue to use Jackson 2 internally for JWT claim (de)serialization. Two Jacksons coexist by package separation.

### 3g. HTTP / RestClient

[HttpClientConfig.java](src/main/java/com/fuba/automation_engine/config/HttpClientConfig.java) is a vanilla `RestClient.builder()` — no Jackson injection. After migration, outbound calls (FUB, AI-call, Slack) deserialize via Spring Boot 4's auto-configured Jackson 3 converters. To verify in Phase 5: `FubFollowUpBossClient.getPersonRawById` round-trip; AI-call adapter round-trip.

## 4. Jackson 2 → Jackson 3 API delta

| Jackson 2 | Jackson 3 | Notes |
|---|---|---|
| `new ObjectMapper()` | `JsonMapper.builder().build()` | Plain constructor removed. |
| `mapper.readTree(String|byte[]|InputStream)` | same | Now throws unchecked `JacksonException`. |
| `mapper.writeValueAsString(obj)` | same | Now throws unchecked `JacksonException`. |
| `mapper.valueToTree(obj)` | same | Same. |
| `mapper.convertValue(node, TypeReference)` | same | Same. |
| `mapper.createObjectNode()` / `createArrayNode()` | same | Same. |
| `JsonNode.get` / `has` / `path` / `hasNonNull` / `asText` / `asLong` / `asInt` / `asBoolean` / `isObject` / `isArray` / `isNull` / `isMissingNode` / `size` | same | All preserved. |
| `JsonNode.fields()` / `fieldNames()` / `elements()` | renamed to `properties()` / `propertyNames()` | **Verified by grep we don't use these.** |
| `ObjectNode.set` / `put` | same | Same. |
| `ArrayNode.add` / `addAll` | same | Same. |
| `JsonProcessingException` (checked, extends `IOException`) | `JacksonException` (unchecked, extends `RuntimeException`) | Drop `throws` clauses; existing `try/catch` blocks become optional. |
| `@JsonInclude(JsonInclude.Include.NON_NULL)` | unchanged | Annotation package preserved. |
| `@JsonProperty("x")` | unchanged | Same. |
| `@JsonIgnoreProperties(ignoreUnknown = true)` | unchanged | Same. |
| `@JsonSerialize(as = X.class)` | `tools.jackson.databind.annotation.JsonSerialize` | Migrate or **delete** — the two existing usages are misapplied; see §4.4. |

## 4. Specific code rewrites

### 4.1 `JacksonConfig` rewrite

Current — [JacksonConfig.java](src/main/java/com/fuba/automation_engine/config/JacksonConfig.java):

```java
import com.fasterxml.jackson.databind.ObjectMapper;
@Configuration
public class JacksonConfig {
    @Bean public ObjectMapper objectMapper() { return new ObjectMapper(); }
}
```

Target:

```java
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
@Configuration
public class JacksonConfig {
    @Bean public ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
}
```

Spring Boot 4 picks this up via `@ConditionalOnMissingBean` and wires it into `JacksonJsonHttpMessageConverter`. Jackson 3's `JsonMapper.builder()` registers `JavaTimeModule` and other JSR-310 support automatically via `ServiceLoader`. **Verify in Phase 5** by checking that `OffsetDateTime` fields like `createdAt` / `updatedAt` render as ISO-8601 strings, not bean dumps. If broken, switch to `JsonMapper.builder().findAndAddModules().build()`.

### 4.2 Entity field rewrite (3 fields, 3 entities)

For each of `LeadEntity.leadDetails`, `WebhookEventEntity.payload`, `ProcessedCallEntity.rawPayload`:

- Change type from `JsonNode` to `String` (field, getter, setter).
- Remove the now-unused `import ...JsonNode` from the entity file.
- `@JdbcTypeCode(SqlTypes.JSON)` and `@Column(...)` stay unchanged.

### 4.3 Service-layer call site rewrites

Verified by exhaustive grep. Counts below are exact, not approximate.

**Reads — 5 main sites + 4 test sites = 9 total:**

Main:
- [ProcessedCallAdminService.java:90-91](src/main/java/com/fuba/automation_engine/service/webhook/ProcessedCallAdminService.java) — chained reads: `entity.getRawPayload().has("eventType")` and `.get("eventType").asText("callsCreated")`. Rewrite to a local var: `JsonNode payload = entity.getRawPayload() == null ? null : objectMapper.readTree(entity.getRawPayload());` then operate on `payload`.
- [AdminWebhookService.java:117](src/main/java/com/fuba/automation_engine/service/webhook/AdminWebhookService.java) — `normalizePayload(entity.getPayload())` — `normalizePayload` signature changes input from `JsonNode` to `String`; method body does `objectMapper.readTree(input)`.
- [LeadAdminQueryService.java:310](src/main/java/com/fuba/automation_engine/service/lead/LeadAdminQueryService.java) — `normalizeJson(entity.getLeadDetails())` — also delete the `normalizeJson` helper at lines 346–355 (redundant; `readTree(String)` already detaches from Hibernate session).
- [LeadAdminQueryService.java:298](src/main/java/com/fuba/automation_engine/service/lead/LeadAdminQueryService.java) — `normalizeJson(row.leadDetails())` — same. The `LeadFeedRow.leadDetails` record component changes from `JsonNode` to `String`; service-side `readTree` produces the JsonNode for the DTO.
- [LeadSnapshotResolver.java:70-78](src/main/java/com/fuba/automation_engine/service/lead/LeadSnapshotResolver.java) — `JsonNode snapshot = entity.get().getLeadDetails();` becomes `JsonNode snapshot = objectMapper.readTree(entity.get().getLeadDetails());`. Existing null/missing-node guards still apply on the parsed JsonNode.

Tests (verified by grep — read sites in test code typically assert on entity-loaded JsonNode):
- [LeadSnapshotResolverTest.java](src/test/java/com/fuba/automation_engine/service/lead/LeadSnapshotResolverTest.java) — 2-3 read assertions.
- [LeadUpsertServiceTest.java](src/test/java/com/fuba/automation_engine/service/lead/LeadUpsertServiceTest.java) — entity assertions after `upsert*` calls.
- Repository read tests in `src/test/java/com/fuba/automation_engine/integration/` — assertions on row contents.

**Writes — 5 main sites + 17 test sites = 22 total** (exhaustive list — every `setLeadDetails` / `setPayload` / `setRawPayload` call):

Main:
- [LeadUpsertService.java:67, 79, 93](src/main/java/com/fuba/automation_engine/service/lead/LeadUpsertService.java) — `entity.setLeadDetails(snapshot)` → `entity.setLeadDetails(snapshot.toString())`. `buildSnapshot()` still returns `JsonNode`; only the persistence handoff changes.
- [WebhookIngressService.java:128](src/main/java/com/fuba/automation_engine/service/webhook/WebhookIngressService.java) — `entity.setPayload(event.payload())` → `entity.setPayload(event.payload().toString())`. Note `WebhookIngressService` has no Jackson imports today (it just chains `event.payload()` calls); after migration the chained `.get()`, `.asText()` calls still resolve to Jackson 3 `JsonNode` because `event.payload()`'s return type is now Jackson 3.
- [WebhookEventProcessorService.java:473](src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java) — `entity.setRawPayload(rawPayload)` → `entity.setRawPayload(rawPayload.toString())`. Surrounding helper methods (`extractEventType`, `extractResourceIds`, `getOrCreateEntity`) keep `JsonNode` parameter types — only the persistence handoff changes.

Tests (17 sites in 13 files):
- src/test/java/com/fuba/automation_engine/integration/WebhookEventRepositoryTest.java:78
- src/test/java/com/fuba/automation_engine/integration/AdminProcessedCallsFlowTest.java:131 (`entity.setRawPayload(objectMapper.createObjectNode())` → `.createObjectNode().toString()`)
- src/test/java/com/fuba/automation_engine/integration/AdminWebhooksFlowTest.java:133, :163
- src/test/java/com/fuba/automation_engine/integration/AdminLeadsFlowTest.java:177
- src/test/java/com/fuba/automation_engine/integration/JdbcLeadFeedReadRepositoryTest.java:150
- src/test/java/com/fuba/automation_engine/integration/AdminWebhooksPostgresRegressionTest.java:84
- src/test/java/com/fuba/automation_engine/integration/JdbcWebhookFeedReadRepositoryTest.java:254
- src/test/java/com/fuba/automation_engine/controller/AdminWebhookControllerTest.java:165
- src/test/java/com/fuba/automation_engine/integration/LeadScopedTopNRepositoryTest.java:164
- src/test/java/com/fuba/automation_engine/integration/AdminProcessedCallsPostgresRegressionTest.java:73
- src/test/java/com/fuba/automation_engine/service/webhook/AdminWebhookServiceTest.java:173
- src/test/java/com/fuba/automation_engine/service/lead/LeadSnapshotResolverTest.java:46, :69, :135 (also a `setLeadDetails(null)` at :120 — no change needed since null is valid for both String and JsonNode)
- src/test/java/com/fuba/automation_engine/service/lead/LeadUpsertServiceTest.java:73
- src/test/java/com/fuba/automation_engine/service/workflow/WorkflowEngineSmokeTest.java:369

**JDBC repository readers** (`JdbcLeadFeedReadRepository` and `JdbcWebhookFeedReadRepository`) currently materialize `JsonNode` from `ResultSet` via `objectMapper.readTree`. After the entity-record component changes from `JsonNode` to `String`:
- The `LeadFeedRow.leadDetails` and `WebhookFeedRow.payload` record components change to `String`.
- The repository simply pulls `rs.getString("lead_details")` (or `rs.getObject(...)` for `PGobject` cases) and stores the raw text.
- The `readJson` helper (current lines [JdbcLeadFeedReadRepository.java:116-135](src/main/java/com/fuba/automation_engine/persistence/repository/JdbcLeadFeedReadRepository.java)) is **simplified** — no `readTree` needed at the repo layer. Service-layer consumers parse with their own ObjectMapper.
- Same for [JdbcWebhookFeedReadRepository.java:115-135](src/main/java/com/fuba/automation_engine/persistence/repository/JdbcWebhookFeedReadRepository.java).

### 4.4 Delete misapplied `@JsonSerialize(as = JsonNode.class)`

Two occurrences carry an annotation that was a failed earlier attempt to fix the same bug:
- [WebhookEventDetailResponse.java:22](src/main/java/com/fuba/automation_engine/controller/dto/WebhookEventDetailResponse.java)
- [WebhookFeedItemResponse.java:24](src/main/java/com/fuba/automation_engine/controller/dto/WebhookFeedItemResponse.java)

**Delete the annotation and the import.** Jackson 3 handles `JsonNode` natively without it.

### 4.5 Production import sweep (35 files, mechanical)

After Phase 1 (entity type changes) lands, run a single sed pass against `src/main/java`:

```bash
find src/main/java -name '*.java' -print0 | xargs -0 sed -i '' \
  -e 's|com\.fasterxml\.jackson\.databind\.|tools.jackson.databind.|g' \
  -e 's|com\.fasterxml\.jackson\.core\.type\.|tools.jackson.core.type.|g' \
  -e 's|com\.fasterxml\.jackson\.core\.JsonProcessingException|tools.jackson.core.JacksonException|g'
```

`com.fasterxml.jackson.annotation.*` is intentionally not touched — those imports remain valid in Jackson 3.

Then by hand:

1. **JacksonConfig** — apply §4.1 rewrite.
2. **`@JsonSerialize` deletions** — apply §4.4.
3. **`throws JsonProcessingException`** — only [SlackNotifyWorkflowStep.java:118](src/main/java/com/fuba/automation_engine/service/workflow/steps/SlackNotifyWorkflowStep.java). Drop the `throws` clause (now unchecked). Update the call site at line 90 to catch `JacksonException` (or remove the catch).
4. **`catch (JsonProcessingException ex)`** — at [AiCallServiceHttpClientAdapter.java:134, 145](src/main/java/com/fuba/automation_engine/client/aicall/AiCallServiceHttpClientAdapter.java). Change to `catch (JacksonException ex)`.

### 4.6 Test import sweep (41 files, mechanical)

Identical sed pass against `src/test/java`. Manual checks afterward:

1. `grep -rn 'new ObjectMapper' src/test/java` — replace with `JsonMapper.builder().build()`.
2. `grep -rn 'JsonProcessingException' src/test/java` — replace with `JacksonException` in `assertThrows` blocks if any.

## 5. Phased execution

### Phase 0 — pre-migration safety net (no code changes)

1. Confirm clean `git status` on a fresh branch off `main`.
2. Run `./mvnw verify`. Record baseline pass count from `target/surefire-reports`.
3. Capture both reproductions of the bug for after/before comparison:
   - `curl -s http://localhost:8080/admin/leads | jq '.items[0].snapshot'` — expect garbage.
   - `curl -s http://localhost:8080/admin/webhooks/{id} | jq '.payload'` — expect garbage.
4. Confirm Postgres data the test will use (or rely on Testcontainers, which is what most integration tests already use — see `src/test/java/com/fuba/automation_engine/integration/*`).

**Gate:** baseline `./mvnw verify` is green. Bug reproductions captured.

### Phase 1 — entities switch to `String` JSONB (single commit)

Touches only the three entity classes plus their direct callers. **No Jackson import changes elsewhere yet.**

1. Apply §4.2 to the three entities.
2. Apply §4.3 to all call sites (services + read repositories + record types).
3. Update affected test fixtures in `src/test/java` that build entities directly or assert entity field types.
4. Run `./mvnw test`.

**Gate:** `./mvnw verify` green. Functionally a no-op for the user-facing bug (Spring is still serializing Jackson 2 JsonNodes from the DTOs); we've only relocated the JsonNode boundary out of Hibernate.

### Phase 2 — Jackson 3 sweep (production code, single commit)

1. Run the sed command from §4.5.
2. Apply the four hand edits from §4.5.
3. Verify: `grep -rn 'com\.fasterxml\.jackson\.\(core\|databind\)' src/main/java` returns empty. Annotations grep should still find `com.fasterxml.jackson.annotation.*` matches — that's correct.
4. Run `./mvnw -DskipTests compile`. Fix any compile errors hand-by-hand.

**Gate:** main tree compiles green.

### Phase 3 — Jackson 3 sweep (tests, single commit)

1. Run the sed command from §4.6 against `src/test/java`.
2. Apply the manual checks from §4.6.
3. Run `./mvnw verify`. Fix any failing tests.

**Gate:** full test suite green.

### Phase 4 — build hygiene (single commit)

Add a Maven enforcer rule (or simple CI grep step) that fails the build if any production source under `src/main/java` or `src/test/java` reintroduces `com.fasterxml.jackson.databind.*` or `com.fasterxml.jackson.core.*` imports. Annotations (`com.fasterxml.jackson.annotation.*`) are explicitly allowed.

Recommended: `maven-enforcer-plugin` with `<bannedImports>` rule, or a simple `bash -c 'grep -rn ... && exit 1 || exit 0'` step in CI.

Also recommended (belt-and-suspenders): pin `com.fasterxml.jackson.core:jackson-databind:2.20.2` explicitly in `pom.xml` `<dependencyManagement>` so unrelated future deps can't silently bump it.

**Gate:** enforcer rule active; CI runs it on every PR.

### Phase 5 — verification (no code changes)

1. Boot the app locally against a Postgres dev DB (or seeded testcontainer).
2. Re-run the two `curl`s from Phase 0. Both must now return real JSON content:
   - `curl -s http://localhost:8080/admin/leads | jq '.items[0].snapshot'` — expect `{ "id": …, "firstName": …, "emails": [...], … }`.
   - `curl -s http://localhost:8080/admin/webhooks/{id} | jq '.payload'` — expect the inbound webhook body.
3. Manual UI walk-through:
   - `/leads` — name column populates from `formatLeadName(snapshot)` ([leadDisplay.ts:6](ui/src/modules/leads/lib/leadDisplay.ts)). Real first/last names visible.
   - `/leads/:id` — `<JsonViewer value={lead.snapshot} />` at [LeadDetailPage.tsx:178](ui/src/modules/leads/ui/LeadDetailPage.tsx) renders real FUB person fields.
   - `/webhooks/:id` — `safeJson(detail.payload)` at [WebhooksPage.tsx:550](ui/src/modules/webhooks/ui/WebhooksPage.tsx) renders the real webhook body.
   - Date columns (`createdAt`, `updatedAt`) display as ISO-8601 strings, not bean dumps.
   - Workflow runs panel renders correctly (verifies `Map<String,Object>` JSONB → HTTP path is intact).
4. End-to-end workflow run: trigger a workflow that uses `lead.firstName` in a JSONata template ([ExpressionScope.java](src/main/java/com/fuba/automation_engine/service/workflow/expression/ExpressionScope.java)). Verify the resolved value is the real FUB first name.
5. JWT login: `POST /admin/auth/login` returns a valid JWT. Verifies JJWT's Jackson 2 path is intact.
6. `./mvnw verify` still green.

**Acceptance:** all of (1)–(6) pass; bug is gone; no test regressions.

## 6. Risk register

| Risk | Likelihood | Impact | Detection / mitigation |
|---|---|---|---|
| `JavaTimeModule` not auto-registered → `OffsetDateTime` fields render as bean dump. | Low | High (similar visible bug for date fields) | Phase 5 step (3) inspects date rendering. If broken, switch to `JsonMapper.builder().findAndAddModules().build()` or explicit `addModule(new JavaTimeModule())`. |
| Spring Boot 4 doesn't pick up our user-defined `tools.jackson.databind.ObjectMapper` bean → defaults override our builder config. | Low | High | Phase 5 step (2) is the canary. If broken, register a `Jackson3ObjectMapperBuilderCustomizer` (Spring Boot 4's Jackson 3 SPI; verify exact class name during impl). |
| Hibernate fails to read existing JSONB rows with the entity field now `String`. | Very low | High | Phase 5 step (3) loads real leads. PostgreSQL's JSONB is normalized text — what was written by Jackson 2 is readable as `String` losslessly. |
| Hibernate's auto-detected Jackson 2 FormatMapper fails because we accidentally excluded Jackson 2. | Low | Critical (`Map<String,Object>` JSONB fields stop deserializing) | We **explicitly do not exclude** Jackson 2. JJWT keeps it on the classpath. Pin Jackson 2 version in `<dependencyManagement>` as belt-and-suspenders. |
| Jackson 3 silently changes serialization shape for some field (null/empty handling, default formats). | Low | Medium | Existing controller integration tests (`AdminLeadsFlowTest`, `AdminWebhooksFlowTest`, etc.) assert specific response shapes and will catch regressions. |
| `JsonNode.toString()` (Jackson 2) produces output Jackson 3 can't parse. | Very low | High | Both produce RFC 8259 JSON. Verified by Phase 5 step (3) loading existing rows that were written by Jackson 2. |
| sed mangles a string literal containing `com.fasterxml.jackson.databind.` (e.g., a log message). | Low | Low | `git diff` review per file before commit; compile gate at end of Phase 2. |
| Stray `com.fasterxml.jackson.databind.*` reintroduced via IDE auto-import after migration. | Medium (over time) | Medium (returns the bug) | Phase 4 enforcer rule blocks the next PR. |
| Future dep accidentally pulls a different Jackson 2 version, conflicting with JJWT. | Low | Medium | Pin `com.fasterxml.jackson.core:jackson-databind:2.20.2` in `<dependencyManagement>`. |
| `JsonNode.fields()` / `fieldNames()` / `elements()` calls break (renamed in Jackson 3). | None | High | Verified by grep — **we don't use any of these methods.** |

## 7. Layer-by-layer safety verification

A walk through every system layer asking "if Phase 5 says we're done, what bug could still ship?":

| Layer | Failure mode | Caught by |
|---|---|---|
| HTTP response (the original bug) | `JsonNode` field still serializes as bean dump. | Phase 5 step (2) curl reproductions; existing controller integration tests. |
| HTTP response — date fields | `OffsetDateTime` serializes as bean dump because `JavaTimeModule` not registered. | Phase 5 step (3) UI walk-through; existing tests asserting `createdAt` shape. |
| Entity → DB write | `String` JSONB write fails on Postgres. | Hibernate 7's PG dialect handles this regardless of Java type; existing repository tests cover writes. |
| Entity ← DB read | Existing JSONB rows fail to materialize as `String`. | Phase 5 step (3) loads a real lead. `JdbcLeadFeedReadRepositoryTest` exercises the read path. |
| Hibernate `Map<String,Object>` JSONB serdes | Jackson 2's `JacksonJsonFormatMapper` no longer auto-instantiates. | We make no Hibernate config changes. `WorkflowEngineSmokeTest`, `WorkflowParityTest` cover Map/List roundtrips. |
| JJWT token issuance/parse | JJWT throws because Jackson 2 was excluded. | We don't exclude Jackson 2. Phase 5 step (5) verifies `POST /admin/auth/login`. `AdminAuthControllerTest` covers it. |
| JSONata expression scope | `lead.firstName` etc. resolve against deserialized snapshot. | `WorkflowParityTest` end-to-end. Phase 5 step (4) workflow run. |
| Inbound webhook ingress | `WebhookIngressController` parses raw String body via FubWebhookParser. | `FubWebhookParserTest`, `FubWebhookParserNormalizedContractTest`. |
| Cursor codecs (`LeadFeedCursorCodec`, `WebhookFeedCursorCodec`) | Existing base64-encoded cursors held in clients become unparseable. | Both codecs round-trip JSON shape that Jackson 3 reads identically; `WebhookFeedCursorCodecTest` validates. |
| Spring tests using `@AutoConfigureMockMvc` | `andExpect(jsonPath(...))` uses Spring Boot's mapper — same as production. | Existing `*FlowTest` integration tests. |
| Slack webhook step | `buildPayload` did `objectMapper.writeValueAsString(payload)` declared `throws JsonProcessingException`. | `SlackNotifyWorkflowStepTest`. Remove the `throws` clause from method signature. |
| AI-call client | `readValue(body, targetType)` deserialization. | `AiCallServiceHttpClientAdapterTest`. |
| `convertValue(node, TypeReference<Map<String,Object>>)` in `WorkflowTriggerRouter` | Jackson 3 may treat differently. | All-or-nothing migration in one PR; both sides upgrade together. `WorkflowTriggerRouterTest` covers. |
| Compile-time | Stray `com.fasterxml.jackson.databind.X` reintroduced via IDE auto-import. | Phase 4 enforcer rule blocks the next PR. |

## 8. Open questions to resolve at execution time

1. **Does `JsonMapper.builder().build()` auto-register `JavaTimeModule` via ServiceLoader?** Verify in Phase 5 step (3). Fallback: `findAndAddModules()` or explicit module add.
2. **Does Spring Boot 4's `JacksonHttpMessageConvertersConfiguration` honor a user-defined `tools.jackson.databind.ObjectMapper` bean?** Should via `@ConditionalOnMissingBean`. Verify in Phase 5 step (2).
3. **Are there test fixture files in `src/test/resources/` that contain Jackson-2-flavored serializations?** Static grep can't find dynamic test data. Phase 3 test runs would surface any breakage.
4. **Does any `try/catch (Exception)` somewhere depend on `JsonProcessingException` being checked?** Now unchecked, so a previously-checked exception may flow through. Grep for `catch (JsonProcessingException` and trace each.

## 9. Files modified — final tally

| Phase | Files | Notes |
|---|---|---|
| 0 | none | safety net |
| 1 | 3 entities + 5 main caller files + 13 test fixture files (~22 writer call sites + 9 reader call sites total) | type change, `.toString()` / `readTree()` conversion |
| 2 | 35 main java files | sed sweep + 4 hand edits (JacksonConfig rewrite, two `@JsonSerialize` deletions, one `throws` cleanup) |
| 3 | 41 test java files | sed sweep |
| 4 | pom.xml + (optional) CI config | enforcer rule + dep pin |
| 5 | none | verification |
| **Total** | **~95 files** (some files participate in multiple phases) | dominated by import-only changes |

## 10. Rollback plan

Land as a sequence of commits on a feature branch, **not** trickled into `main`:

1. Phase 1 commit — entities → String, callers updated.
2. Phase 2 commit — Jackson 3 import sweep (production).
3. Phase 3 commit — Jackson 3 import sweep (tests).
4. Phase 4 commit — enforcer rule + dep pins.

Roll back the whole branch with `git revert` of the merge if any post-deploy issue surfaces. **Schema is unchanged**, so no DB rollback needed. Existing JSONB data remains valid for either codepath.

## 11. Out of scope

- JJWT replacement / Jackson 2 removal — preserved.
- Hibernate version upgrade for native Jackson 3 support — wait for Hibernate to ship a `JacksonJsonFormatMapper` that accepts `tools.jackson.databind.ObjectMapper`. Track upstream.
- Custom Jackson 3 modules / serializers — none needed; Jackson 3 handles `JsonNode` and `OffsetDateTime` natively after the import flip.
- UI changes — none required. The UI's `JsonViewer` and `safeJson` already render whatever JSON the API returns; the bug was server-side only.

## 12. Pre-flight checklist before merging

- [ ] `git grep 'com\.fasterxml\.jackson\.\(core\|databind\)' src/main/java/` returns empty.
- [ ] `git grep 'com\.fasterxml\.jackson\.\(core\|databind\)' src/test/java/` returns empty.
- [ ] `git grep 'com\.fasterxml\.jackson\.annotation' src/` may still find matches — that's expected and correct.
- [ ] `./mvnw verify` exits 0 on a fresh checkout.
- [ ] Manual UI walkthrough complete: lead list, lead detail, webhook list, webhook detail, workflow runs, all dates render correctly.
- [ ] `curl -fsS http://localhost:8080/admin/auth/login -d '{...}' | jq .token` returns a valid JWT.
- [ ] Postgres JSONB columns of pre-migration rows still load without error in JPA repository tests against a copy of prod data.
- [ ] Phase 4 enforcer rule active in CI.

## 13. Acceptance criteria (single-line summary)

> The two `curl` reproductions from Phase 0 return real lead/webhook JSON; admin UI lead-detail and webhook-detail panels show parsed content; `./mvnw verify` is green; JWT login endpoint still works; no `com.fasterxml.jackson.core` or `com.fasterxml.jackson.databind` imports remain in `src/main/java` or `src/test/java`; CI enforcer rule prevents future reintroduction.

---

## 14. Re-evaluation pass (2026-05-08)

After drafting the plan, I verified every load-bearing claim against the actual codebase. This section documents the verification, what changed, and residual unknowns.

### 14a. Verified by direct grep

| Claim | Verification | Result |
|---|---|---|
| Three entity fields are typed `JsonNode`. | `grep -B1 -A3 "@JdbcTypeCode" src/main/java/.../entity/*.java` | **Confirmed.** `LeadEntity.leadDetails`, `WebhookEventEntity.payload`, `ProcessedCallEntity.rawPayload`. |
| Nine entity fields are typed `Map<String,Object>` / `List<String>` and stay untouched. | Same grep above. | **Confirmed.** Listed in §3c. |
| All JSONB columns are `JSONB` not `JSON` or `TEXT`. | `grep -rn "JSONB" src/main/resources/db/migration/` | **Confirmed.** V1, V2, V14 (and other workflow migrations) all use `JSONB`. |
| No `@RequestBody JsonNode` exists. | `grep -rn '@RequestBody' src/main/java/.../controller` | **Confirmed.** All inbound bodies are typed records or raw `String`. |
| No `JsonNode.fields()` / `.fieldNames()` / `.elements()` usage. | `grep -rn '\.fields()\|\.fieldNames()\|node\.elements()'` | **Confirmed empty.** Renamed APIs are not a concern. |
| No custom Jackson serializers / deserializers / `@JsonView` / `SimpleModule.registerModule`. | `grep -rn 'extends StdSerializer\|extends StdDeserializer\|@JsonComponent\|registerModule\|@JsonView'` | **Confirmed empty.** No custom Jackson behaviour to migrate. |
| No JSON test fixtures in `src/test/resources/`. | `ls src/test/resources/` | **Confirmed.** Only `application.properties` and a `static/` folder; no JSON files. |
| `JJWT` is the only thing pulling Jackson 2 onto the classpath. | `mvn dependency:tree -Dincludes=com.fasterxml.jackson.core:jackson-databind` | **Confirmed.** Single path: `jjwt-jackson:0.12.6 → jackson-databind:2.20.2`. |
| `MappingJackson2HttpMessageConverter` still ships in Spring Framework 7.0.5. | `unzip -l ~/.m2/.../spring-web-7.0.5.jar` | **Confirmed.** Both Jackson 2 and Jackson 3 converters are present (relevant only for the rejected quick-fix strategy). |
| Hibernate 7.2.4's `JacksonJsonFormatMapper` constructor takes `com.fasterxml.jackson.databind.ObjectMapper`. | `javap -classpath ~/.m2/.../hibernate-core-7.2.4.Final.jar org.hibernate.type.format.jackson.JacksonJsonFormatMapper` | **Confirmed.** Hibernate has no Jackson 3 FormatMapper; the `String`-field strategy is correct. |
| Jackson 3 keeps annotations in `com.fasterxml.jackson.annotation.*`. | `mvn dependency:tree -Dverbose` shows `tools.jackson.databind:3.0.4 → com.fasterxml.jackson.core:jackson-annotations:2.20` (kept). | **Confirmed.** `@JsonInclude`, `@JsonProperty`, `@JsonIgnoreProperties` imports do not migrate. |
| Jackson 3 has `tools.jackson.databind.json.JsonMapper`. | `unzip -l ~/.m2/.../jackson-databind-3.0.4.jar | grep JsonMapper` | **Confirmed.** Class is at `tools/jackson/databind/json/JsonMapper.class`. |
| Jackson 3 has `tools.jackson.core.JacksonException` (replaces checked `JsonProcessingException`). | `unzip -l ~/.m2/.../jackson-core-3.0.4.jar | grep Exception` | **Confirmed.** `JacksonException` lives at `tools/jackson/core/JacksonException.class`. |
| Jackson 3 `JsonNode` has `propertyNames()` and `properties()` (renamed from `fieldNames`/`fields`). | `javap` on Jackson 3 `JsonNode` | **Confirmed.** Renamed APIs verified — but we don't use them, so no impact. |

### 14b. Errors / gaps caught during verification (corrected in the plan)

1. **Writer count was undercounted.** Original draft said "~5 test fixtures." Exhaustive grep found **17 test writer sites in 13 files** (+5 main writer sites = 22 total). Plan §4.3 and §9 corrected.
2. **Two main writer sites omitted from the original list.** `WebhookIngressService.java:128` (`setPayload(event.payload())`) and `WebhookEventProcessorService.java:473` (`setRawPayload(rawPayload)`) were missing from §4.3. Now listed.
3. **`WebhookIngressService` has no Jackson imports** but operates on `JsonNode` via chained method calls on `event.payload()`. After migration the chained calls still resolve correctly (Jackson 3 `JsonNode` has the same chain API), but the persistence handoff at line 128 needs `.toString()`. Now noted.
4. **`ProcessedCallAdminService.java:90-91` does chained reads** (`entity.getRawPayload().has(...)` / `.get(...).asText(...)`) — moderately invasive because the chain becomes a local variable after the entity returns `String`. Now called out as a notable rewrite.
5. **`AdminWebhookService.normalizePayload`** signature changes from `JsonNode` → `String` (input). The implementation already does `objectMapper.readTree(input.toString())` so the `.toString()` call goes away. Noted.
6. **Reader count was overstated.** Original said "~10 reader sites." Exact count is **5 main + 4 test = 9**. Plan §4.3 corrected.

### 14c. Residual unknowns (cannot verify statically)

These remain as Phase 5 verification gates — failure on any one is a hard stop, not a release-blocker downgrade:

1. **Does `JsonMapper.builder().build()` auto-register `JavaTimeModule`?** If not, `OffsetDateTime` fields will revert to bean-introspection. Symptom would be visible on every DTO (`createdAt`, `updatedAt`). Phase 5 step (3) catches this. Mitigation: `JsonMapper.builder().findAndAddModules().build()` or explicit `addModule(new JavaTimeModule())`.
2. **Does Spring Boot 4 wire a user-defined `tools.jackson.databind.ObjectMapper @Bean` into `JacksonJsonHttpMessageConverter`?** Spring Boot 4's auto-config introduced `JacksonJsonMapperBuilderCustomizer` — if this is the supported customization SPI, our raw `@Bean ObjectMapper` may not be picked up. Phase 5 step (2) is the canary. Mitigation: register a `JacksonJsonMapperBuilderCustomizer` bean instead of a raw mapper.
3. **Does Postgres JSONB write succeed with a Java `String` field via Hibernate 7's PG dialect?** Documented behavior is yes (PG dialect wraps as `PGobject(type=jsonb)`), but unverified on this exact combination of versions. Phase 5 catches via the integration tests (which use real Postgres via Testcontainers).
4. **Do existing JSONB rows materialize correctly as `String`** when the entity field type is `String`? Same dialect path as (3); verified end-to-end by Phase 5 step (3) loading a real lead. Theoretically lossless because the data is stored as text either way.

### 14d. Risks the re-evaluation has *not* surfaced

Things I deliberately checked for and did not find:
- Hidden Jackson configuration (`MapperFeature` flags, custom date formats, custom property naming strategies) — none.
- Custom Jackson modules / `@JsonComponent` — none.
- Use of advanced JsonNode APIs beyond the standard navigation set — none.
- `@RequestBody JsonNode` or `Map<String,Object>` going through the HTTP/Jackson boundary — none.
- Test fixture files containing pre-serialized Jackson 2 JSON shapes — none.
- Database columns that are `JSON` instead of `JSONB`, or that are typed differently than expected — none.

### 14e. Final go/no-go assessment

**Go.** The migration is bounded, the surface area is fully enumerated, every load-bearing assumption has been verified against the codebase or against the actual jar contents, and the residual unknowns (§14c) are all caught by Phase 5 verification — none of them is a "discover the problem in production" hazard.

The single highest-risk item is unknown #1 (JavaTimeModule auto-registration). If it bites, the symptom is the same kind of bean-dump on date fields that triggered this whole effort, which means the Phase 5 gate will catch it the same way the original bug was caught, and the fix (one line in `JacksonConfig`) is trivial.

Suggested commit boundaries (so reverts stay surgical):
1. Phase 1 → one commit per entity (3 commits) — easier to bisect.
2. Phase 2 → one commit for the sed sweep, separate commits for each of the four hand edits.
3. Phase 3 → one commit for the test sed sweep; separate commits for any test-only fixes.
4. Phase 4 → one commit for the enforcer rule, one for any version pin.

Total estimate: **6–10 commits across a 1–2 day implementation window**, including verification.
