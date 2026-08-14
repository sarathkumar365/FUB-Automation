package com.flux.service.reporting;

/**
 * The caller asked for a reporting window we do not offer.
 *
 * <p>A dedicated type rather than {@code IllegalArgumentException} so the controller can
 * answer 400 for exactly this case: catching the broader type would report any internal
 * fault as bad client input and hide a real defect from logs and alerting.
 */
public class UnknownReportWindowException extends RuntimeException {

    public UnknownReportWindowException(String key) {
        super("Unknown reporting window: " + key);
    }
}
