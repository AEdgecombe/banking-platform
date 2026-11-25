package com.example.web;

import com.example.service.BankingException;
import com.example.web.ApiModels.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every failure into an ErrorResponse. Clients never see a stack trace, a Spring
 * ProblemDetail or a bare string.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ErrorResponse> handleBanking(BankingException ex) {
        var status = switch (ex) {
            case BankingException.AccountNotFound e -> HttpStatus.NOT_FOUND;
            case BankingException.InsufficientFunds e -> HttpStatus.UNPROCESSABLE_ENTITY;
            case BankingException.InvalidAmount e -> HttpStatus.BAD_REQUEST;
            case BankingException.InvalidRequest e -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(ex.code(), ex.getMessage(), ex.field()));
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleNotImplemented(UnsupportedOperationException ex) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ErrorResponse("NOT_IMPLEMENTED", ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Something went wrong on our side", null));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        FieldError error = ex.getBindingResult().getFieldError();
        var body = error == null
                ? new ErrorResponse("VALIDATION_FAILED", "Request is invalid", null)
                : new ErrorResponse("VALIDATION_FAILED", error.getDefaultMessage(), error.getField());
        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    /** Malformed JSON, wrong method, bad path variable, unknown route and so on. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode status,
                                                             WebRequest request) {
        var resolved = HttpStatus.resolve(status.value());
        var code = resolved == null ? "HTTP_" + status.value() : resolved.name();
        var message = resolved == null ? "Request failed" : resolved.getReasonPhrase();
        return ResponseEntity.status(status).headers(headers).body(new ErrorResponse(code, message, null));
    }
}
