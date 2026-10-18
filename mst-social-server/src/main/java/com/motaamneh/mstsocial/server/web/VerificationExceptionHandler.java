package com.motaamneh.mstsocial.server.web;

import com.motaamneh.mstsocial.core.service.VerificationException;
import com.motaamneh.mstsocial.core.validation.InvalidHandleException;
import com.motaamneh.mstsocial.server.web.dto.response.ApiErrorResponse;
import com.motaamneh.mstsocial.server.web.dto.response.FieldErrorResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;

/** Central HTTP translation. The core never imports HTTP or Spring types. */
@RestControllerAdvice
public class VerificationExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(VerificationException.class)
    ResponseEntity<Object> domain(VerificationException exception) {
        HttpStatus status = switch (exception.code()) {
            case REQUEST_NOT_FOUND, ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_ACTIVE_REQUEST -> HttpStatus.CONFLICT;
            case ACTIVE_REQUEST_LIMIT_REACHED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNSUPPORTED_PLATFORM -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        String detail = switch (exception.code()) {
            case REQUEST_NOT_FOUND -> "The verification request was not found.";
            case ACCOUNT_NOT_FOUND -> "The verified social account was not found.";
            case DUPLICATE_ACTIVE_REQUEST -> "An active request already exists for this subject, platform, and handle.";
            case ACTIVE_REQUEST_LIMIT_REACHED -> "The active verification request limit was reached. Cancel a request or wait for expiry.";
            case UNSUPPORTED_PLATFORM -> "This installation does not support that platform.";
        };
        return problem(status, exception.code().name(), detail);
    }

    @ExceptionHandler(InvalidHandleException.class)
    ResponseEntity<Object> invalidHandle(InvalidHandleException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_HANDLE", "The handle has an invalid format for this platform.");
    }

    @ExceptionHandler(ApiRequestException.class)
    ResponseEntity<Object> invalidRequest(ApiRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(ApiRateLimitException.class)
    ResponseEntity<Object> rateLimit(ApiRateLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(ApiErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                        "The tenant's request rate limit was reached. Retry after the indicated delay."));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Object> persistence(DataAccessException exception) {
        if (exception instanceof TransientDataAccessException || exception instanceof DataAccessResourceFailureException) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE",
                    "Storage is temporarily unavailable. Read the request status before retrying a mutation.");
        }
        logger.error("Storage operation failed (" + exception.getClass().getSimpleName() + ")");
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The operation could not be completed.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception exception) {
        // Exception messages and stack traces can contain SQL, provider payloads, or credentials.
        logger.error("API operation failed (" + exception.getClass().getSimpleName() + ")");
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The operation could not be completed.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request
    ) {
        List<FieldErrorResponse> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .distinct().toList();
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .body(new ApiErrorResponse(URI.create("about:blank"), "Bad Request", 400, "VALIDATION_FAILED",
                        "One or more request fields are invalid.", errors, null, null));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        // Replace framework-generated details, which may echo rejected JSON or input values.
        HttpStatus resolved = HttpStatus.resolve(status.value());
        if (resolved == null) {
            resolved = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String code = switch (resolved) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case NOT_FOUND -> "NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case NOT_ACCEPTABLE -> "NOT_ACCEPTABLE";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            default -> resolved.is5xxServerError() ? "INTERNAL_ERROR" : "INVALID_REQUEST";
        };
        HttpHeaders safeHeaders = new HttpHeaders();
        safeHeaders.putAll(headers);
        safeHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        safeHeaders.setCacheControl(CacheControl.noStore());
        return new ResponseEntity<>(ApiErrorResponse.of(resolved, code,
                resolved.is5xxServerError() ? "The operation could not be completed." : resolved.getReasonPhrase()),
                safeHeaders, resolved);
    }

    private static ResponseEntity<Object> problem(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore()).body(ApiErrorResponse.of(status, code, detail));
    }
}
