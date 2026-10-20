package com.motaamneh.mstsocial.server.observability;

import org.slf4j.spi.LoggingEventBuilder;

import java.sql.SQLException;
import java.util.Arrays;

/** Adds diagnostic metadata without exception messages, SQL, payloads, or cause stack traces. */
public final class SafeExceptionDetails {
    private SafeExceptionDetails() {
    }

    public static LoggingEventBuilder addTo(LoggingEventBuilder event, Throwable exception) {
        event.addKeyValue("exceptionType", exception.getClass().getSimpleName());
        Arrays.stream(exception.getStackTrace())
                .filter(frame -> frame.getClassName().startsWith("com.motaamneh.mstsocial."))
                .findFirst().ifPresent(frame -> event.addKeyValue("failureAt",
                        frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber()));
        // Bound traversal even if an unusual exception has a cyclic cause chain.
        Throwable cause = exception;
        for (int depth = 0; depth < 16; depth++) {
            if (cause instanceof SQLException sql) {
                String sqlState = sql.getSQLState();
                if (sqlState != null && sqlState.matches("[A-Z0-9]{5}")) {
                    event.addKeyValue("sqlState", sqlState);
                }
                break;
            }
            if (cause.getCause() == null || cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        return event;
    }
}
