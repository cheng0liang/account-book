package com.accountbook.common.web;

import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        List<ErrorResponse.Detail> details = ex.details().stream()
                .map(i -> new ErrorResponse.Detail(i.field(), messages.getMessage(i.messageKey(), i.args(), locale)))
                .toList();
        String message = messages.getMessage(ex.messageKey(), ex.args(), locale);
        return ResponseEntity.status(ex.status()).body(new ErrorResponse(ex.code(), message, details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        if (ex instanceof MaxUploadSizeExceededException) {
            return body(HttpStatus.PAYLOAD_TOO_LARGE, List.of());
        }
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.Detail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::detail)
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ErrorResponse("VALIDATION_FAILED", localized("error.VALIDATION_FAILED"), details));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        return ResponseEntity.status(statusCode).headers(headers)
                .body(new ErrorResponse(codeOf(statusCode), localized("error." + codeOf(statusCode)), List.of()));
    }

    private ErrorResponse.Detail detail(FieldError e) {
        return new ErrorResponse.Detail(e.getField(), messages.getMessage(e, LocaleContextHolder.getLocale()));
    }

    private ResponseEntity<ErrorResponse> body(HttpStatus status, List<ErrorResponse.Detail> details) {
        String code = codeOf(status);
        return ResponseEntity.status(status).body(new ErrorResponse(code, localized("error." + code), details));
    }

    private String localized(String key) {
        return messages.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    static String codeOf(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "VALIDATION_FAILED";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 429 -> "TOO_MANY_REQUESTS";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "INTERNAL_ERROR";
        };
    }
}
