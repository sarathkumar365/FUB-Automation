package com.fuba.automation_engine.service.fub;

import com.fuba.automation_engine.config.FubRetryProperties;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class FubCallHelper {

    private final FubRetryProperties retryProperties;

    public FubCallHelper(FubRetryProperties retryProperties) {
        this.retryProperties = retryProperties;
    }

    public <T> T executeWithRetry(Supplier<T> action) {
        int maxAttempts = Math.max(1, retryProperties.getMaxAttempts());
        int attempt = 1;
        while (true) {
            try {
                return action.get();
            } catch (FubTransientException ex) {
                if (attempt >= maxAttempts) {
                    throw ex;
                }
                attempt++;
            }
        }
    }

    public long parsePersonId(String sourcePersonId) {
        if (sourcePersonId == null || sourcePersonId.isBlank()) {
            throw new IllegalArgumentException("sourcePersonId is missing or blank");
        }
        try {
            return Long.parseLong(sourcePersonId.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("sourcePersonId is not a valid number: " + sourcePersonId, ex);
        }
    }

    public static String stringifyStatus(Integer statusCode) {
        return statusCode == null ? "UNKNOWN" : String.valueOf(statusCode);
    }
}
