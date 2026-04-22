package com.fuba.automation_engine.exception.lead;

/**
 * Thrown when a client provides an invalid leads-list query (bad cursor,
 * malformed date range, etc). Mapped to HTTP 400 by the controller.
 */
public class InvalidLeadFeedQueryException extends RuntimeException {

    public InvalidLeadFeedQueryException(String message) {
        super(message);
    }
}
