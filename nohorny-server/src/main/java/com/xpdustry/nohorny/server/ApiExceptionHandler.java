// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.SimpleServerMessage;
import jakarta.servlet.RequestDispatcher;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

/// Every error answers `{"message": "..."}`.
@RestControllerAdvice
public final class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<SimpleServerMessage> onResponseStatusException(final ResponseStatusException exception) {
        final var reason = exception.getReason();
        return message(
                exception.getStatusCode().value(),
                reason == null ? reasonOf(exception.getStatusCode().value()) : reason);
    }

    /// Answers the first violated constraint of a request body, such as `password must be at least 8 characters`.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<SimpleServerMessage> onMethodArgumentNotValidException(
            final MethodArgumentNotValidException exception) {
        final var error = exception.getBindingResult().getFieldError();
        final var message = error == null ? null : error.getDefaultMessage();
        return message(HttpStatus.BAD_REQUEST.value(), message == null ? "invalid request body" : message);
    }

    public static ResponseEntity<SimpleServerMessage> message(final int status, final String message) {
        // Explicit, so the error is written whatever the request accepts
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SimpleServerMessage(message));
    }

    public static String reasonOf(final int status) {
        final var resolved = HttpStatus.resolve(status);
        return resolved == null ? "error" : resolved.getReasonPhrase().toLowerCase(Locale.ROOT);
    }

    /// Replaces the body of the errors rendered by the `/error` endpoint, such as the Spring MVC and filter ones.
    @Component
    public static final class MessageErrorAttributes extends DefaultErrorAttributes {

        @Override
        public Map<String, Object> getErrorAttributes(final WebRequest request, final ErrorAttributeOptions options) {
            final var status =
                    request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE, RequestAttributes.SCOPE_REQUEST);
            return Map.of("message", reasonOf(status instanceof Integer code ? code : 500));
        }
    }
}
