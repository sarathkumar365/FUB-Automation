package com.fuba.automation_engine.client.fub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.client.fub.dto.FubCallResponseDto;
import com.fuba.automation_engine.client.fub.dto.FubCreateTaskRequestDto;
import com.fuba.automation_engine.client.fub.dto.FubTaskResponseDto;
import com.fuba.automation_engine.config.FubClientProperties;
import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import com.fuba.automation_engine.service.model.CreatedTask;
import com.fuba.automation_engine.service.model.PersonCommunicationCheckResult;
import com.fuba.automation_engine.service.model.PersonDetails;
import com.fuba.automation_engine.service.model.RegisterWebhookCommand;
import com.fuba.automation_engine.service.model.RegisterWebhookResult;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FubFollowUpBossClient implements FollowUpBossClient {

    private static final Logger log = LoggerFactory.getLogger(FubFollowUpBossClient.class);

    private final RestClient restClient;
    private final FubClientProperties properties;
    private final ObjectMapper objectMapper;

    public FubFollowUpBossClient(
            RestClient.Builder restClientBuilder,
            FubClientProperties properties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        validateRequiredConfiguration();
        this.restClient = restClientBuilder.baseUrl(trimTrailingSlash(properties.getBaseUrl())).build();
    }

    @Override
    public RegisterWebhookResult registerWebhook(RegisterWebhookCommand command) {
        log.info("Register webhook called in stub mode event={} url={}", command == null ? null : command.event(), command == null ? null : command.url());
        // Step 2 scope: webhook registration is done manually in FUB (UI/API) for now.
        // This stub keeps the contract in place until automated registration is implemented.
        return new RegisterWebhookResult(
                0L,
                command == null ? null : command.event(),
                command == null ? null : command.url(),
                "STUBBED");
    }

    @Override
    public CallDetails getCallById(long callId) {
        log.info("Calling FUB getCallById callId={}", callId);
        try {
            FubCallResponseDto response = restClient.get()
                    .uri("/calls/{id}", callId)
                    .headers(this::applyDefaultHeaders)
                    .retrieve()
                    .body(FubCallResponseDto.class);

            if (response == null) {
                throw new FubPermanentException("FUB returned empty body for getCallById", null);
            }

            log.info("FUB getCallById succeeded callId={}", callId);
            return new CallDetails(
                    response.id(),
                    response.personId(),
                    response.duration(),
                    response.userId(),
                    response.outcome(),
                    response.isIncoming(),
                    response.created());
        } catch (RestClientResponseException ex) {
            log.warn("FUB getCallById returned HTTP error callId={} status={}", callId, ex.getStatusCode().value());
            throw mapResponseException("GET /calls/{id}", ex);
        } catch (ResourceAccessException ex) {
            log.warn("FUB getCallById network error callId={}", callId);
            throw new FubTransientException("FUB network failure on GET /calls/{id}", null, ex);
        }
    }

    @Override
    public PersonDetails getPersonById(long personId) {
        // Single raw fetch path: derive the typed PersonDetails view from the same JsonNode
        // body we already pull for lead snapshotting — avoids a duplicate /people/{id} call
        // and keeps the DTO shape drift-free with the raw payload.
        JsonNode response = getPersonRawById(personId);
        Long id = response.hasNonNull("id") ? response.get("id").asLong() : null;
        Boolean claimed = response.hasNonNull("claimed") ? response.get("claimed").asBoolean() : null;
        Long assignedUserId = response.hasNonNull("assignedUserId") ? response.get("assignedUserId").asLong() : null;
        Integer contacted = response.hasNonNull("contacted") ? response.get("contacted").asInt() : null;
        return new PersonDetails(id, claimed, assignedUserId, contacted);
    }

    @Override
    public JsonNode getPersonRawById(long personId) {
        log.info("Calling FUB getPersonRawById personId={}", personId);
        try {
            String body = restClient.get()
                    .uri("/people/{id}", personId)
                    .headers(this::applyDefaultHeaders)
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new FubPermanentException("FUB returned empty body for getPersonRawById", null);
            }

            JsonNode response;
            try {
                response = objectMapper.readTree(body);
            } catch (Exception parseEx) {
                throw new FubPermanentException("FUB returned non-JSON body for getPersonRawById", null, parseEx);
            }
            if (response == null || response.isNull()) {
                throw new FubPermanentException("FUB returned empty body for getPersonRawById", null);
            }

            log.info("FUB getPersonRawById succeeded personId={}", personId);
            return response;
        } catch (RestClientResponseException ex) {
            log.warn("FUB getPersonRawById returned HTTP error personId={} status={}", personId, ex.getStatusCode().value());
            throw mapResponseException("GET /people/{id}", ex);
        } catch (ResourceAccessException ex) {
            log.warn("FUB getPersonRawById network error personId={}", personId);
            throw new FubTransientException("FUB network failure on GET /people/{id}", null, ex);
        }
    }

    @Override
    public PersonCommunicationCheckResult checkPersonCommunication(long personId) {
        PersonDetails person = getPersonById(personId);
        boolean communicationFound = person.contacted() != null && person.contacted() > 0;
        log.info(
                "FUB communication check personId={} contacted={} result={}",
                personId,
                person.contacted(),
                communicationFound ? "FOUND" : "NOT_FOUND");
        return new PersonCommunicationCheckResult(personId, communicationFound);
    }

    @Override
    public ActionExecutionResult reassignPerson(long personId, long targetUserId) {
        log.info(
                "FUB action simulated: reassign person personId={} targetUserId={} mode=log-only",
                personId,
                targetUserId);
        return ActionExecutionResult.ok();
    }

    @Override
    public ActionExecutionResult movePersonToPond(long personId, long targetPondId) {
        log.info(
                "FUB action simulated: move person to pond personId={} targetPondId={} mode=log-only",
                personId,
                targetPondId);
        return ActionExecutionResult.ok();
    }

    @Override
    public ActionExecutionResult addTag(long personId, String tagName) {
        log.info(
                "FUB action simulated: add tag personId={} tagName={} mode=log-only",
                personId,
                tagName);
        return ActionExecutionResult.ok();
    }

    @Override
    public CreatedTask createTask(CreateTaskCommand command) {
        log.info("Calling FUB createTask personId={} assignedUserId={}", command.personId(), command.assignedUserId());
        FubCreateTaskRequestDto request = new FubCreateTaskRequestDto(
                command.personId(),
                command.name(),
                command.assignedUserId(),
                command.dueDate(),
                command.dueDateTime());
        try {
            FubTaskResponseDto response = restClient.post()
                    .uri("/tasks")
                    .headers(this::applyDefaultHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(FubTaskResponseDto.class);

            if (response == null) {
                throw new FubPermanentException("FUB returned empty body for createTask", null);
            }

            log.info("FUB createTask succeeded taskId={} personId={}", response.id(), response.personId());
            return new CreatedTask(
                    response.id(),
                    response.personId(),
                    response.assignedUserId(),
                    response.name(),
                    response.dueDate(),
                    response.dueDateTime());
        } catch (RestClientResponseException ex) {
            log.warn("FUB createTask returned HTTP error status={}", ex.getStatusCode().value());
            throw mapResponseException("POST /tasks", ex);
        } catch (ResourceAccessException ex) {
            log.warn("FUB createTask network error");
            throw new FubTransientException("FUB network failure on POST /tasks", null, ex);
        }
    }

    private RuntimeException mapResponseException(String operation, RestClientResponseException ex) {
        int statusCode = ex.getStatusCode().value();
        String message = "FUB request failed for " + operation + " with status " + statusCode;
        if (statusCode == 429 || statusCode >= 500) {
            return new FubTransientException(message, statusCode, ex);
        }
        return new FubPermanentException(message, statusCode, ex);
    }

    private void applyDefaultHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.AUTHORIZATION, buildBasicAuthValue(properties.getApiKey()));
        headers.set("X-System", properties.getXSystem());
        headers.set("X-System-Key", properties.getXSystemKey());
    }

    private String buildBasicAuthValue(String apiKey) {
        String credential = (apiKey == null ? "" : apiKey) + ":";
        String encoded = Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private String trimTrailingSlash(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        if (input.endsWith("/")) {
            return input.substring(0, input.length() - 1);
        }
        return input;
    }

    private void validateRequiredConfiguration() {
        requireNonBlank(properties.getBaseUrl(), "fub.base-url");
        requireNonBlank(properties.getApiKey(), "fub.api-key");
        requireNonBlank(properties.getXSystem(), "fub.x-system");
        requireNonBlank(properties.getXSystemKey(), "fub.x-system-key");
    }

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required FUB configuration: " + fieldName);
        }
    }
}
