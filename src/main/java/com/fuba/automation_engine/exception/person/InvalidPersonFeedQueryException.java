package com.fuba.automation_engine.exception.person;

/**
 * Thrown when a client provides an invalid persons-list query (bad cursor,
 * malformed date range, etc). Mapped to HTTP 400 by the controller.
 */
public class InvalidPersonFeedQueryException extends RuntimeException {

    public InvalidPersonFeedQueryException(String message) {
        super(message);
    }
}
