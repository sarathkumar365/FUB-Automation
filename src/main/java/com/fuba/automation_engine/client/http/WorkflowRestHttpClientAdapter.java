package com.fuba.automation_engine.client.http;

import com.fuba.automation_engine.config.WorkflowStepHttpProperties;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpClient;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpClientException;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpRequest;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class WorkflowRestHttpClientAdapter implements WorkflowHttpClient {

    private final RestClient restClient;

    public WorkflowRestHttpClientAdapter(
            RestClient.Builder restClientBuilder,
            WorkflowStepHttpProperties workflowStepHttpProperties) {
        this.restClient = restClientBuilder
                .requestFactory(requestFactory(workflowStepHttpProperties))
                .build();
    }

    @Override
    public WorkflowHttpResponse execute(WorkflowHttpRequest request) {
        if (request == null) {
            throw new WorkflowHttpClientException("HTTP request is required", false);
        }

        HttpMethod method;
        try {
            method = HttpMethod.valueOf(request.method().trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new WorkflowHttpClientException("Unsupported HTTP method", false, ex);
        }

        try {
            return restClient
                    .method(method)
                    .uri(request.url())
                    .headers(headers -> applyHeaders(headers, request.headers()))
                    .body(request.body() != null ? request.body() : "")
                    .exchange((clientRequest, clientResponse) -> {
                        int statusCode = clientResponse.getStatusCode().value();
                        Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
                        clientResponse.getHeaders().forEach((name, values) -> responseHeaders.put(name, List.copyOf(values)));
                        String responseBody = readResponseBody(clientResponse.getBody());
                        return new WorkflowHttpResponse(statusCode, responseHeaders, responseBody);
                    });
        } catch (ResourceAccessException ex) {
            throw new WorkflowHttpClientException("HTTP transport failure", true, ex);
        }
    }

    private SimpleClientHttpRequestFactory requestFactory(WorkflowStepHttpProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(1, properties.getConnectTimeoutMs())));
        factory.setReadTimeout(Duration.ofMillis(Math.max(1, properties.getReadTimeoutMs())));
        return factory;
    }

    private void applyHeaders(org.springframework.http.HttpHeaders target, Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach(target::set);
    }

    private String readResponseBody(java.io.InputStream bodyStream) {
        if (bodyStream == null) {
            return "";
        }
        try {
            return new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
}
