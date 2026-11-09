package com.accountbook.common.web;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final transient Object[] args;
    private final List<FieldIssue> details;

    private ApiException(HttpStatus status, String code, String messageKey, Object[] args,
                         List<FieldIssue> details) {
        super(messageKey);
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.args = args;
        this.details = details;
    }

    public static ApiException badRequest(String messageKey, Object... args) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", messageKey, args, List.of());
    }

    public static ApiException invalidField(String field, String messageKey, Object... args) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "error.VALIDATION_FAILED", new Object[0],
                List.of(new FieldIssue(field, messageKey, args)));
    }

    public static ApiException notFound(String messageKey, Object... args) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", messageKey, args, List.of());
    }

    public static ApiException conflict(String messageKey, Object... args) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", messageKey, args, List.of());
    }

    public static ApiException unauthorized(String messageKey, Object... args) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", messageKey, args, List.of());
    }

    public static ApiException forbidden(String messageKey, Object... args) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", messageKey, args, List.of());
    }

    public static ApiException of(HttpStatus status, String code, String messageKey, Object... args) {
        return new ApiException(status, code, messageKey, args, List.of());
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String messageKey() {
        return messageKey;
    }

    public Object[] args() {
        return args;
    }

    public List<FieldIssue> details() {
        return details;
    }
}
