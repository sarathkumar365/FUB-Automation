package com.flux.client.cortex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.client.cortex.dto.CortexPlaceRequestDto;
import com.flux.client.cortex.dto.CortexPlaceResponseDto;
import com.flux.config.CortexProperties;
import com.flux.service.workflow.cortex.CortexClient;
import com.flux.service.workflow.cortex.CortexClientException;
import com.flux.service.workflow.cortex.GetCallResponse;
import com.flux.service.workflow.cortex.PlaceCallRequest;
import com.flux.service.workflow.cortex.PlaceCallResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CortexHttpClientAdapter implements CortexClient {

    private static final String IN_PROGRESS = "in_progress";
    private static final TypeReference<LinkedHashMap<String, Object>> JSON_OBJECT_TYPE =
            new TypeReference<>() {};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CortexProperties properties;
    private final Environment environment;

    public CortexHttpClientAdapter(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            CortexProperties properties,
            Environment environment) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.environment = environment;
        this.restClient = restClientBuilder
                .requestFactory(requestFactory(properties))
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .build();
    }

    @Override
    public PlaceCallResponse placeCall(PlaceCallRequest request) {
        ensureConfigured();
        if (request == null) {
            throw new CortexClientException("placeCall request is required", false, null);
        }
        try {
            String to = resolveToNumber(request.to());
            String body = restClient.post()
                    .uri("/call")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CortexPlaceRequestDto(request.callKey(), to, request.context()))
                    .retrieve()
                    .body(String.class);

            CortexPlaceResponseDto dto = read(body, CortexPlaceResponseDto.class, "POST /call");
            if (dto.callSid() == null || dto.callSid().isBlank() || dto.status() == null || dto.status().isBlank()) {
                throw new CortexClientException("POST /call response missing required fields", false, null);
            }
            return new PlaceCallResponse(dto.callSid(), dto.status());
        } catch (RestClientResponseException ex) {
            throw mapResponseException("POST /call", ex);
        } catch (ResourceAccessException ex) {
            throw new CortexClientException("Network failure on POST /call", true, null, ex);
        }
    }

    @Override
    public GetCallResponse getCall(String callSid) {
        ensureConfigured();
        if (callSid == null || callSid.isBlank()) {
            throw new CortexClientException("callSid is required", false, null);
        }
        try {
            String body = restClient.get()
                    .uri("/calls/{callSid}", callSid)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> payload = readJsonObject(body, "GET /calls/{callSid}");
            Object statusValue = payload.get("status");
            String status = statusValue instanceof String s ? s : null;
            if (status == null || status.isBlank()) {
                throw new CortexClientException("GET /calls/{callSid} response missing status", false, null);
            }

            Object callSidValue = payload.get("call_sid");
            String resolvedCallSid = callSidValue instanceof String s && !s.isBlank() ? s : callSid;

            if (IN_PROGRESS.equals(status)) {
                return new GetCallResponse(resolvedCallSid, status, null);
            }

            return new GetCallResponse(resolvedCallSid, status, payload);
        } catch (RestClientResponseException ex) {
            throw mapResponseException("GET /calls/{callSid}", ex);
        } catch (ResourceAccessException ex) {
            throw new CortexClientException("Network failure on GET /calls/{callSid}", true, null, ex);
        }
    }

    private void ensureConfigured() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new CortexClientException("cortex.base-url is required", false, null);
        }
    }

    private RuntimeException mapResponseException(String operation, RestClientResponseException ex) {
        HttpStatusCode statusCode = ex.getStatusCode();
        int status = statusCode != null ? statusCode.value() : 0;
        boolean transientFailure = status >= 500;
        String message = operation + " failed with status=" + status;
        return new CortexClientException(message, transientFailure, status, ex);
    }

    private <T> T read(String body, Class<T> targetType, String operation) {
        if (body == null || body.isBlank()) {
            throw new CortexClientException(operation + " returned empty body", false, null);
        }
        try {
            return objectMapper.readValue(body, targetType);
        } catch (JsonProcessingException ex) {
            throw new CortexClientException(operation + " returned invalid JSON", false, null, ex);
        }
    }

    private Map<String, Object> readJsonObject(String body, String operation) {
        if (body == null || body.isBlank()) {
            throw new CortexClientException(operation + " returned empty body", false, null);
        }
        try {
            return objectMapper.readValue(body, JSON_OBJECT_TYPE);
        } catch (JsonProcessingException ex) {
            throw new CortexClientException(operation + " returned invalid JSON", false, null, ex);
        }
    }

    private SimpleClientHttpRequestFactory requestFactory(CortexProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(1, properties.getConnectTimeoutMs())));
        factory.setReadTimeout(Duration.ofMillis(Math.max(1, properties.getReadTimeoutMs())));
        return factory;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    String resolveToNumber(String requestedTo) {
        String safeTo = properties.getLocalSafeToNumber();
        if (environment.acceptsProfiles(Profiles.of("local"))
                && safeTo != null
                && !safeTo.isBlank()) {
            return safeTo.trim();
        }
        return requestedTo;
    }
}
